package dev.egateza.termul.app.update;

import dev.egateza.termul.core.Os;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.update.UpdateProtocol;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Instalasi Windows (app-image jpackage) yang sedang berjalan: apakah opsi JVM hemat memori dari installer
 * ({@code jpackage.javaOptions} di app/pom.xml) sudah dipakai, dan link unduhan installer baru. Opsi itu tertulis di
 * {@code TermUL.cfg}, jadi hanya didapat lewat pasang ulang zip, bukan update lewat menu.
 */
public final class InstallerInfo {

    /** System property penanda di {@code TermUL.cfg} installer yang membawa opsi hemat memori. */
    static final String MEMORY_MARKER = "termul.installer.memory";
    /** Penanda di installer v0.1.139 (sebelum ada {@link #MEMORY_MARKER}). */
    private static final String SERIAL_GC = "-XX:+UseSerialGC";

    private InstallerInfo() {
    }

    /** Folder instalasi kalau berjalan dari app-image Windows; kosong untuk JAR portable, macOS, atau IDE. */
    static Optional<Path> installDir(Function<String, String> props, Os os) {
        String exe = props.apply(RestartCommand.JPACKAGE_APP_PATH);
        if (!os.isWindows() || exe == null || exe.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Path.of(exe).getParent());
    }

    /** @return true kalau instalasi Windows ini belum memakai opsi JVM hemat memori dari installer terbaru */
    static boolean needsReinstall(Function<String, String> props, Os os, List<String> jvmArgs) {
        return installDir(props, os).isPresent()
                && !Boolean.parseBoolean(props.apply(MEMORY_MARKER))
                && !jvmArgs.contains(SERIAL_GC);
    }

    public static boolean needsReinstall() {
        return needsReinstall(System::getProperty, Os.current(),
                ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    static Optional<Path> installDir() {
        return installDir(System::getProperty, Os.current());
    }

    /** Nama aset zip Windows yang diterbitkan tools/release.ps1. */
    static String windowsZipName(ReleaseVersion version) {
        return "TermUL-" + version + "-windows.zip";
    }

    static URI windowsZip(ReleaseVersion version) {
        return UpdateProtocol.RELEASES.resolve("download/" + version.tag() + "/" + windowsZipName(version));
    }

    static URI releasePage(ReleaseVersion version) {
        return UpdateProtocol.RELEASES.resolve("tag/" + version.tag());
    }
}
