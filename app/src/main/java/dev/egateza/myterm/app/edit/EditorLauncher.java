package dev.egateza.myterm.app.edit;

import dev.egateza.myterm.core.config.EditorConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Menjalankan editor lokal. Di Windows, command tanpa path dicari di PATH (PATHEXT); script
 * {@code .cmd}/{@code .bat} (mis. {@code code.cmd} milik VS Code) dijalankan lewat {@code cmd.exe /c}.
 *
 * <p>Aman karena path file yang diteruskan selalu path cache yang namanya sudah disanitasi
 * ({@code EditCache}), jadi tidak ada metakarakter {@code cmd.exe} dari nama file remote.
 */
public final class EditorLauncher {

    private final Supplier<EditorConfig> config;

    public EditorLauncher(Supplier<EditorConfig> config) {
        this.config = config;
    }

    public Process launch(Path file) throws IOException {
        List<String> command = resolve(config.get().commandFor(file));
        var pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try {
            return pb.start();
        } catch (IOException e) {
            throw new IOException("Editor tidak bisa dijalankan (" + command.getFirst() + "). Cek pengaturan editor.", e);
        }
    }

    static List<String> resolve(List<String> command) {
        if (!isWindows()) {
            return command;
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

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
