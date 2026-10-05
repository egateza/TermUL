package dev.egateza.termul.ssh;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.AuthSetup;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.AppServerKeyVerifier;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.HostKeyRejectedException;
import dev.egateza.termul.ssh.hostkey.HostKeyVerdict;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.client.auth.password.PasswordIdentityProvider;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.core.CoreModuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mengelola koneksi SSH: satu {@link SshConnection} per profil, dipakai bersama lewat
 * {@link SshLease} (reference counting). Koneksi tanpa pemakai ditutup setelah grace period.
 *
 * <p>{@link #acquire} blocking (network + prompt user): panggil dari executor, jangan di EDT.
 */
public final class SessionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /**
     * Field profil yang menentukan koneksi. Kalau berubah (mis. username diperbaiki), koneksi lama tidak dipakai untuk
     * pemakai baru; pemakai lama tetap memakainya sampai dilepas.
     */
    record Target(String host, int port, String username, AuthMethod authMethod, String privateKeyPath,
                  UUID jumpHostId) {
        static Target of(HostProfile p) {
            return new Target(p.host(), p.port(), p.username(), p.authMethod(), p.privateKeyPath(), p.jumpHostId());
        }
    }

    /** State per profil. Semua field di-guard oleh {@link SessionManager#lock}. */
    static final class Entry {
        final UUID profileId;
        final String address;
        final Target target;
        final CompletableFuture<SshConnection> future = new CompletableFuture<>();
        /** Dibatalkan saat semua penunggu connect membatalkan: connect yang berjalan dihentikan. */
        final ConnectCancel abort = new ConnectCancel();
        int refs; // guarded by lock
        int waiting; // guarded by lock; pemakai yang masih menunggu connect selesai
        ScheduledFuture<?> pendingClose; // guarded by lock

        Entry(HostProfile profile) {
            this.profileId = profile.id();
            this.address = profile.address();
            this.target = Target.of(profile);
        }

        boolean isUsable() {
            if (!future.isDone()) {
                return true; // sedang connect: ikut menunggu
            }
            if (future.isCompletedExceptionally()) {
                return false;
            }
            return future.join().isOpen();
        }
    }

    /** Satu pemanggil {@link #acquire} yang sedang menunggu. Field di-guard oleh {@link SessionManager#lock}. */
    private static final class Waiter {
        boolean done;
        boolean withdrawn;
    }

    /** Batas panjang rantai jump host. */
    static final int MAX_JUMPS = 4;

    /**
     * Banner client ({@code SSH-2.0-TermUL}) dan nama global request keep-alive, supaya koneksi TermUL mudah dikenali
     * di log server ({@code remote software version TermUL}, {@code rtype keepalive@termul}). RFC 4253: tanpa spasi
     * dan tanpa tanda minus.
     */
    static final String CLIENT_IDENTIFICATION = "TermUL";
    static final String HEARTBEAT_REQUEST = "keepalive@termul";

    private final Function<UUID, Optional<HostProfile>> profiles;
    private final SshClient client;
    private final SshSettings settings;
    private final CredentialProvider credentials;
    private final ScheduledExecutorService scheduler;
    private final Object lock = new Object();
    private final Map<UUID, Entry> entries = new HashMap<>(); // guarded by lock
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Tanpa jump host (profil dengan jump host ditolak dengan pesan jelas). */
    public SessionManager(KnownHostsStore knownHosts, HostKeyPrompt hostKeyPrompt, CredentialProvider credentials,
                          SshSettings settings) {
        this(knownHosts, hostKeyPrompt, credentials, settings, id -> Optional.empty());
    }

    /** @param profiles mencari profil jump host berdasarkan id (versi terbaru dari store) */
    public SessionManager(KnownHostsStore knownHosts, HostKeyPrompt hostKeyPrompt, CredentialProvider credentials,
                          SshSettings settings, Function<UUID, Optional<HostProfile>> profiles) {
        this.profiles = Objects.requireNonNull(profiles);
        this.settings = Objects.requireNonNull(settings);
        this.credentials = Objects.requireNonNull(credentials);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("ssh-release").daemon().factory());

        client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(new AppServerKeyVerifier(knownHosts, hostKeyPrompt));
        client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
        client.setPasswordIdentityProvider(PasswordIdentityProvider.EMPTY_PASSWORDS_PROVIDER);
        CoreModuleProperties.PASSWORD_PROMPTS.set(client, AuthSetup.MAX_PASSWORD_ATTEMPTS);
        CoreModuleProperties.CLIENT_IDENTIFICATION.set(client, CLIENT_IDENTIFICATION);
        if (!settings.heartbeatInterval().isZero()) {
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, settings.heartbeatInterval());
            CoreModuleProperties.HEARTBEAT_REQUEST.set(client, HEARTBEAT_REQUEST);
            CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, settings.heartbeatMaxMissed());
        }
        client.start();
    }

    /**
     * Mendapatkan koneksi ke profil (membuat baru kalau belum ada / sudah putus).
     * Pemanggil wajib {@link SshLease#close()} setelah selesai.
     */
    public SshLease acquire(HostProfile profile) throws SshConnectException {
        return acquire(profile, new ConnectCancel());
    }

    /**
     * Seperti {@link #acquire(HostProfile)}, tapi bisa dibatalkan selama masih connect: pemanggil langsung kembali
     * dengan {@link SshConnectCancelledException}. Connect yang berjalan (termasuk ke jump host) ikut dihentikan kalau
     * tidak ada pemakai lain yang masih menunggunya.
     */
    public SshLease acquire(HostProfile profile, ConnectCancel cancel) throws SshConnectException {
        return acquire(profile, List.of(), cancel);
    }

    /** @param via profil yang sedang connect lewat profil ini (rantai jump host, untuk deteksi putaran) */
    private SshLease acquire(HostProfile requested, List<UUID> via, ConnectCancel cancel) throws SshConnectException {
        Objects.requireNonNull(requested, "profile");
        Objects.requireNonNull(cancel, "cancel");
        // Pemanggil (tab, panel SFTP, sesi edit) bisa memegang versi profil saat dibuka: pakai versi terbaru dari
        // store, supaya username/host/key yang sudah diperbaiki langsung berlaku saat "Coba lagi"/reconnect.
        HostProfile profile = profiles.apply(requested.id()).orElse(requested);
        if (closed.get()) {
            throw new IllegalStateException("SessionManager sudah ditutup");
        }
        if (cancel.isCancelled()) {
            throw new SshConnectCancelledException(profile.address());
        }
        Entry entry;
        boolean owner = false;
        int refsNow;
        var waiter = new Waiter();
        synchronized (lock) {
            entry = entries.get(profile.id());
            if (entry == null || !entry.isUsable() || !entry.target.equals(Target.of(profile))) {
                Entry outdated = entry;
                entry = new Entry(profile);
                entries.put(profile.id(), entry);
                owner = true;
                if (outdated != null && outdated.isUsable()) {
                    log.info("Profil {} diubah (host/user/auth): koneksi baru dibuat, koneksi lama ke {} dipakai "
                            + "sampai pemakainya selesai", profile.name(), outdated.address);
                    if (outdated.refs == 0) {
                        if (outdated.pendingClose != null) {
                            outdated.pendingClose.cancel(false);
                        }
                        closeEntry(outdated);
                    }
                }
            }
            entry.refs++;
            entry.waiting++;
            refsNow = entry.refs;
            if (entry.pendingClose != null) {
                entry.pendingClose.cancel(false);
                entry.pendingClose = null;
            }
        }
        Entry acquired = entry;
        cancel.onCancel(() -> withdraw(acquired, waiter));

        if (owner) {
            try {
                entry.future.complete(connect(profile, via, entry.abort));
            } catch (SshConnectException | RuntimeException e) {
                Exception failure = entry.abort.isCancelled() && !(e instanceof SshConnectCancelledException)
                        ? new SshConnectCancelledException(profile.address()) : e;
                entry.future.completeExceptionally(failure);
                synchronized (lock) {
                    entries.remove(profile.id(), entry);
                }
                if (failure instanceof SshConnectException sce) {
                    throw sce;
                }
                throw (RuntimeException) failure;
            }
        } else {
            try {
                CompletableFuture.anyOf(entry.future, cancel.future()).join();
            } catch (CompletionException e) {
                // gagal connect: ditangani di bawah
            }
        }

        synchronized (lock) {
            if (!waiter.withdrawn) {
                waiter.done = true;
                entry.waiting--;
            }
        }
        if (waiter.withdrawn) { // dibatalkan, tapi connect diteruskan untuk pemakai lain yang masih menunggu
            release(entry);
            throw new SshConnectCancelledException(profile.address());
        }

        try {
            SshConnection connection = entry.future.join();
            if (!owner) {
                log.info("Memakai koneksi yang ada ke {} (pemakai: {})", profile.address(), refsNow);
            }
            return new SshLease(this, entry, connection);
        } catch (CompletionException e) {
            synchronized (lock) {
                entry.refs--;
            }
            if (e.getCause() instanceof SshConnectException sce) {
                throw sce;
            }
            throw new SshConnectException("Koneksi gagal", e.getCause());
        }
    }

    /** Pemanggil {@code acquire} membatalkan; kalau tidak ada lagi yang menunggu, connect yang berjalan dihentikan. */
    private void withdraw(Entry entry, Waiter waiter) {
        boolean abort;
        synchronized (lock) {
            if (waiter.done || waiter.withdrawn) {
                return;
            }
            waiter.withdrawn = true;
            entry.waiting--;
            abort = entry.waiting == 0 && !entry.future.isDone();
        }
        if (abort) {
            log.info("Connect ke {} dibatalkan", entry.address);
            entry.abort.cancel();
        }
    }

    void release(Entry entry) {
        synchronized (lock) {
            entry.refs--;
            log.info("Pemakai koneksi {} dilepas (sisa: {})", entry.address, entry.refs);
            if (entry.refs > 0) {
                return;
            }
            if (entries.get(entry.profileId) != entry) { // sudah diganti koneksi baru (profil diubah / putus)
                closeEntry(entry);
                return;
            }
            if (closed.get() || settings.releaseGrace().isZero()) {
                closeEntry(entry);
            } else {
                entry.pendingClose = scheduler.schedule(() -> {
                    synchronized (lock) {
                        if (entry.refs == 0 && entries.get(entry.profileId) == entry) {
                            closeEntry(entry);
                        }
                    }
                }, settings.releaseGrace().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Jumlah koneksi yang masih tercatat (untuk test / diagnostik). */
    public int connectionCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    // guarded by lock
    private void closeEntry(Entry entry) {
        entries.remove(entry.profileId, entry);
        entry.future.thenAccept(c -> {
            log.info("Menutup koneksi ke {}", c.profile().address());
            c.closeByManager();
        });
    }

    /** @param abort dibatalkan → session yang sedang dibuka ditutup sehingga connect cepat gagal */
    private SshConnection connect(HostProfile profile, List<UUID> via, ConnectCancel abort) throws SshConnectException {
        if (profile.jumpHostId() == null) {
            log.info("Connect ke {}", profile.address());
            return connectTo(profile, profile.host(), profile.port(), null, abort);
        }
        return connectViaJump(profile, via, abort);
    }

    /**
     * ProxyJump: koneksi (bersama) ke jump host, lalu local forward {@code 127.0.0.1:<acak>} → host tujuan dilihat
     * dari jump host. Host key diverifikasi terhadap host tujuan ({@link AppServerKeyVerifier#LOGICAL_TARGET}), bukan
     * alamat tunnel. Tunnel dan lease jump host dilepas saat koneksi tujuan tertutup. Jump host boleh berantai.
     */
    private SshConnection connectViaJump(HostProfile profile, List<UUID> via, ConnectCancel abort)
            throws SshConnectException {
        UUID jumpId = profile.jumpHostId();
        var chain = new ArrayList<>(via);
        chain.add(profile.id());
        if (chain.contains(jumpId) || chain.size() > MAX_JUMPS) {
            throw new SshConnectException("Rantai jump host untuk " + profile.address() + " berputar atau terlalu panjang.");
        }
        HostProfile jump = profiles.apply(jumpId).orElseThrow(() -> new SshConnectException(
                "Jump host untuk " + profile.name() + " tidak ditemukan (profilnya sudah dihapus?)."));
        SshLease jumpLease = acquire(jump, List.copyOf(chain), abort);
        ExplicitPortForwardingTracker tunnel = null;
        try {
            tunnel = jumpLease.connection().session().createLocalPortForwardingTracker(
                    new SshdSocketAddress("127.0.0.1", 0), new SshdSocketAddress(profile.host(), profile.port()));
            SshdSocketAddress local = tunnel.getBoundAddress();
            log.info("Connect ke {} lewat jump host {} (tunnel {})", profile.address(), jump.address(), local);
            var context = AttributeRepository.ofKeyValuePair(AppServerKeyVerifier.LOGICAL_TARGET,
                    new SshdSocketAddress(profile.host(), profile.port()));
            SshConnection connection;
            try {
                connection = connectTo(profile, local.getHostName(), local.getPort(), context, abort);
            } catch (HostKeyRejectedException | SshConnectCancelledException e) {
                throw e;
            } catch (SshConnectException e) {
                throw new SshConnectException(e.getMessage() + " (lewat jump host " + jump.address()
                        + "; pastikan jump host mengizinkan TCP forwarding dan bisa menjangkau " + profile.host() + ")", e);
            }
            ExplicitPortForwardingTracker opened = tunnel;
            connection.addCloseListener(c -> {
                closeQuietly(opened);
                jumpLease.close();
            });
            if (!connection.isOpen()) { // tertutup sebelum listener terpasang
                closeQuietly(opened);
                jumpLease.close();
            }
            return connection;
        } catch (IOException e) {
            closeQuietly(tunnel);
            jumpLease.close();
            throw new SshConnectException("Gagal membuat tunnel lewat jump host " + jump.address() + ": "
                    + e.getMessage(), e);
        } catch (SshConnectException | RuntimeException e) {
            closeQuietly(tunnel);
            jumpLease.close();
            throw e;
        }
    }

    private static void closeQuietly(ExplicitPortForwardingTracker tunnel) {
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (IOException | RuntimeException e) {
                log.debug("Menutup tunnel: {}", e.toString());
            }
        }
    }

    /** @param context connection context (mis. host tujuan logis untuk tunnel), boleh null */
    private SshConnection connectTo(HostProfile profile, String host, int port, AttributeRepository context,
                                    ConnectCancel abort) throws SshConnectException {
        ClientSession session;
        try {
            ConnectFuture connecting = client.connect(profile.username(), host, port, context, null);
            abort.onCancel(connecting::cancel);
            session = connecting.verify(settings.connectTimeout()).getSession();
        } catch (IOException | RuntimeException e) {
            if (abort.isCancelled()) {
                throw new SshConnectCancelledException(profile.address());
            }
            throw translateConnect(profile, e);
        }
        abort.onCancel(() -> session.close(true));
        try {
            awaitServerIdentification(profile, session);
            AuthSetup.configure(session, profile, credentials);
            session.auth().verify(settings.authTimeout());
            log.info("Terautentikasi ke {}", profile.address());
            return new SshConnection(profile, session, settings.channelOpenTimeout());
        } catch (SshConnectException e) {
            session.close(true);
            throw abort.isCancelled() ? new SshConnectCancelledException(profile.address()) : e;
        } catch (IOException | RuntimeException e) {
            // terjemahkan dulu: atribut session hilang setelah close
            SshConnectException translated = abort.isCancelled()
                    ? new SshConnectCancelledException(profile.address()) : translateAuth(profile, session, e);
            session.close(true);
            throw translated;
        }
    }

    /**
     * Menunggu banner identifikasi server ({@code SSH-2.0-...}) dalam {@link SshSettings#connectTimeout()}. TCP connect
     * saja tidak cukup: lewat jump host, socket lokal tunnel langsung tersambung walaupun jump host masih (lama) mencoba
     * menjangkau tujuan, dan server yang hang tidak pernah mengirim banner. Tanpa batas ini yang menunggu hanya
     * {@link SshSettings#authTimeout()} yang sengaja panjang (memberi waktu user mengetik password/konfirmasi TOFU).
     */
    private void awaitServerIdentification(HostProfile profile, ClientSession session) throws SshConnectException {
        var received = new CompletableFuture<Boolean>();
        SessionListener listener = new SessionListener() {
            @Override
            public void sessionPeerIdentificationReceived(Session s, String version, List<String> extraLines) {
                received.complete(true);
            }

            @Override
            public void sessionClosed(Session s) {
                received.complete(false);
            }
        };
        session.addSessionListener(listener);
        try {
            if (session.getServerVersion() != null) {
                return;
            }
            if (!session.isOpen()) {
                received.complete(false);
            }
            if (!received.get(settings.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new SshConnectException("Koneksi ke " + profile.host() + ":" + profile.port()
                        + " ditutup sebelum server SSH merespons.");
            }
        } catch (TimeoutException e) {
            throw new SshConnectException("Timeout: server SSH di " + profile.host() + ":" + profile.port()
                    + " tidak merespons dalam " + settings.connectTimeout().toSeconds() + " detik.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SshConnectException("Connect ke " + profile.address() + " diinterupsi.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e); // tidak terjadi: future tidak pernah gagal
        } finally {
            session.removeSessionListener(listener);
        }
    }

    private static SshConnectException translateConnect(HostProfile profile, Exception e) {
        Throwable root = rootCause(e);
        String target = profile.host() + ":" + profile.port();
        return switch (root) {
            case UnknownHostException _ -> new SshConnectException("Host tidak dikenal: " + profile.host(), e);
            case UnresolvedAddressException _ -> new SshConnectException("Host tidak dikenal: " + profile.host(), e);
            case ConnectException _ -> new SshConnectException("Koneksi ke " + target + " ditolak atau tidak bisa dijangkau.", e);
            case NoRouteToHostException _ -> new SshConnectException("Tidak ada rute ke " + target + ".", e);
            default -> e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")
                    ? new SshConnectException("Timeout saat connect ke " + target + ".", e)
                    : new SshConnectException("Gagal connect ke " + target + ": " + root, e);
        };
    }

    private static SshConnectException translateAuth(HostProfile profile, ClientSession session, Exception e) {
        HostKeyVerdict verdict = session.getAttribute(AppServerKeyVerifier.VERDICT);
        if (verdict != null && !(verdict instanceof HostKeyVerdict.Trusted)) {
            return new HostKeyRejectedException(verdict);
        }
        var cancelled = session.getAttribute(AuthSetup.CANCELLED);
        if (cancelled != null && cancelled.get()) {
            return new SshConnectException("Login ke " + profile.address() + " dibatalkan.", e);
        }
        if (e instanceof SshException && e.getMessage() != null && e.getMessage().contains("No more authentication methods")) {
            return new SshConnectException("Autentikasi ke " + profile.address() + " gagal (key/password ditolak).", e);
        }
        Throwable root = rootCause(e);
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
            return new SshConnectException("Timeout saat autentikasi ke " + profile.address() + ".", e);
        }
        return new SshConnectException("Autentikasi ke " + profile.address() + " gagal: " + root.getMessage(), e);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (lock) {
            for (Entry e : entries.values().toArray(Entry[]::new)) {
                closeEntry(e);
            }
        }
        scheduler.shutdownNow();
        try {
            client.stop();
        } catch (RuntimeException e) {
            log.warn("Gagal menghentikan SshClient", e);
        }
    }

    /** Untuk test. */
    Duration releaseGrace() {
        return settings.releaseGrace();
    }
}
