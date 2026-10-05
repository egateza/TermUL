package dev.egateza.termul.update;

import dev.egateza.termul.core.AppPaths;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Main class app-image (jpackage) dan fat jar. Berjalan dari jar bawaan installer, lalu memilih:
 *
 * <ul>
 *   <li>update terpasang yang valid (tanda tangan + hash diverifikasi ulang setiap start dengan public key dari kode
 *       bawaan), dimuat lewat {@link URLClassLoader} ber-parent platform classloader supaya class lama di classpath
 *       bawaan tidak ikut termuat; atau</li>
 *   <li>{@code TermULApp} bawaan, dipanggil langsung seperti sebelum ada fitur update.</li>
 * </ul>
 *
 * <b>Jangan</b> memakai SLF4J/Logback, JNA, atau AWT di sini (dan di class yang dipanggilnya): library itu harus
 * pertama kali dimuat oleh classloader aplikasi. Catatan diteruskan lewat {@link UpdateProtocol#PROP_NOTES}.
 */
public final class Bootstrap {

    static final long WAIT_PID_SECONDS = 30;

    private Bootstrap() {
    }

    public static void main(String[] args) throws Throwable {
        var notes = new ArrayList<String>();
        String[] appArgs = waitForPrevious(args, notes);
        System.setProperty(UpdateProtocol.PROP_GENERATION, String.valueOf(UpdateProtocol.GENERATION));
        ReleaseVersion bundled = bundledVersion(Bootstrap.class.getClassLoader());
        Optional<UpdateStore.Installed> update = Optional.empty();
        if (bundled != null) {
            System.setProperty(UpdateProtocol.PROP_BUNDLED_VERSION, bundled.toString());
            try {
                AppPaths.Detected detected = AppPaths.detect();
                notes.addAll(detected.notes()); // migrasi folder lama terjadi di sini, bukan di TermULApp
                var store = new UpdateStore(detected.paths().updatesDir());
                update = store.select(UpdateKeys.publicKey(), bundled, UpdateProtocol.GENERATION, notes);
                update.ifPresent(u -> store.recordStart(u.manifest().version()));
            } catch (RuntimeException e) {
                notes.add("Pemeriksaan folder update gagal, memakai versi bawaan: " + e);
                update = Optional.empty();
            }
        }
        if (update.isPresent() && launchUpdate(update.get(), appArgs, notes)) {
            return;
        }
        publishNotes(notes);
        invoke(Class.forName(UpdateProtocol.DEFAULT_MAIN_CLASS), appArgs);
    }

    /** @return false kalau class utama update tidak bisa dimuat (pakai versi bawaan) */
    private static boolean launchUpdate(UpdateStore.Installed update, String[] args, List<String> notes)
            throws Throwable {
        String version = update.manifest().version().toString();
        Class<?> main;
        try {
            var loader = new URLClassLoader("termul-" + version, urls(update.jars()),
                    ClassLoader.getPlatformClassLoader());
            main = Class.forName(update.manifest().mainClass(), false, loader);
            Thread.currentThread().setContextClassLoader(loader); // ServiceLoader, LookAndFeel, EDT mewarisi ini
        } catch (ClassNotFoundException | LinkageError | MalformedURLException e) {
            notes.add("Update " + version + " tidak bisa dimuat, memakai versi bawaan: " + e);
            return false;
        }
        System.setProperty(UpdateProtocol.PROP_RUNNING_UPDATE, version);
        notes.add("Menjalankan update " + version + " dari " + update.dir());
        publishNotes(notes);
        invoke(main, args);
        return true;
    }

    private static void invoke(Class<?> main, String[] args) throws Throwable {
        try {
            main.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static URL[] urls(List<Path> jars) throws MalformedURLException {
        var urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = jars.get(i).toUri().toURL();
        }
        return urls;
    }

    /** Versi bawaan dari {@code build.properties} di jar aplikasi; null untuk build dev (update tidak dipakai). */
    static ReleaseVersion bundledVersion(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream(UpdateProtocol.BUILD_INFO_RESOURCE)) {
            if (in == null) {
                return null;
            }
            var props = new Properties();
            props.load(in);
            return ReleaseVersion.parseOrNull(props.getProperty("version"));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Restart setelah update: proses lama mengirim {@code --wait-pid=<pid>} supaya proses baru menunggu proses lama
     * selesai menutup sesi dan menyimpan state.
     *
     * @return argumen tanpa {@code --wait-pid}
     */
    static String[] waitForPrevious(String[] args, List<String> notes) {
        var rest = new ArrayList<String>();
        for (String a : args) {
            if (!a.startsWith(UpdateProtocol.ARG_WAIT_PID)) {
                rest.add(a);
                continue;
            }
            try {
                long pid = Long.parseLong(a.substring(UpdateProtocol.ARG_WAIT_PID.length()));
                Optional<ProcessHandle> previous = ProcessHandle.of(pid);
                if (previous.isPresent()) {
                    previous.get().onExit().get(WAIT_PID_SECONDS, TimeUnit.SECONDS);
                }
            } catch (NumberFormatException e) {
                notes.add("Argumen tidak valid diabaikan: " + a);
            } catch (TimeoutException e) {
                notes.add("Proses TermUL sebelumnya belum selesai setelah " + WAIT_PID_SECONDS + " detik");
            } catch (ExecutionException e) {
                // proses sudah selesai
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return rest.toArray(String[]::new);
    }

    private static void publishNotes(List<String> notes) {
        if (!notes.isEmpty()) {
            System.setProperty(UpdateProtocol.PROP_NOTES, String.join("\n", notes));
        }
    }
}
