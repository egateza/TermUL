package dev.egateza.myterm.ssh.hostkey;

import java.security.PublicKey;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;

/**
 * Info host key untuk ditampilkan ke user.
 *
 * @param host        host seperti yang ditulis di profil
 * @param port        port SSH
 * @param algorithm   tipe key, mis. {@code ssh-ed25519}
 * @param fingerprint fingerprint {@code SHA256:...}
 */
public record HostKeyInfo(String host, int port, String algorithm, String fingerprint) {

    public static HostKeyInfo of(String host, int port, PublicKey key) {
        return new HostKeyInfo(host, port, KeyUtils.getKeyType(key), fingerprint(key));
    }

    public static String fingerprint(PublicKey key) {
        return KeyUtils.getFingerPrint(BuiltinDigests.sha256, key);
    }

    public String hostLabel() {
        return port == 22 ? host : host + ":" + port;
    }
}
