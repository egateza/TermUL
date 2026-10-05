package dev.egateza.termul.update;

import java.net.URI;

/**
 * Konstanta bersama antara Bootstrap, aplikasi, dan ReleaseTool. Lihat {@code docs/adr/0003-self-update.md}.
 */
public final class UpdateProtocol {

    /**
     * Generasi installer. <b>Naikkan</b> setiap kali runtime jlink (modul JDK / versi JDK) atau kode {@link Bootstrap}
     * berubah: rilis dengan generation lebih tinggi tidak bisa dipasang lewat menu, user diarahkan ke installer baru.
     */
    public static final int GENERATION = 1;

    public static final URI RELEASES = URI.create("https://github.com/egateza/TermUL/releases/");
    public static final String MANIFEST = "manifest.json";
    public static final String SIGNATURE = "manifest.json.sig";
    public static final String DEFAULT_MAIN_CLASS = "dev.egateza.termul.app.TermULApp";
    /** Resource build info aplikasi (di jar {@code termul-app}); dibaca Bootstrap dan ReleaseTool. */
    public static final String BUILD_INFO_RESOURCE = "dev/egateza/termul/app/build.properties";

    /** Diset Bootstrap: generation installer yang terpasang. Tidak ada = tidak lewat Bootstrap (IDE/dev). */
    public static final String PROP_GENERATION = "termul.update.generation";
    /** Diset Bootstrap: versi bawaan installer. */
    public static final String PROP_BUNDLED_VERSION = "termul.update.bundled";
    /** Diset Bootstrap kalau aplikasi berjalan dari folder update: versinya. */
    public static final String PROP_RUNNING_UPDATE = "termul.update.running";
    /** Diset Bootstrap: catatan (dipisah baris baru) untuk di-log aplikasi setelah logger siap. */
    public static final String PROP_NOTES = "termul.update.notes";
    /** Argumen program: tunggu proses dengan PID ini selesai (restart setelah update). */
    public static final String ARG_WAIT_PID = "--wait-pid=";

    private UpdateProtocol() {
    }
}
