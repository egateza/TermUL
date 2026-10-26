package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.terminal.TerminalTranslucency;
import dev.egateza.termul.core.Os;
import dev.egateza.termul.core.config.AppConfig;
import java.awt.Color;
import java.awt.IllegalComponentStateException;
import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.RepaintManager;

/**
 * Transparansi window utama, dua slider: bagian selain terminal ({@link TranslucentRootPane}, teks ikut pudar) dan
 * latar terminal ({@link TerminalTranslucency}, teks tetap solid). Keduanya butuh window beralpha per piksel, yang di
 * Java hanya bisa untuk window tanpa dekorasi native.
 *
 * <p>Windows: window utama selalu tanpa dekorasi native + {@link WinCustomFrame} (Snap tetap jalan), jadi semua
 * perubahan langsung berlaku tanpa membuat window ulang. OS lain: hanya kalau window sudah dibuat tanpa dekorasi
 * (transparansi aktif saat start); dari solid ke transparan butuh restart.
 */
final class WindowOpacity {

    private static Color solidBackground; // latar window sebelum dibuat tembus; EDT
    private static Boolean dynamicLayout; // nilai awal Toolkit.isDynamicLayoutActive(); EDT

    private WindowOpacity() {
    }

    /** @return persen jendela yang dibatasi ke {@link AppConfig#MIN_WINDOW_OPACITY}..100 */
    static int clamp(int percent) {
        return Math.max(AppConfig.MIN_WINDOW_OPACITY, Math.min(100, percent));
    }

    /** @return persen latar terminal yang dibatasi ke {@link AppConfig#MIN_TERMINAL_OPACITY}..100 */
    static int clampTerminal(int percent) {
        return Math.max(AppConfig.MIN_TERMINAL_OPACITY, Math.min(100, percent));
    }

    /** @return true kalau window utama selalu memakai bingkai sendiri (Windows) */
    static boolean ownFrame() {
        return Os.current().isWindows();
    }

    /** @return true kalau window utama harus dibuat tanpa dekorasi native sejak awal */
    static boolean undecorated(AppConfig config) {
        return ownFrame() || config.translucent();
    }

    /**
     * Terapkan kedua opasitas (juga untuk pratinjau slider). EDT.
     *
     * @return true kalau berlaku sekarang; false kalau butuh restart (window masih berdekorasi native)
     */
    static boolean apply(JFrame frame, int windowPercent, int terminalPercent) {
        int window = clamp(windowPercent);
        int terminal = clampTerminal(terminalPercent);
        boolean translucent = window < 100 || terminal < 100;
        var root = frame.getRootPane() instanceof TranslucentRootPane t ? t : null;
        if (!frame.isUndecorated() || root == null) {
            return !translucent;
        }
        if (solidBackground == null) {
            solidBackground = frame.getBackground();
        }
        boolean switching = translucent == frame.isOpaque(); // berganti mode solid <-> beralpha per piksel
        if (switching && !translucent && frame.isShowing()) {
            // Ke solid: buat semua piksel penuh dulu selagi window masih beralpha, supaya window yang sudah solid
            // tidak sempat menampilkan isi yang masih dipudarkan (tampak gelap sesaat).
            root.setChromeOpacity(100);
            TerminalTranslucency.setPercent(100);
            paintNow(root);
        }
        try {
            setBackground(frame, root, translucent ? new Color(0, 0, 0, 0) : solidBackground, switching);
        } catch (IllegalComponentStateException | UnsupportedOperationException e) {
            return !translucent; // sistem tanpa dukungan alpha per piksel
        }
        root.setChromeOpacity(translucent ? window : 100);
        TerminalTranslucency.setPercent(translucent ? terminal : 100);
        // window beralpha per piksel digambar ulang penuh tiap langkah resize: tata letak cukup disusun setelah selesai
        var toolkit = Toolkit.getDefaultToolkit();
        if (dynamicLayout == null) {
            dynamicLayout = toolkit.isDynamicLayoutActive();
        }
        toolkit.setDynamicLayout(!translucent && dynamicLayout);
        root.repaint();
        return true;
    }

    /**
     * Ganti latar window. Saat berganti mode, Windows membuang isi window ({@code WS_EX_LAYERED} dipasang/dilepas) dan
     * window kosong sampai digambar ulang penuh: terlihat sebagai kedipan. Selama pergantian, root pane cukup menempel
     * tangkapan isinya, sehingga window langsung terisi lagi dengan gambar yang sama.
     */
    private static void setBackground(JFrame frame, TranslucentRootPane root, Color background, boolean switching) {
        if (!switching || !frame.isShowing() || root.getWidth() <= 0 || root.getHeight() <= 0) {
            frame.setBackground(background);
            return;
        }
        root.setCover(root.capture());
        try {
            frame.setBackground(background); // ke beralpha: AWT langsung menggambar ulang penuh (paintAll) di sini
            if (frame.isOpaque()) {
                paintNow(root); // ke solid: AWT tidak menggambar ulang, isi sendiri sekarang
            }
        } finally {
            root.setCover(null);
        }
    }

    /** Gambar root pane sekarang (bukan nanti lewat antrean repaint), termasuk mengirimnya ke window beralpha. */
    private static void paintNow(JRootPane root) {
        root.repaint();
        RepaintManager.currentManager(root).paintDirtyRegions();
    }
}
