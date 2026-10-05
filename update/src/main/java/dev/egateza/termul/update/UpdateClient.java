package dev.egateza.termul.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * Mengambil rilis dari GitHub Releases: {@code <base>latest/download/manifest.json}, lalu tanda tangan dan jar dari
 * {@code <base>download/v<versi>/} (versi pasti, supaya tidak tercampur kalau ada rilis baru di tengah proses).
 * Redirect GitHub ke CDN-nya diikuti, tapi tidak pernah turun dari HTTPS ke HTTP.
 */
public final class UpdateClient implements ReleaseFeed, AutoCloseable {

    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final String USER_AGENT = "TermUL-Updater";

    private final URI base;
    private final HttpClient http;

    /** Klien ke repo rilis resmi ({@link UpdateProtocol#RELEASES}). */
    public UpdateClient() {
        this(UpdateProtocol.RELEASES);
    }

    /**
     * @param base URL folder releases dengan garis miring di akhir. Wajib HTTPS; HTTP hanya untuk loopback (test).
     */
    public UpdateClient(URI base) {
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && isLoopback(base.getHost()))) {
            throw new IllegalArgumentException("URL update wajib HTTPS: " + base);
        }
        if (!base.getPath().endsWith("/")) {
            throw new IllegalArgumentException("URL update harus diakhiri '/': " + base);
        }
        this.base = base;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Ambil manifest rilis terbaru dan verifikasi tanda tangannya. */
    @Override
    public SignedRelease fetchLatest(PublicKey key) throws UpdateException, InterruptedException {
        byte[] manifest = fetchSmall(base.resolve("latest/download/" + UpdateProtocol.MANIFEST), MAX_MANIFEST_BYTES,
                "Belum ada rilis TermUL yang mendukung update otomatis di GitHub.");
        // versi dibaca dari byte yang belum diverifikasi hanya untuk menentukan URL tanda tangannya
        ReleaseVersion version = UpdateManifest.parse(manifest).version();
        byte[] signature = fetchSmall(asset(version, UpdateProtocol.SIGNATURE), ManifestSignature.MAX_SIGNATURE_TEXT,
                "Rilis " + version + " tidak punya file tanda tangan; update ditolak.");
        return SignedRelease.verify(manifest, signature, key);
    }

    @Override
    public void download(ReleaseVersion version, UpdateManifest.FileEntry entry, Path target, LongConsumer progress)
            throws IOException, UpdateException, InterruptedException {
        URI uri = asset(version, entry.name());
        HttpResponse<InputStream> response = send(uri, Duration.ofMinutes(10));
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            checkStatus(response, uri, "File " + entry.name() + " tidak ada di rilis " + version + ".");
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > entry.size()) {
                    throw new UpdateException("File " + entry.name() + " lebih besar dari yang tercatat di manifest; "
                            + "unduhan dihentikan.");
                }
                out.write(buf, 0, n);
                progress.accept(n);
                if (Thread.interrupted()) {
                    throw new InterruptedException("Unduhan dibatalkan");
                }
            }
        }
    }

    URI asset(ReleaseVersion version, String name) {
        return base.resolve("download/" + version.tag() + "/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
    }

    private byte[] fetchSmall(URI uri, int limit, String notFoundMessage) throws UpdateException, InterruptedException {
        HttpResponse<InputStream> response = send(uri, Duration.ofSeconds(30));
        try (InputStream in = response.body()) {
            checkStatus(response, uri, notFoundMessage);
            byte[] data = in.readNBytes(limit + 1);
            if (data.length > limit) {
                throw new UpdateException("Respons " + uri + " terlalu besar.");
            }
            return data;
        } catch (IOException e) {
            throw new UpdateException("Gagal membaca " + uri + ": " + e.getMessage(), e);
        }
    }

    private HttpResponse<InputStream> send(URI uri, Duration timeout) throws UpdateException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", USER_AGENT).GET().build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpConnectTimeoutException | ConnectException | UnknownHostException e) {
            throw new UpdateException("Tidak bisa terhubung ke " + uri.getHost() + ". Periksa koneksi internet.", e);
        } catch (HttpTimeoutException e) {
            throw new UpdateException("Server update tidak merespons (timeout).", e);
        } catch (IOException e) {
            throw new UpdateException("Gagal menghubungi server update: " + e.getMessage(), e);
        }
    }

    private static void checkStatus(HttpResponse<?> response, URI uri, String notFoundMessage) throws UpdateException {
        int status = response.statusCode();
        if (status == 404) {
            throw new UpdateException(notFoundMessage);
        }
        if (status != 200) {
            throw new UpdateException("Server update membalas HTTP " + status + " untuk " + uri);
        }
    }

    private static boolean isLoopback(String host) {
        try {
            return host != null && InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @Override
    public void close() {
        http.shutdownNow(); // close() menunggu request yang masih jalan; di sini dipanggil juga saat batal
    }
}
