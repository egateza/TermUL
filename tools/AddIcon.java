import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menambah/mengganti/menghapus ikon aplikasi untuk kedua set unduhan (Font Awesome Free + Material Symbols Rounded).
 * Set orisinal TermUL (Garis, Duoton, Piksel) tidak diunduh: setelah menambah ikon, gambar SVG-nya secara manual di
 * folder set masing-masing (tool ini menampilkan file yang perlu dibuat); {@code --remove} ikut menghapusnya.
 * Jalankan dari root project (JDK 25, tanpa compile):
 *
 * <pre>
 *   java tools/AddIcon.java NAMA fa-nama material_nama     tambah atau ganti ikon
 *   java tools/AddIcon.java --remove NAMA                   hapus ikon
 *   java tools/AddIcon.java --all                           unduh ulang semua ikon dari icons.properties
 * </pre>
 *
 * Contoh: {@code java tools/AddIcon.java TERMINAL terminal terminal}
 * Nama Font Awesome: https://fontawesome.com/search?ic=free (default gaya solid; tulis {@code regular/nama} untuk
 * gaya regular). Nama Material: https://fonts.google.com/icons (pakai huruf kecil + underscore, mis. folder_open).
 * Setelah itu pakai di kode sebagai {@code AppIcon.NAMA.icon()}.
 */
public class AddIcon {

    static final String FA_VERSION = "7.3.1";
    static final String MATERIAL_COMMIT = "bd8cb85bd4bad964fe6918f79665bb40c3a8efef";

    static final Path RES = Path.of("app/src/main/resources/dev/egateza/termul/app/icons");
    static final Path MANIFEST = RES.resolve("icons.properties");
    static final Path ENUM = Path.of("app/src/main/java/dev/egateza/termul/app/ui/AppIcon.java");
    static final String BEGIN = "    // <icons> (dikelola oleh tools/AddIcon.java)";
    static final String END = "    // </icons>";

    static final List<String> SETS = List.of("fa", "material");
    /** Set orisinal TermUL (IconSet.GARIS/DUOTON/PIKSEL): tidak diunduh, SVG digambar manual. */
    static final List<String> ORIGINAL_SETS = List.of("garis", "duoton", "piksel");

    static final Pattern NAME = Pattern.compile("[A-Z][A-Z0-9_]*");
    static final Pattern FA_NAME = Pattern.compile("((solid|regular|brands)/)?[a-z0-9-]+");
    static final Pattern MATERIAL_NAME = Pattern.compile("[a-z0-9_]+");
    static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"(-?[\\d.]+) (-?[\\d.]+) ([\\d.]+) ([\\d.]+)\"");

    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    record Source(String fa, String material) {
        String faPath() {
            return fa.contains("/") ? fa : "solid/" + fa;
        }

        @Override
        public String toString() {
            return "fa:" + faPath() + ", material:" + material;
        }

        static Source parse(String value) {
            String fa = null;
            String material = null;
            for (String part : value.split(",")) {
                String p = part.strip();
                if (p.startsWith("fa:")) {
                    fa = p.substring(3).strip();
                } else if (p.startsWith("material:")) {
                    material = p.substring(9).strip();
                }
            }
            if (fa == null || material == null) {
                throw new IllegalArgumentException("Format salah di icons.properties: " + value);
            }
            return new Source(fa, material);
        }
    }

    public static void main(String[] args) throws Exception {
        if (!Files.isRegularFile(ENUM)) {
            fail("Jalankan dari root project (folder yang berisi pom.xml).");
        }
        Map<String, Source> icons = readManifest();
        switch (args.length > 0 ? args[0] : "") {
            case "--all" -> {
                for (var e : icons.entrySet()) {
                    download(e.getKey(), e.getValue());
                }
            }
            case "--remove" -> {
                if (args.length != 2) {
                    usage();
                }
                String name = args[1];
                if (icons.remove(name) == null) {
                    fail("Ikon " + name + " tidak ada.");
                }
                for (String set : SETS) {
                    Files.deleteIfExists(file(set, name));
                }
                for (String set : ORIGINAL_SETS) {
                    Files.deleteIfExists(file(set, name));
                }
                System.out.println("Dihapus: " + name + " (pastikan tidak dipakai lagi di kode)");
            }
            default -> {
                if (args.length != 3) {
                    usage();
                }
                String name = args[0];
                var source = new Source(args[1], args[2]);
                check(NAME, name, "NAMA harus huruf besar + underscore, mis. FOLDER_OPEN");
                check(FA_NAME, source.fa(), "nama Font Awesome tidak valid, mis. folder-open atau regular/folder");
                check(MATERIAL_NAME, source.material(), "nama Material tidak valid, mis. folder_open");
                download(name, source);
                icons.put(name, source);
                for (String set : ORIGINAL_SETS) {
                    if (!Files.isRegularFile(file(set, name))) {
                        System.out.println("Perlu digambar manual: " + file(set, name)
                                + " (set orisinal TermUL; AppIconTest gagal sampai file ini ada)");
                    }
                }
            }
        }
        writeManifest(icons);
        writeEnum(icons);
        System.out.println("Selesai. " + icons.size() + " ikon terdaftar di " + MANIFEST);
    }

    static void download(String name, Source source) throws IOException, InterruptedException {
        String fa = get("https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@" + FA_VERSION
                + "/svgs/" + source.faPath() + ".svg", "Font Awesome '" + source.fa() + "'");
        String m = source.material();
        String material = get("https://raw.githubusercontent.com/google/material-design-icons/" + MATERIAL_COMMIT
                + "/symbols/web/" + m + "/materialsymbolsrounded/" + m + "_24px.svg", "Material '" + m + "'");
        write(file("fa", name), squareViewBox(fa));
        write(file("material", name), squareViewBox(material));
        System.out.println("OK " + name + " <- " + source);
    }

    static String get(String url, String what) throws IOException, InterruptedException {
        var res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200 || !res.body().contains("<svg")) {
            fail("Ikon " + what + " tidak ditemukan (HTTP " + res.statusCode() + "): " + url);
        }
        return res.body();
    }

    /** viewBox dibuat persegi (konten di tengah) supaya ikon tidak gepeng saat dirender 16x16. */
    static String squareViewBox(String svg) {
        Matcher m = VIEW_BOX.matcher(svg);
        if (!m.find()) {
            fail("SVG tanpa viewBox");
        }
        double x = Double.parseDouble(m.group(1));
        double y = Double.parseDouble(m.group(2));
        double w = Double.parseDouble(m.group(3));
        double h = Double.parseDouble(m.group(4));
        if (w == h) {
            return svg;
        }
        double n = Math.max(w, h);
        String box = "viewBox=\"%s %s %s %s\"".formatted(num(x - (n - w) / 2), num(y - (n - h) / 2), num(n), num(n));
        return svg.substring(0, m.start()) + box + svg.substring(m.end());
    }

    static String num(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    static Path file(String set, String name) {
        return RES.resolve(set).resolve(name.toLowerCase() + ".svg");
    }

    static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.strip() + "\n", StandardCharsets.UTF_8);
    }

    static Map<String, Source> readManifest() throws IOException {
        var map = new TreeMap<String, Source>();
        if (Files.exists(MANIFEST)) {
            for (String line : Files.readAllLines(MANIFEST, StandardCharsets.UTF_8)) {
                String l = line.strip();
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }
                int eq = l.indexOf('=');
                map.put(l.substring(0, eq).strip(), Source.parse(l.substring(eq + 1)));
            }
        }
        return map;
    }

    static void writeManifest(Map<String, Source> icons) throws IOException {
        var lines = new ArrayList<String>();
        lines.add("# Sumber ikon per konstanta AppIcon. Dikelola oleh tools/AddIcon.java; jangan edit manual.");
        lines.add("# fa = Font Awesome Free " + FA_VERSION + " (CC BY 4.0), material = Material Symbols Rounded "
                + "(Apache 2.0, google/material-design-icons@" + MATERIAL_COMMIT.substring(0, 7) + ")");
        icons.forEach((name, source) -> lines.add(name + " = " + source));
        Files.writeString(MANIFEST, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    static void writeEnum(Map<String, Source> icons) throws IOException {
        String src = Files.readString(ENUM, StandardCharsets.UTF_8);
        int begin = src.indexOf(BEGIN);
        int end = src.indexOf(END);
        if (begin < 0 || end < begin) {
            fail("Penanda <icons> tidak ditemukan di " + ENUM);
        }
        List<String> names = new ArrayList<>(icons.keySet());
        var sb = new StringBuilder(BEGIN).append('\n');
        for (int i = 0; i < names.size(); i++) {
            sb.append("    ").append(names.get(i)).append(i == names.size() - 1 ? ";" : ",").append('\n');
        }
        String updated = src.substring(0, begin) + sb + src.substring(end);
        Files.writeString(ENUM, updated, StandardCharsets.UTF_8);
    }

    static void check(Pattern p, String value, String message) {
        if (!p.matcher(value).matches()) {
            fail(message + " (diberikan: " + value + ")");
        }
    }

    static void usage() {
        fail("""
                Pemakaian (dari root project):
                  java tools/AddIcon.java NAMA fa-nama material_nama   tambah/ganti ikon
                  java tools/AddIcon.java --remove NAMA                 hapus ikon
                  java tools/AddIcon.java --all                         unduh ulang semua ikon""");
    }

    static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
