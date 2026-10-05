package dev.egateza.termul.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

/**
 * Entry point fat jar ({@code Main-Class}), di-compile untuk Java 8 supaya tetap jalan kalau JAR di-double-click dengan
 * Java lama (file association {@code .jar} sering menunjuk JDK lama). Di Java 25+ langsung menjalankan TermUL. Di Java
 * lama mencari JDK 25+ yang terpasang lalu menjalankan ulang JAR ini dengannya; kalau tidak ada, tampilkan dialog.
 *
 * <p>Sengaja hanya memakai API Java 8 dan tidak mereferensikan class TermUL secara langsung (class Java 25 tidak bisa
 * dimuat Java lama).
 */
public final class Launcher {

    static final int REQUIRED = 25;
    static final String MAIN_CLASS = "dev.egateza.termul.app.TermULApp";
    /** Penanda proses hasil relaunch, supaya tidak relaunch berulang. */
    static final String RELAUNCHED = "termul.relaunched";
    /** Sama dengan {@code AppPaths.HOME_OVERRIDE_PROPERTY}; diteruskan ke proses relaunch. */
    static final String HOME_OVERRIDE = "termul.home";

    private static final Pattern RELEASE_VERSION = Pattern.compile("^JAVA_VERSION=\"([^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern REG_VALUE = Pattern.compile("^\\s+\\S.*?\\s+REG_(?:EXPAND_)?SZ\\s+(.+)$");

    private Launcher() {
    }

    public static void main(String[] args) throws Throwable {
        if (major(System.getProperty("java.specification.version")) >= REQUIRED) {
            try {
                Class.forName(MAIN_CLASS).getMethod("main", String[].class).invoke(null, (Object) args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            return;
        }
        String current = System.getProperty("java.version") + " (" + System.getProperty("java.home") + ")";
        Path jar = ownJar();
        Path java = Boolean.getBoolean(RELAUNCHED) || jar == null ? null : findJava();
        if (java != null) {
            List<String> cmd = new ArrayList<String>();
            cmd.add(java.toString());
            cmd.add("--enable-native-access=ALL-UNNAMED");
            cmd.add("-D" + RELAUNCHED + "=true");
            String home = System.getProperty(HOME_OVERRIDE);
            if (home != null) {
                cmd.add("-D" + HOME_OVERRIDE + "=" + home); // data dev/test tetap terpisah dari data asli
            }
            if (isMac()) {
                cmd.add("-Xdock:name=TermUL");
            }
            cmd.add("-jar");
            cmd.add(jar.toString());
            for (String a : args) {
                cmd.add(a);
            }
            try {
                new ProcessBuilder(cmd).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(nullFile())).start();
                return;
            } catch (IOException e) {
                fail("TermUL butuh Java " + REQUIRED + ", tapi Java yang ditemukan tidak bisa dijalankan:\n" + java
                        + "\n\n" + e.getMessage());
                return;
            }
        }
        fail("TermUL butuh Java " + REQUIRED + " atau lebih baru.\n\nJava yang dipakai sekarang: " + current
                + "\n\nPasang JDK " + REQUIRED + " (mis. Temurin dari adoptium.net), lalu buka TermUL lagi.");
    }

    /** @return versi mayor dari {@code java.specification.version} / {@code JAVA_VERSION}: "1.8" → 8, "25.0.2" → 25 */
    static int major(String version) {
        if (version == null || version.isEmpty()) {
            return -1;
        }
        String v = version.startsWith("1.") ? version.substring(2) : version;
        Matcher m = Pattern.compile("^(\\d+)").matcher(v);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** @return versi mayor JDK di {@code home} dari file {@code release}, atau -1 */
    static int majorOfHome(Path home) {
        try {
            String release = new String(Files.readAllBytes(home.resolve("release")), StandardCharsets.UTF_8);
            Matcher m = RELEASE_VERSION.matcher(release);
            return m.find() ? major(m.group(1)) : -1;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** JDK 25+ dengan versi tertinggi dari kandidat, atau null. */
    static Path pickJava(Iterable<Path> homes, boolean windows) {
        Path best = null;
        int bestMajor = -1;
        for (Path home : homes) {
            Path exe = home.resolve("bin").resolve(windows ? "javaw.exe" : "java");
            int m = majorOfHome(home);
            if (m >= REQUIRED && m > bestMajor && Files.isRegularFile(exe)) {
                best = exe;
                bestMajor = m;
            }
        }
        return best;
    }

    private static Path findJava() {
        boolean windows = isWindows();
        Set<Path> homes = new LinkedHashSet<Path>();
        addHome(homes, System.getenv("JAVA_HOME"));
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(Pattern.quote(File.pathSeparator))) {
                if (!dir.trim().isEmpty()) {
                    Path p = Paths.get(dir.trim().replace("\"", ""));
                    if (p.getParent() != null) {
                        addHome(homes, p.getParent().toString()); // <home>/bin
                    }
                }
            }
        }
        String userHome = System.getProperty("user.home");
        addChildren(homes, Paths.get(userHome, ".jdks"), ""); // JDK unduhan IntelliJ
        if (windows) {
            for (String key : new String[] {"HKLM\\SOFTWARE\\Eclipse Adoptium", "HKCU\\SOFTWARE\\Eclipse Adoptium",
                    "HKLM\\SOFTWARE\\JavaSoft", "HKLM\\SOFTWARE\\Microsoft\\JDK", "HKLM\\SOFTWARE\\Azul Systems",
                    "HKLM\\SOFTWARE\\Amazon Corretto", "HKLM\\SOFTWARE\\BellSoft"}) {
                for (String value : registryValues(key)) {
                    addHome(homes, value);
                }
            }
            for (File root : File.listRoots()) {
                for (String vendor : new String[] {"Eclipse Adoptium", "Java", "Microsoft", "Zulu", "Amazon Corretto",
                        "BellSoft"}) {
                    addChildren(homes, root.toPath().resolve("Program Files").resolve(vendor), "");
                }
            }
        } else if (isMac()) {
            String home = run("/usr/libexec/java_home", "-v", REQUIRED + "+");
            if (home != null) {
                addHome(homes, home.trim());
            }
            addChildren(homes, Paths.get("/Library/Java/JavaVirtualMachines"), "Contents/Home");
            addChildren(homes, Paths.get(userHome, "Library/Java/JavaVirtualMachines"), "Contents/Home");
            addChildren(homes, Paths.get("/opt/homebrew/opt"), "libexec/openjdk.jdk/Contents/Home");
            addChildren(homes, Paths.get("/usr/local/opt"), "libexec/openjdk.jdk/Contents/Home");
        } else {
            addChildren(homes, Paths.get("/usr/lib/jvm"), "");
        }
        return pickJava(homes, windows);
    }

    private static void addHome(Set<Path> homes, String dir) {
        if (dir == null || dir.trim().isEmpty()) {
            return;
        }
        try {
            Path p = Paths.get(dir.trim());
            if (Files.isRegularFile(p.resolve("release"))) {
                homes.add(p);
            }
        } catch (RuntimeException ignored) {
            // nilai registry/PATH yang bukan path valid
        }
    }

    private static void addChildren(Set<Path> homes, Path parent, String suffix) {
        if (!Files.isDirectory(parent)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(parent)) {
            for (Path child : children) {
                addHome(homes, (suffix.isEmpty() ? child : child.resolve(suffix)).toString());
            }
        } catch (IOException | RuntimeException ignored) {
            // folder tidak bisa dibaca: lewati
        }
    }

    /** Semua nilai string di bawah {@code key} (rekursif), mis. {@code Path} / {@code JavaHome}. */
    private static List<String> registryValues(String key) {
        List<String> values = new ArrayList<String>();
        String out = run("reg", "query", key, "/s");
        if (out != null) {
            for (String line : out.split("\\r?\\n")) {
                Matcher m = REG_VALUE.matcher(line);
                if (m.matches()) {
                    values.add(m.group(1).trim());
                }
            }
        }
        return values;
    }

    /** @return stdout, atau null kalau gagal / exit code bukan 0 */
    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 ? sb.toString() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Path ownJar() {
        try {
            Path p = Paths.get(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static File nullFile() {
        return new File(isWindows() ? "NUL" : "/dev/null");
    }

    private static void fail(String message) {
        System.err.println("TermUL: " + message);
        try {
            JOptionPane.showMessageDialog(null, message, "TermUL", JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException | Error ignored) {
            // headless: pesan sudah di stderr
        }
        System.exit(1);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }
}
