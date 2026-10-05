package dev.egateza.termul.app.edit;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.Os;
import dev.egateza.termul.core.config.EditorConfig;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menjalankan editor lokal. Di Windows, command tanpa path dicari di PATH (PATHEXT); script
 * {@code .cmd}/{@code .bat} (mis. {@code code.cmd} milik VS Code) dijalankan lewat {@code cmd.exe /c}.
 * Di macOS, aplikasi yang dibuka dari Finder hanya mendapat PATH minimal, jadi command tanpa path juga dicari di
 * folder CLI yang umum ({@link #extraUnixDirs}), dan {@code code} jatuh ke CLI di dalam bundle VS Code.
 *
 * <p>Aman karena path file yang diteruskan selalu path cache yang namanya sudah disanitasi
 * ({@code EditCache}), jadi tidak ada metakarakter {@code cmd.exe} dari nama file remote.
 *
 * <p>Template {@link #SYSTEM_DEFAULT} membuka file dengan aplikasi default OS (file association Windows /
 * LaunchServices macOS), kecuali file yang dijalankan OS saat dibuka ({@link #isExecutable}) atau tanpa ekstensi: itu
 * dibuka dengan editor dari pengaturan, supaya file dari server tidak pernah dieksekusi di lokal.
 */
public final class EditorLauncher {

    private static final Logger log = LoggerFactory.getLogger(EditorLauncher.class);

    /** Template khusus: buka dengan aplikasi default OS (double-click / menu "Buka"). */
    public static final String SYSTEM_DEFAULT = "<system-default>";

    /**
     * Ekstensi yang dijalankan (bukan dibuka untuk dibaca) lewat association Windows atau macOS; ditambah
     * {@code PATHEXT}. Daftar gabungan dipakai di semua OS: lebih aman membuka file seperti itu dengan editor.
     */
    static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
            "exe", "com", "bat", "cmd", "scr", "pif", "cpl", "msc", "msi", "msp", "mst", "appx", "appxbundle",
            "msix", "msixbundle", "appinstaller", "application", "appref-ms", "gadget", "diagcab", "msh", "msh1",
            "msh2", "mshxml", "msh1xml", "msh2xml", "ps1", "ps1xml", "ps2", "ps2xml", "psc1", "psc2", "psd1",
            "psm1", "vb", "vbe", "vbs", "js", "jse", "ws", "wsc", "wsf", "wsh", "hta", "jar", "reg", "inf", "scf",
            "lnk", "url", "website", "settingcontent-ms", "library-ms", "search-ms", "searchconnector-ms",
            "chm", "hlp", "py", "pyw", "pyc", "pyz", "rb", "pl", "iso", "img", "vhd", "vhdx", "xll", "xbap",
            // macOS: bundle aplikasi/installer, script yang dibuka Terminal, AppleScript/Automator, lokasi/URL
            "app", "command", "tool", "sh", "bash", "zsh", "csh", "ksh", "tcsh", "fish", "pkg", "mpkg", "dmg",
            "terminal", "scpt", "scptd", "applescript", "workflow", "action", "prefpane", "kext", "plugin",
            "bundle", "dylib", "webloc", "inetloc", "fileloc", "jnlp");

    private final Supplier<EditorConfig> config;

    public EditorLauncher(Supplier<EditorConfig> config) {
        this.config = config;
    }

    public Process launch(Path file) throws IOException {
        return launch(file, null);
    }

    /**
     * @param template command editor pilihan user, {@link #SYSTEM_DEFAULT}, atau null = editor sesuai
     *                 ekstensi/default dari pengaturan
     * @return proses editor; null kalau dibuka dengan aplikasi default OS (tidak ada proses untuk ditunggu)
     */
    public Process launch(Path file, String template) throws IOException {
        if (SYSTEM_DEFAULT.equals(template)) {
            if (openWithSystem(file)) {
                return null;
            }
            template = null;
        }
        List<String> command = resolve(template == null
                ? config.get().commandFor(file) : EditorConfig.commandFor(file, template));
        var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try {
            return pb.start();
        } catch (IOException e) {
            throw new IOException(I18n.t("edit.launcher.failed", command.getFirst()), e);
        }
    }

    /** @return false kalau tidak bisa/tidak boleh dibuka lewat association (pemanggil memakai editor) */
    private static boolean openWithSystem(Path file) {
        String name = file.getFileName().toString();
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return false;
        }
        if (extension(name).isEmpty() || isExecutable(name)) {
            log.info("{} tidak dibuka dengan aplikasi default (executable/tanpa ekstensi), pakai editor", name);
            return false;
        }
        try {
            Desktop.getDesktop().open(file.toFile());
            return true;
        } catch (IOException | RuntimeException e) {
            log.info("Aplikasi default untuk {} tidak bisa dibuka, pakai editor: {}", name, e.toString());
            return false;
        }
    }

    /** True kalau OS menjalankan file ini (bukan membukanya untuk dibaca) lewat association. */
    static boolean isExecutable(String fileName) {
        String ext = extension(fileName);
        if (ext.isEmpty()) {
            return false;
        }
        if (EXECUTABLE_EXTENSIONS.contains(ext)) {
            return true;
        }
        for (String e : System.getenv().getOrDefault("PATHEXT", "").split(";")) {
            if (e.strip().toLowerCase(Locale.ROOT).equals("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /** Ekstensi huruf kecil tanpa titik (titik/spasi di akhir diabaikan seperti di Windows); "" kalau tidak ada. */
    static String extension(String fileName) {
        String name = fileName.replaceAll("[. ]+$", "");
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static List<String> resolve(List<String> command) {
        if (!Os.current().isWindows()) {
            return resolveUnix(command, System.getenv("PATH"), Path.of(System.getProperty("user.home")));
        }
        String exe = command.getFirst();
        Path resolved = exe.contains("\\") || exe.contains("/") ? Path.of(exe) : findOnPath(exe);
        if (resolved == null) {
            return command;
        }
        String lower = resolved.toString().toLowerCase(Locale.ROOT);
        var result = new ArrayList<String>();
        if (lower.endsWith(".cmd") || lower.endsWith(".bat")) {
            result.add("cmd.exe");
            result.add("/c");
        }
        result.add(resolved.toString());
        result.addAll(command.subList(1, command.size()));
        return result;
    }

    private static Path findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        String pathExt = System.getenv().getOrDefault("PATHEXT", ".COM;.EXE;.BAT;.CMD");
        boolean hasExt = name.contains(".");
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path base = Path.of(dir.strip().replace("\"", ""));
            if (hasExt && Files.isRegularFile(base.resolve(name))) {
                return base.resolve(name);
            }
            for (String ext : pathExt.split(";")) {
                Path candidate = base.resolve(name + ext.toLowerCase(Locale.ROOT));
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** macOS/Linux: command tanpa path diganti path absolut kalau ditemukan; kalau tidak, dibiarkan apa adanya. */
    static List<String> resolveUnix(List<String> command, String pathEnv, Path home) {
        String exe = command.getFirst();
        if (exe.contains("/")) {
            return command;
        }
        var dirs = new ArrayList<Path>();
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                if (!dir.isBlank()) {
                    dirs.add(Path.of(dir));
                }
            }
        }
        dirs.addAll(extraUnixDirs(home));
        Path resolved = dirs.stream().map(d -> d.resolve(exe)).filter(EditorLauncher::isRunnable).findFirst()
                .orElse(null);
        if (resolved == null && exe.equals("code")) {
            resolved = vsCodeCli(home).stream().filter(EditorLauncher::isRunnable).findFirst().orElse(null);
        }
        if (resolved == null) {
            return command;
        }
        var result = new ArrayList<String>(command);
        result.set(0, resolved.toString());
        return result;
    }

    /** Folder CLI yang umum di macOS tapi tidak ada di PATH aplikasi yang dibuka dari Finder/Dock. */
    static List<Path> extraUnixDirs(Path home) {
        return List.of(Path.of("/opt/homebrew/bin"), Path.of("/usr/local/bin"), home.resolve(".local").resolve("bin"),
                home.resolve("bin"));
    }

    /** CLI {@code code} di dalam bundle VS Code, kalau "Install 'code' command in PATH" belum dijalankan. */
    static List<Path> vsCodeCli(Path home) {
        String cli = "Visual Studio Code.app/Contents/Resources/app/bin/code";
        return List.of(Path.of("/Applications").resolve(cli), home.resolve("Applications").resolve(cli));
    }

    private static boolean isRunnable(Path p) {
        return Files.isRegularFile(p) && Files.isExecutable(p);
    }
}
