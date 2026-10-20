package dev.egateza.termul.localterm;

import dev.egateza.termul.core.Os;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Shell lokal per OS (ADR 0005): deteksi shell yang terpasang dan penyusunan {@link LocalShell} dari perintah,
 * direktori, dan environment profil. Logikanya murni (OS, environment, pemeriksa file, isi {@code /etc/shells}
 * disuntikkan) supaya bisa di-test di OS mana pun.
 */
public final class LocalShells {

    static final String MAC_FALLBACK = "/bin/zsh";
    static final String LINUX_FALLBACK = "/bin/bash";

    private LocalShells() {
    }

    /**
     * Shell yang terpasang di mesin ini (tanpa distro WSL, lihat {@link #wsl}), yang pertama adalah default otomatis.
     * Blocking (membaca {@code /etc/shells} dan memeriksa file): jangan dipanggil di EDT.
     */
    public static List<DetectedShell> detectCurrent() {
        Os os = Os.current();
        List<String> etcShells = List.of();
        if (!os.isWindows()) {
            try {
                etcShells = Files.readAllLines(Path.of("/etc/shells"));
            } catch (IOException e) {
                // tidak ada /etc/shells: cukup $SHELL dan cadangan
            }
        }
        return detect(os, System.getenv(), LocalShells::isExecutable, etcShells, List.of());
    }

    static boolean isExecutable(Path p) {
        return Files.isRegularFile(p) && Files.isExecutable(p);
    }

    static List<DetectedShell> detect(Os os, Map<String, String> env, Predicate<Path> executable,
                                      List<String> etcShells, List<String> wslDistros) {
        return os.isWindows() ? detectWindows(env, executable, wslDistros) : detectUnix(os, env, executable, etcShells);
    }

    /** Shell default untuk mesin ini, mulai di home user (cadangan kalau tidak ada profil/deteksi). */
    static LocalShell defaultFor(Os os, Map<String, String> env, Predicate<Path> executable, Path home) {
        var first = detect(os, env, executable, List.of(), List.of()).getFirst();
        return new LocalShell(first.command(), home, environment(os, env));
    }

    /**
     * Shell login ({@code -l}) supaya PATH dari profil shell terbaca (aplikasi dari Finder hanya dapat PATH minimal).
     * Urutan: {@code $SHELL}, lalu isi {@code /etc/shells} (satu per nama, mis. bash dari /bin dan /usr/bin cukup sekali).
     */
    private static List<DetectedShell> detectUnix(Os os, Map<String, String> env, Predicate<Path> executable,
                                                  List<String> etcShells) {
        var paths = new ArrayList<String>();
        String login = env.get("SHELL");
        if (login != null && login.startsWith("/") && executable.test(Path.of(login))) {
            paths.add(login);
        }
        for (String line : etcShells) {
            String s = line.strip();
            if (s.startsWith("/") && executable.test(Path.of(s))) {
                paths.add(s);
            }
        }
        var names = new HashSet<String>();
        var result = new ArrayList<DetectedShell>();
        for (String path : paths) {
            String name = baseName(path);
            if (names.add(name)) {
                result.add(new DetectedShell(path, name, List.of(path, "-l")));
            }
        }
        if (result.isEmpty()) {
            String fallback = os.isMac() ? MAC_FALLBACK : LINUX_FALLBACK;
            result.add(new DetectedShell(fallback, baseName(fallback), List.of(fallback, "-l")));
        }
        return result;
    }

    /** PowerShell 7, Windows PowerShell, cmd, Git Bash, lalu distro WSL. PowerShell 7 jadi default kalau ada. */
    private static List<DetectedShell> detectWindows(Map<String, String> env, Predicate<Path> executable,
                                                     List<String> wslDistros) {
        var result = new ArrayList<DetectedShell>();
        String root = Optional.ofNullable(getIgnoreCase(env, "SystemRoot")).orElse("C:\\Windows");
        String programFiles = Optional.ofNullable(getIgnoreCase(env, "ProgramFiles")).orElse("C:\\Program Files");
        String system32 = Path.of(root, "System32").toString();

        findInPath("pwsh.exe", pathOf(env), executable)
                .or(() -> existing(executable, Path.of(programFiles, "PowerShell", "7", "pwsh.exe")))
                .ifPresent(p -> result.add(new DetectedShell("pwsh", "PowerShell 7", List.of(p.toString(), "-NoLogo"))));

        Path builtin = Path.of(system32, "WindowsPowerShell", "v1.0", "powershell.exe");
        result.add(new DetectedShell("powershell", "Windows PowerShell",
                List.of(executable.test(builtin) ? builtin.toString() : "powershell.exe", "-NoLogo")));

        String comSpec = getIgnoreCase(env, "ComSpec");
        Path cmd = comSpec != null ? Path.of(comSpec) : Path.of(system32, "cmd.exe");
        result.add(new DetectedShell("cmd", "Command Prompt", List.of(executable.test(cmd) ? cmd.toString() : "cmd.exe")));

        gitBash(env, executable, programFiles).ifPresent(p ->
                result.add(new DetectedShell("git-bash", "Git Bash", List.of(p.toString(), "--login", "-i"))));

        result.addAll(wsl(env, wslDistros));
        return result;
    }

    /**
     * Shell untuk distro WSL ({@code wsl.exe -d <distro>}), tanpa I/O: daftar distro berasal dari WSL Manager. Kosong di
     * luar Windows atau kalau Manajemen WSL mati.
     */
    public static List<DetectedShell> wsl(List<String> distros) {
        return Os.current().isWindows() ? wsl(System.getenv(), distros) : List.of();
    }

    private static List<DetectedShell> wsl(Map<String, String> env, List<String> distros) {
        String root = Optional.ofNullable(getIgnoreCase(env, "SystemRoot")).orElse("C:\\Windows");
        String exe = Path.of(root, "System32", "wsl.exe").toString();
        return distros.stream()
                .map(d -> new DetectedShell("wsl:" + d, "WSL · " + d, List.of(exe, "-d", d)))
                .toList();
    }

    /**
     * {@code bin\bash.exe} milik Git for Windows (bukan {@code git-bash.exe}, yang membuka window mintty sendiri): dari
     * lokasi instalasi umum, atau dari {@code git.exe} di PATH ({@code <Git>\cmd\git.exe}).
     */
    private static Optional<Path> gitBash(Map<String, String> env, Predicate<Path> executable, String programFiles) {
        var candidates = new ArrayList<Path>();
        candidates.add(Path.of(programFiles, "Git", "bin", "bash.exe"));
        String x86 = getIgnoreCase(env, "ProgramFiles(x86)");
        if (x86 != null) {
            candidates.add(Path.of(x86, "Git", "bin", "bash.exe"));
        }
        String localAppData = getIgnoreCase(env, "LOCALAPPDATA");
        if (localAppData != null) {
            candidates.add(Path.of(localAppData, "Programs", "Git", "bin", "bash.exe"));
        }
        findInPath("git.exe", pathOf(env), executable)
                .map(git -> git.getParent() == null || git.getParent().getParent() == null ? null
                        : git.getParent().getParent().resolve("bin").resolve("bash.exe"))
                .ifPresent(candidates::add);
        return candidates.stream().filter(executable).findFirst();
    }

    private static Optional<Path> existing(Predicate<Path> executable, Path p) {
        return executable.test(p) ? Optional.of(p) : Optional.empty();
    }

    /**
     * Susun shell dari profil.
     *
     * @param directory kosong = home; {@code ~} atau {@code ~/...} = relatif ke home
     * @param overrides variabel tambahan/pengganti; nilai kosong = variabel dihapus
     */
    public static LocalShell build(List<String> command, String directory, Map<String, String> overrides) {
        return build(command, directory, overrides, Os.current(), System.getenv(),
                Path.of(System.getProperty("user.home")));
    }

    static LocalShell build(List<String> command, String directory, Map<String, String> overrides, Os os,
                            Map<String, String> inherited, Path home) {
        var env = environment(os, inherited);
        overrides.forEach((name, value) -> {
            if (os.isWindows()) {
                env.keySet().removeIf(k -> k.equalsIgnoreCase(name)); // nama variabel Windows tidak peka huruf
            } else {
                env.remove(name);
            }
            if (value != null && !value.isEmpty()) {
                env.put(name, value);
            }
        });
        return new LocalShell(command, directory(directory, home), env);
    }

    static Path directory(String directory, Path home) {
        String d = directory == null ? "" : directory.strip();
        if (d.isEmpty() || d.equals("~")) {
            return home;
        }
        if (d.startsWith("~/") || d.startsWith("~\\")) {
            return home.resolve(d.substring(2));
        }
        return Path.of(d);
    }

    /** Cari {@code exe} di PATH Windows (dipisah {@code ;}, entri boleh berkutip). */
    static Optional<Path> findInPath(String exe, String path, Predicate<Path> executable) {
        if (path == null) {
            return Optional.empty();
        }
        for (String dir : path.split(";")) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir.strip().replace("\"", ""), exe);
            if (executable.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static String pathOf(Map<String, String> env) {
        return getIgnoreCase(env, "PATH");
    }

    /** Nama variabel di Windows tidak peka huruf besar/kecil ("Path", "SystemRoot"). */
    private static String getIgnoreCase(Map<String, String> env, String name) {
        String exact = env.get(name);
        if (exact != null) {
            return exact;
        }
        return env.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(slash + 1);
    }

    /**
     * Environment shell: warisan TermUL + {@code TERM}/{@code COLORTERM}/{@code TERM_PROGRAM} untuk JediTerm, dan
     * {@code LANG} UTF-8 kalau belum ada di macOS/Linux (aplikasi dari Finder tidak mendapat LANG, sehingga zsh salah
     * menampilkan karakter non-ASCII).
     */
    static Map<String, String> environment(Os os, Map<String, String> inherited) {
        var env = new HashMap<>(inherited);
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("TERM_PROGRAM", "TermUL");
        if (!os.isWindows() && blank(env.get("LANG")) && blank(env.get("LC_ALL")) && blank(env.get("LC_CTYPE"))) {
            env.put("LANG", "en_US.UTF-8");
        }
        return env;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
