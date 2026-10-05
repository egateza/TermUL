package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Klien terhadap server HTTP lokal yang meniru struktur URL GitHub Releases (termasuk redirect "latest"). */
class UpdateClientTest {

    @TempDir
    Path tmp;

    private final KeyPair key = Fixtures.keyPair();
    private final Map<String, byte[]> files = new HashMap<>();
    private HttpServer server;
    private UpdateClient client;
    private SignedRelease release;

    @BeforeEach
    void setUp() throws Exception {
        var r = new Fixtures.Release().version("0.1.200").jar("termul-app.jar", "app baru");
        release = r.signed(key);
        files.put("/releases/download/v0.1.200/manifest.json", release.manifestJson());
        files.put("/releases/download/v0.1.200/manifest.json.sig", release.signature());
        files.put("/releases/download/v0.1.200/termul-app.jar", r.jars.get("termul-app.jar"));

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/releases/latest/download/manifest.json")) {
                exchange.getResponseHeaders().add("Location", "/releases/download/v0.1.200/manifest.json");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
            byte[] body = files.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        client = new UpdateClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/releases/"));
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.stop(0);
    }

    @Test
    void ambilRilisTerbaruLewatRedirect() throws Exception {
        var latest = client.fetchLatest(key.getPublic());
        assertThat(latest.manifest()).isEqualTo(release.manifest());
    }

    @Test
    void tanpaFileTandaTanganDitolak() {
        files.remove("/releases/download/v0.1.200/manifest.json.sig");
        assertThatThrownBy(() -> client.fetchLatest(key.getPublic()))
                .isInstanceOf(UpdateException.class).hasMessageContaining("tanda tangan");
    }

    @Test
    void tandaTanganKeyLainDitolak() {
        assertThatThrownBy(() -> client.fetchLatest(Fixtures.keyPair().getPublic()))
                .isInstanceOf(UpdateException.class).hasMessageContaining("Tanda tangan");
    }

    @Test
    void belumAdaRilis() {
        files.clear();
        server.removeContext("/");
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        assertThatThrownBy(() -> client.fetchLatest(key.getPublic()))
                .isInstanceOf(UpdateException.class).hasMessageContaining("Belum ada rilis");
    }

    @Test
    void unduhJar() throws Exception {
        var entry = release.manifest().files().getFirst();
        var target = tmp.resolve("termul-app.jar");
        var bytes = new AtomicLong();

        client.download(release.version(), entry, target, bytes::addAndGet);

        assertThat(Hashes.matches(target, entry)).isTrue();
        assertThat(bytes.get()).isEqualTo(entry.size());
    }

    @Test
    void jarLebihBesarDariManifestDihentikan() throws IOException {
        var entry = release.manifest().files().getFirst();
        files.put("/releases/download/v0.1.200/termul-app.jar", new byte[(int) entry.size() + 1000]);

        assertThatThrownBy(() -> client.download(release.version(), entry, tmp.resolve("x.jar"), n -> { }))
                .isInstanceOf(UpdateException.class).hasMessageContaining("lebih besar");
        assertThat(Files.size(tmp.resolve("x.jar"))).isLessThanOrEqualTo(entry.size());
    }

    @Test
    void httpKeHostLuarDitolak() {
        assertThatThrownBy(() -> new UpdateClient(URI.create("http://github.com/egateza/TermUL/releases/")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateClient(URI.create("https://github.com/egateza/TermUL/releases")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urlAsetMemakaiTagVersi() {
        try (var official = new UpdateClient()) {
            assertThat(official.asset(ReleaseVersion.parse("0.1.130"), "termul-app-0.1.0-SNAPSHOT.jar"))
                    .hasToString("https://github.com/egateza/TermUL/releases/download/v0.1.130/"
                            + "termul-app-0.1.0-SNAPSHOT.jar");
        }
    }
}
