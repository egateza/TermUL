package dev.egateza.termul.app.monitor;

import com.sun.management.HotSpotDiagnosticMXBean;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;
import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Penggunaan memori" di menu Pengaturan: mengubah flag JVM yang bisa diubah saat berjalan ({@code manageable}),
 * jadi langsung berlaku tanpa restart dan ikut update lewat menu. Batas heap ({@code -Xmx}) dan GC sendiri diatur di
 * opsi installer ({@code jpackage.javaOptions} di app/pom.xml), bukan di sini.
 *
 * <p>Mode hemat: heap yang tidak terpakai dikembalikan ke OS setelah GC (rasio ruang kosong heap 10–30%, bawaan JVM
 * 40–70%), dan dengan G1 ada GC berkala saat idle supaya pengembalian itu juga terjadi saat tidak ada aktivitas.
 * Ukuran heap terpakai tidak berubah; yang berkurang hanya cadangan kosong.
 */
public final class MemoryTuning {

    /** Pilihan di menu; disimpan di config sebagai {@link #id}. */
    public enum Mode {
        SAVER(AppConfig.MEMORY_SAVER),
        NORMAL(AppConfig.MEMORY_NORMAL);

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String label() {
            return I18n.t("main.menu.settings.memory." + id);
        }

        /** @return mode dengan id tersebut, atau {@link #SAVER} kalau tidak dikenal */
        public static Mode fromId(String id) {
            return NORMAL.id.equals(id) ? NORMAL : SAVER;
        }
    }

    static final String MIN_FREE = "MinHeapFreeRatio";
    static final String MAX_FREE = "MaxHeapFreeRatio";
    static final String PERIODIC_GC = "G1PeriodicGCInterval";
    static final int SAVER_MIN_FREE = 10;
    static final int SAVER_MAX_FREE = 30;
    /** GC berkala saat idle (ms); hanya terjadi kalau tidak ada GC lain selama selang ini. Diabaikan selain G1. */
    static final long SAVER_PERIODIC_GC_MS = 30_000;

    private static final Logger log = LoggerFactory.getLogger(MemoryTuning.class);
    private static MemoryTuning platform; // guarded by MemoryTuning.class

    private final HotSpotDiagnosticMXBean vm;
    private final int launchMinFree;   // nilai saat JVM start (opsi installer atau bawaan JVM)
    private final int launchMaxFree;
    private final long launchPeriodicGc;

    MemoryTuning(HotSpotDiagnosticMXBean vm) {
        this.vm = vm;
        this.launchMinFree = Integer.parseInt(vm.getVMOption(MIN_FREE).getValue());
        this.launchMaxFree = Integer.parseInt(vm.getVMOption(MAX_FREE).getValue());
        this.launchPeriodicGc = periodicGcSupported() ? Long.parseLong(vm.getVMOption(PERIODIC_GC).getValue()) : 0;
    }

    /** Instance untuk JVM ini, nilai awal flag dicatat saat pertama dipanggil. */
    public static synchronized MemoryTuning platform() {
        if (platform == null) {
            platform = new MemoryTuning(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class));
        }
        return platform;
    }

    /**
     * Terapkan mode; {@link Mode#NORMAL} mengembalikan nilai saat JVM start. Gagal (JVM tanpa flag tersebut) cukup
     * di-log, aplikasi tetap jalan.
     *
     * @return true kalau semua flag berhasil diset
     */
    public synchronized boolean apply(Mode mode) {
        try {
            if (mode == Mode.SAVER) {
                setRatios(SAVER_MIN_FREE, SAVER_MAX_FREE);
                setPeriodicGc(SAVER_PERIODIC_GC_MS);
            } else {
                setRatios(launchMinFree, launchMaxFree);
                setPeriodicGc(launchPeriodicGc);
            }
            log.info("Penggunaan memori: {} ({}={}, {}={})", mode.id(), MIN_FREE, vm.getVMOption(MIN_FREE).getValue(),
                    MAX_FREE, vm.getVMOption(MAX_FREE).getValue());
            return true;
        } catch (RuntimeException e) { // IllegalArgumentException: flag tidak ada / tidak bisa diubah di JVM ini
            log.warn("Pengaturan penggunaan memori {} tidak bisa diterapkan: {}", mode.id(), e.getMessage());
            return false;
        }
    }

    /** JVM menolak Min &gt; Max, jadi urutan set bergantung pada arah perubahan. */
    private void setRatios(int min, int max) {
        int currentMax = Integer.parseInt(vm.getVMOption(MAX_FREE).getValue());
        if (min <= currentMax) {
            vm.setVMOption(MIN_FREE, String.valueOf(min));
            vm.setVMOption(MAX_FREE, String.valueOf(max));
        } else {
            vm.setVMOption(MAX_FREE, String.valueOf(max));
            vm.setVMOption(MIN_FREE, String.valueOf(min));
        }
    }

    private void setPeriodicGc(long millis) {
        if (periodicGcSupported()) {
            vm.setVMOption(PERIODIC_GC, String.valueOf(millis));
        }
    }

    /** Flag {@value #PERIODIC_GC} hanya ada di G1 (instalasi baru memakai Serial GC). */
    private boolean periodicGcSupported() {
        try {
            return Boolean.parseBoolean(vm.getVMOption("UseG1GC").getValue());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
