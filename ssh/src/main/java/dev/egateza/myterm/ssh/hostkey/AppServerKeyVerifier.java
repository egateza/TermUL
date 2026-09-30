package dev.egateza.myterm.ssh.hostkey;

import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strict host key checking terhadap known_hosts aplikasi:
 * <ul>
 *   <li>key cocok → terima</li>
 *   <li>host dikenal tapi tidak ada key yang cocok → <b>tolak</b> (tanpa opsi override di sini)</li>
 *   <li>host baru → tanya user (TOFU); kalau setuju, simpan ke known_hosts</li>
 * </ul>
 * Hasil disimpan di atribut session {@link #VERDICT} supaya pemanggil bisa memberi pesan yang jelas.
 */
public final class AppServerKeyVerifier implements ServerKeyVerifier {

    public static final AttributeKey<HostKeyVerdict> VERDICT = new AttributeKey<>();

    private static final Logger log = LoggerFactory.getLogger(AppServerKeyVerifier.class);

    private final KnownHostsStore store;
    private final HostKeyPrompt prompt;

    public AppServerKeyVerifier(KnownHostsStore store, HostKeyPrompt prompt) {
        this.store = Objects.requireNonNull(store, "store");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
    }

    @Override
    public boolean verifyServerKey(ClientSession session, SocketAddress remoteAddress, PublicKey serverKey) {
        SshdSocketAddress target = SshdSocketAddress.toSshdSocketAddress(session.getConnectAddress());
        HostKeyVerdict verdict = verify(target.getHostName(), target.getPort(), serverKey);
        session.setAttribute(VERDICT, verdict);
        return verdict instanceof HostKeyVerdict.Trusted;
    }

    /** Logika verifikasi tanpa ketergantungan ke session (mudah di-unit-test). */
    public HostKeyVerdict verify(String host, int port, PublicKey key) {
        var info = HostKeyInfo.of(host, port, key);
        List<PublicKey> known = store.lookup(host, port);
        if (known.stream().anyMatch(k -> KeyUtils.compareKeys(k, key))) {
            return new HostKeyVerdict.Trusted(info, false);
        }
        if (!known.isEmpty()) {
            var fingerprints = known.stream().map(HostKeyInfo::fingerprint).toList();
            log.warn("HOST KEY BERUBAH untuk {}: {} (tersimpan: {})", info.hostLabel(), info.fingerprint(), fingerprints);
            prompt.hostKeyChanged(info, fingerprints);
            return new HostKeyVerdict.Changed(info, fingerprints);
        }
        if (prompt.confirmUnknownHost(info)) {
            store.add(host, port, key);
            return new HostKeyVerdict.Trusted(info, true);
        }
        return new HostKeyVerdict.RejectedByUser(info);
    }
}
