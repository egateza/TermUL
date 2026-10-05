package dev.egateza.termul.app.update;

import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.core.Os;
import dev.egateza.termul.update.UpdateProtocol;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Perintah untuk membuka ulang TermUL setelah update. Proses baru menerima {@code --wait-pid} supaya Bootstrap
 * menunggu proses ini selesai menutup sesi dan menyimpan state.
 */
public final class RestartCommand {

    /** Diset launcher jpackage: path {@code TermUL.exe} / {@code TermUL.app/Contents/MacOS/TermUL}. */
    static final String JPACKAGE_APP_PATH = "jpackage.app-path";

    private RestartCommand() {
    }

    public static Optional<List<String>> current() {
        return of(System::getProperty, ProcessHandle.current().info().command(), ProcessHandle.current().pid(),
                Os.current());
    }

    /**
     * @param props       pembaca system property
     * @param javaCommand executable JVM yang sedang berjalan (untuk fat jar)
     * @return kosong kalau cara membuka ulang tidak diketahui (mis. dijalankan dari IDE)
     */
    static Optional<List<String>> of(Function<String, String> props, Optional<String> javaCommand, long pid, Os os) {
        String waitArg = UpdateProtocol.ARG_WAIT_PID + pid;
        String appPath = props.apply(JPACKAGE_APP_PATH);
        if (appPath != null && !appPath.isBlank()) {
            return Optional.of(List.of(appPath, waitArg));
        }
        String classPath = props.apply("java.class.path");
        boolean singleJar = classPath != null && classPath.endsWith(".jar") && !classPath.contains(File.pathSeparator);
        if (!singleJar || javaCommand.isEmpty() || props.apply(UpdateProtocol.PROP_GENERATION) == null) {
            return Optional.empty();
        }
        var cmd = new ArrayList<String>();
        cmd.add(javaCommand.get());
        cmd.add("--enable-native-access=ALL-UNNAMED");
        String home = props.apply(AppPaths.HOME_OVERRIDE_PROPERTY);
        if (home != null) {
            cmd.add("-D" + AppPaths.HOME_OVERRIDE_PROPERTY + "=" + home); // data dev/test tetap terpisah
        }
        if (os.isMac()) {
            cmd.add("-Xdock:name=TermUL");
        }
        cmd.add("-jar");
        cmd.add(classPath);
        cmd.add(waitArg);
        return Optional.of(List.copyOf(cmd));
    }
}
