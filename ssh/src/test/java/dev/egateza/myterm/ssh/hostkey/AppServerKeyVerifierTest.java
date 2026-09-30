package dev.egateza.myterm.ssh.hostkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppServerKeyVerifierTest {

    @TempDir
    Path dir;

    private KnownHostsStore store;
    private RecordingPrompt prompt;
    private AppServerKeyVerifier verifier;

    @BeforeEach
    void setUp() {
        store = new KnownHostsStore(dir.resolve("known_hosts"));
        prompt = new RecordingPrompt();
        verifier = new AppServerKeyVerifier(store, prompt);
    }

    @Test
    void hostBaruDiterimaUserDisimpan() throws Exception {
        PublicKey key = ecKey();
        prompt.answer = true;

        var verdict = verifier.verify("srv.example", 22, key);

        assertThat(verdict).isInstanceOf(HostKeyVerdict.Trusted.class);
        assertThat(((HostKeyVerdict.Trusted) verdict).newlyAdded()).isTrue();
        assertThat(prompt.asked).singleElement().satisfies(info -> {
            assertThat(info.fingerprint()).startsWith("SHA256:");
            assertThat(info.algorithm()).isEqualTo("ecdsa-sha2-nistp256");
        });
        assertThat(Files.readString(store.file())).startsWith("srv.example ecdsa-sha2-nistp256 ");

        // koneksi kedua: tidak bertanya lagi
        prompt.asked.clear();
        assertThat(verifier.verify("srv.example", 22, key)).isInstanceOf(HostKeyVerdict.Trusted.class);
        assertThat(prompt.asked).isEmpty();
    }

    @Test
    void hostBaruDitolakUserTidakDisimpan() throws Exception {
        prompt.answer = false;

        var verdict = verifier.verify("srv.example", 22, ecKey());

        assertThat(verdict).isInstanceOf(HostKeyVerdict.RejectedByUser.class);
        assertThat(store.file()).doesNotExist();
    }

    @Test
    void hostKeyBerubahSelaluDitolakTanpaBertanya() throws Exception {
        PublicKey original = ecKey();
        store.add("srv.example", 22, original);
        prompt.answer = true; // walaupun user "mau", tetap tidak boleh ditanya

        var verdict = verifier.verify("srv.example", 22, ecKey());

        assertThat(verdict).isInstanceOf(HostKeyVerdict.Changed.class);
        assertThat(((HostKeyVerdict.Changed) verdict).knownFingerprints())
                .containsExactly(HostKeyInfo.fingerprint(original));
        assertThat(prompt.asked).isEmpty();
        assertThat(prompt.changed).hasSize(1);
        assertThat(store.lookup("srv.example", 22)).hasSize(1);
    }

    @Test
    void portBerbedaDianggapHostBerbeda() throws Exception {
        PublicKey key = ecKey();
        store.add("srv.example", 2222, key);

        assertThat(Files.readString(store.file())).startsWith("[srv.example]:2222 ");
        assertThat(store.lookup("srv.example", 2222)).hasSize(1);
        assertThat(store.lookup("srv.example", 22)).isEmpty();
    }

    @Test
    void membacaEntryFormatOpenSshDenganKomentar() throws Exception {
        PublicKey key = ecKey();
        var tmp = new KnownHostsStore(dir.resolve("tmp"));
        tmp.add("a", 22, key);
        String keyPart = Files.readString(tmp.file()).strip().substring(2);
        Files.writeString(store.file(), "# komentar\n\nweb1,10.0.0.5 " + keyPart + " ega@laptop\n");

        assertThat(store.lookup("10.0.0.5", 22)).hasSize(1);
        assertThat(store.lookup("web1", 22)).hasSize(1);
        assertThat(store.lookup("web2", 22)).isEmpty();
    }

    private static PublicKey ecKey() throws Exception {
        var gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256);
        return gen.generateKeyPair().getPublic();
    }

    private static final class RecordingPrompt implements HostKeyPrompt {
        boolean answer;
        final List<HostKeyInfo> asked = new ArrayList<>();
        final List<HostKeyInfo> changed = new ArrayList<>();

        @Override
        public boolean confirmUnknownHost(HostKeyInfo info) {
            asked.add(info);
            return answer;
        }

        @Override
        public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            changed.add(presented);
        }
    }
}
