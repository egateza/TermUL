package dev.egateza.termul.ssh.hostkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnownHostsStoreTest {

    @TempDir
    Path dir;

    private KnownHostsStore store;

    @BeforeEach
    void setUp() {
        store = new KnownHostsStore(dir.resolve("known_hosts"));
    }

    @Test
    void fileBelumAdaDaftarKosong() {
        assertThat(store.entries()).isEmpty();
        assertThat(store.entriesFor("srv", 22)).isEmpty();
        assertThat(store.remove(List.of())).isZero();
    }

    @Test
    void daftarEntryDenganFingerprint() throws Exception {
        PublicKey a = ecKey();
        PublicKey b = ecKey();
        store.add("srv.example", 22, a);
        store.add("srv.example", 2222, b);

        assertThat(store.entries()).satisfiesExactly(
                e -> {
                    assertThat(e.hosts()).isEqualTo("srv.example");
                    assertThat(e.algorithm()).isEqualTo("ecdsa-sha2-nistp256");
                    assertThat(e.fingerprint()).isEqualTo(HostKeyInfo.fingerprint(a));
                    assertThat(e.marker()).isNull();
                    assertThat(e.hashed()).isFalse();
                },
                e -> assertThat(e.hosts()).isEqualTo("[srv.example]:2222"));
        assertThat(store.entriesFor("srv.example", 2222)).singleElement()
                .satisfies(e -> assertThat(e.fingerprint()).isEqualTo(HostKeyInfo.fingerprint(b)));
    }

    @Test
    void hapusHanyaBarisYangDipilihKomentarDanRevokedTetapUtuh() throws Exception {
        PublicKey keep = ecKey();
        PublicKey old = ecKey();
        PublicKey revoked = ecKey();
        Files.writeString(store.file(), String.join("\n",
                "# komentar",
                "keep.example " + PublicKeyEntry.toString(keep),
                "srv.example " + PublicKeyEntry.toString(old),
                "@revoked srv.example " + PublicKeyEntry.toString(revoked),
                "baris rusak",
                ""), StandardCharsets.UTF_8);

        var forHost = store.entriesFor("srv.example", 22);
        assertThat(forHost).singleElement().satisfies(e -> assertThat(e.fingerprint()).isEqualTo(HostKeyInfo.fingerprint(old)));
        assertThat(store.remove(forHost)).isEqualTo(1);

        assertThat(store.lookup("srv.example", 22)).isEmpty();
        assertThat(store.lookup("keep.example", 22)).singleElement().isEqualTo(keep);
        String content = Files.readString(store.file());
        assertThat(content).contains("# komentar", "@revoked srv.example", "baris rusak");
        assertThat(store.entries()).extracting(KnownHostsStore.Entry::marker).containsExactly(null, "revoked");

        assertThat(store.remove(forHost)).isZero(); // sudah tidak ada
    }

    @Test
    void entryHashedCocokDenganHostDanBisaDihapus() throws Exception {
        PublicKey key = ecKey();
        Files.writeString(store.file(), hashed("[srv.example]:2222") + " " + PublicKeyEntry.toString(key) + "\n",
                StandardCharsets.UTF_8);

        assertThat(store.entries()).singleElement().satisfies(e -> assertThat(e.hashed()).isTrue());
        assertThat(store.entriesFor("srv.example", 22)).isEmpty();
        var match = store.entriesFor("srv.example", 2222);
        assertThat(match).hasSize(1);

        store.remove(match);
        assertThat(store.entries()).isEmpty();
    }

    /** Pola host hashed OpenSSH: {@code |1|base64(salt)|base64(HMAC-SHA1(salt, host))}. */
    private static String hashed(String host) throws Exception {
        byte[] salt = new byte[20];
        new java.security.SecureRandom().nextBytes(salt);
        var mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(salt, "HmacSHA1"));
        byte[] hash = mac.doFinal(host.getBytes(StandardCharsets.UTF_8));
        var b64 = Base64.getEncoder();
        return "|1|" + b64.encodeToString(salt) + "|" + b64.encodeToString(hash);
    }

    private static PublicKey ecKey() throws Exception {
        var gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        return gen.generateKeyPair().getPublic();
    }
}
