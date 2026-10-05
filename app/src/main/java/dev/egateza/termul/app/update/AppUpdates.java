package dev.egateza.termul.app.update;

import dev.egateza.termul.app.BuildInfo;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.update.ReleaseFeed;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.update.UpdateKeys;
import dev.egateza.termul.update.UpdateProtocol;
import dev.egateza.termul.update.UpdateStore;
import dev.egateza.termul.update.Updater;
import java.io.IOException;
import org.slf4j.Logger;

/** Wiring fitur update di aplikasi (lihat {@code docs/adr/0003-self-update.md}). */
public final class AppUpdates {

    private AppUpdates() {
    }

    /** Catatan dari Bootstrap (dibuat sebelum logger ada). */
    public static void logBootstrapNotes(Logger log) {
        String notes = System.getProperty(UpdateProtocol.PROP_NOTES);
        if (notes != null && !notes.isBlank()) {
            notes.lines().forEach(note -> log.info("Bootstrap: {}", note));
        }
    }

    /**
     * Tandai update yang sedang berjalan sebagai sehat (window utama sudah tampil), supaya Bootstrap tidak kembali ke
     * versi bawaan. Disk I/O: panggil di luar EDT.
     */
    public static void markHealthy(AppPaths paths, Logger log) {
        ReleaseVersion running = ReleaseVersion.parseOrNull(System.getProperty(UpdateProtocol.PROP_RUNNING_UPDATE));
        if (running == null) {
            return;
        }
        try {
            new UpdateStore(paths.updatesDir()).markHealthy(running);
        } catch (IOException e) {
            log.warn("Update {} tidak bisa ditandai sehat: {}", running, e.toString());
        }
    }

    public static Updater updater(AppPaths paths, ReleaseFeed feed) {
        return new Updater(feed, new UpdateStore(paths.updatesDir()), UpdateKeys.publicKey(),
                Updater.Environment.detect(BuildInfo.load().version()));
    }
}
