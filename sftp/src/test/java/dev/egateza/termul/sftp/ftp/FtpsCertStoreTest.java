package dev.egateza.termul.sftp.ftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FtpsCertStoreTest {

    @TempDir
    Path tmp;

    @Test
    void trustFindRemove() {
        var store = new FtpsCertStore(tmp.resolve("ftps_known_certs"));
        assertThat(store.find("ftp.example.com", 21)).isEmpty();

        store.trust("FTP.Example.com", 21, "SHA256:aaa");
        store.trust("ftp.example.com", 990, "SHA256:bbb");
        store.trust("ftp.example.com", 21, "SHA256:ccc"); // ganti entry host:port yang sama

        assertThat(store.find("ftp.example.com", 21)).contains("SHA256:ccc");
        assertThat(store.find("ftp.example.com", 990)).contains("SHA256:bbb");
        assertThat(store.entries()).hasSize(2);

        assertThat(store.remove("ftp.example.com", 21)).isTrue();
        assertThat(store.remove("ftp.example.com", 21)).isFalse();
        assertThat(store.find("ftp.example.com", 21)).isEmpty();
    }

    @Test
    void barisRusakDanKomentarDilewati() throws Exception {
        Path file = Files.writeString(tmp.resolve("ftps_known_certs"), """
                # komentar
                host.ok 21 SHA256:abc
                rusak
                host.port 99999 SHA256:abc
                host.fp 21 MD5:abc
                [::1] 21 SHA256:v6
                """);
        var store = new FtpsCertStore(file);
        assertThat(store.entries()).extracting(FtpsCertStore.Entry::host).containsExactly("host.ok", "[::1]");
        assertThat(store.entries().getFirst().hostLabel()).isEqualTo("host.ok:21");
    }
}
