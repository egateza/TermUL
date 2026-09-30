package dev.egateza.termul.ssh;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.SshException;
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

    /** State per profil. Semua field di-guard oleh {@link SessionManager#lock}. */
    static final class Entry {
        final UUID profileId;
        final String address;
        final CompletableFuture<SshConnection> future = new CompletableFuture<>();
        int refs; // guarded by lock
        ScheduledFuture<?> pendingClose; // guarded by lock

        Entry(UUID profileId, String address) {
            this.profileId = profileId;
            this.address = address;
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

    /** Batas panjang rantai jump host. */
    static final int MAX_JUMPS = 4;

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
        if (!settings.heartbeatInterval().isZero()) {
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, settings.heartbeatInterval());
            CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, settings.heartbeatMaxMissed());
        }
        client.start();
    }

    /**
     * Mendapatkan koneksi ke profil (membuat baru kalau belum ada / sudah putus).
     * Pemanggil wajib {@link SshLease#close()} setelah selesai.
     */
    public SshLease acquire(HostProfile profile) throws SshConnectException {
        return acquire(profile, List.of());
    }

    /** @param via profil yang sedang connect lewat profil ini (rantai jump host, untuk deteksi putaran) */
    private SshLease acquire(HostProfile profile, List<UUID> via) throws SshConnectException {
        Objects.requireNonNull(profile, "profile");
        if (closed.get()) {
            throw new IllegalStateException("SessionManager sudah ditutup");
        }
        Entry entry;
        boolean owner = false;
        int refsNow;
        synchronized (lock) {
            entry = entries.get(profile.id());
            if (entry == null || !entry.isUsable()) {
                entry = new Entry(profile.id(), profile.address());
                entries.put(profile.id(), entry);
                owner = true;
            }
            entry.refs++;
            refsNow = entry.refs;
            if (entry.pendingClose != null) {
                entry.pendingClose.cancel(false);
                entry.pendingClose = null;
            }
        }

        if (owner) {
            try {
                entry.future.complete(connect(profile, via));
            } catch (SshConnectException | RuntimeException e) {
                entry.future.completeExceptionally(e);
                synchronized (lock) {
                    entries.remove(profile.id(), entry);
                }
                throw e;
            }
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

    void release(Entry entry) {
        synchronized (lock) {
            entry.refs--;
            log.info("Pemakai koneksi {} dilepas (sisa: {})", entry.address, entry.refs);
            if (entry.refs > 0 || entries.get(entry.profileId) != entry) {
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

    private SshConnection connect(HostProfile profile, List<UUID> via) throws SshConnectException {
        if (profile.jumpHostId() == null) {
            log.info("Connect ke {}", profile.address());
            return connectTo(profile, profile.host(), profile.port(), null);
        }
        return connectViaJump(profile, via);
    }

    /**
     * ProxyJump: koneksi (bersama) ke jump host, lalu local forward {@code 127.0.0.1:<acak>} → host tujuan dilihat
     * dari jump host. Host key diverifikasi terhadap host tujuan ({@link AppServerKeyVerifier#LOGICAL_TARGET}), bukan
     * alamat tunnel. Tunnel dan lease jump host dilepas saat koneksi tujuan tertutup. Jump host boleh berantai.
     */
    private SshConnection connectViaJump(HostProfile profile, List<UUID> via) throws SshConnectException {
        UUID jumpId = profile.jumpHostId();
        var chain = new ArrayList<>(via);
        chain.add(profile.id());
        if (chain.contains(jumpId) || chain.size() > MAX_JUMPS) {
            throw new SshConnectException("Rantai jump host untuk " + profile.address() + " berputar atau terlalu panjang.");
        }
        HostProfile jump = profiles.apply(jumpId).orElseThrow(() -> new SshConnectException(
                "Jump host untuk " + profile.name() + " tidak ditemukan (profilnya sudah dihapus?)."));
        SshLease jumpLease = acquire(jump, List.copyOf(chain));
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
                connection = connectTo(profile, local.getHostName(), local.getPort(), context);
            } catch (HostKeyRejectedException e) {
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
    private SshConnection connectTo(HostProfile profile, String host, int port, AttributeRepository context)
            throws SshConnectException {
        ClientSession session;
        try {
            session = client.connect(profile.username(), host, port, context, null)
                    .verify(settings.connectTimeout())
                    .getSession();
        } catch (IOException | RuntimeException e) {
            throw translateConnect(profile, e);
        }
        try {
            AuthSetup.configure(session, profile, credentials);
            session.auth().verify(settings.authTimeout());
            log.info("Terautentikasi ke {}", profile.address());
            return new SshConnection(profile, session, settings.channelOpenTimeout());
        } catch (SshConnectException e) {
            session.close(true);
            throw e;
        } catch (IOException | RuntimeException e) {
            // terjemahkan dulu: atribut session hilang setelah close
            SshConnectException translated = translateAuth(profile, session, e);
            session.close(true);
            throw translated;
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
