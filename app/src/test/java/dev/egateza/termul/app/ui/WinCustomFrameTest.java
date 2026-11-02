package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class WinCustomFrameTest {

    private static final int W = 800;
    private static final int H = 600;

    private static int hit(int x, int y) {
        return WinCustomFrame.edgeHit(x, y, W, H, 5, 16);
    }

    /** Tepi resize dilaporkan ke Windows (HTLEFT dst.) supaya resize & Snap ditangani Windows sendiri. */
    @Test
    void tepiDanPojokWindow() {
        assertThat(hit(2, 300)).isEqualTo(10); // kiri
        assertThat(hit(797, 300)).isEqualTo(11); // kanan
        assertThat(hit(400, 1)).isEqualTo(12); // atas
        assertThat(hit(400, 598)).isEqualTo(15); // bawah
        assertThat(hit(1, 1)).isEqualTo(13); // kiri atas
        assertThat(hit(798, 2)).isEqualTo(14); // kanan atas
        assertThat(hit(2, 597)).isEqualTo(16); // kiri bawah
        assertThat(hit(798, 598)).isEqualTo(17); // kanan bawah
    }

    @Test
    void pojokLebihPanjangDariTebalTepi() {
        assertThat(hit(10, 2)).isEqualTo(13); // di tepi atas, dekat pojok kiri
        assertThat(hit(2, 12)).isEqualTo(13); // di tepi kiri, dekat pojok atas
        assertThat(hit(20, 2)).isEqualTo(12); // sudah lewat panjang pojok: tepi atas biasa
    }

    /** Yang bertooltip (meter / teks "Host status") menerima mouse; wordmark dan pemisah tetap area seret window. */
    @Test
    void komponenBertooltipMenerimaMouseWordmarkTetapAreaSeret() {
        var label = new javax.swing.JLabel("214 MB / 256 MB");
        assertThat(WinCustomFrame.isControl(label)).isFalse();
        label.setToolTipText("Heap: ...");
        assertThat(WinCustomFrame.isControl(label)).isTrue();
        assertThat(WinCustomFrame.isControl(new Wordmark())).isFalse();
        var divider = dev.egateza.termul.app.monitor.HostStatusPanel.verticalDivider(14);
        divider.putClientProperty("JComponent.titleBarCaption", true);
        divider.setToolTipText("x");
        assertThat(WinCustomFrame.isControl(divider)).as("titleBarCaption menang").isFalse();
    }

    /**
     * Ganti tema / font aplikasi membuat ulang title bar FlatLaf di tempat yang sama, tanpa event resize. Klik maximize
     * sesudahnya harus sampai ke tombol yang baru, bukan ke tombol lama yang sudah tidak tampil.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void maximizeTetapJalanSetelahTitleBarDibuatUlang() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        var originalLaf = UIManager.getLookAndFeel();
        var frame = new AtomicReference<JFrame>();
        var custom = new AtomicReference<WinCustomFrame>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                FlatDarkLaf.setup();
                var f = new JFrame("test");
                f.setUndecorated(true);
                f.getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
                var cf = new WinCustomFrame(f);
                f.setBounds(80, 80, 600, 300);
                f.addNotify();
                cf.install();
                f.setVisible(true);
                frame.set(f);
                custom.set(cf);
            });
            SwingUtilities.invokeAndWait(() -> {
                FlatLightLaf.setup();
                FlatLaf.updateUI(); // title bar & tombolnya dibuat ulang
            });
            SwingUtilities.invokeAndWait(() -> { }); // geometri yang dijadwalkan ulang
            SwingUtilities.invokeAndWait(() -> custom.get().clickMaxButton());

            await().atMost(Duration.ofSeconds(5))
                    .until(() -> (frame.get().getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame.get() != null) {
                    frame.get().dispose();
                }
                try {
                    UIManager.setLookAndFeel(originalLaf);
                } catch (UnsupportedLookAndFeelException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    /** Monitor kedua 125%: area kerja fisik 1920x1020 di kiri monitor utama. */
    private static final int[] WORK = {-1920, 0, 0, 1020};

    @Test
    void windowYangMembesarKarenaScalingDipaskanKeAreaKerja() {
        // 1280x800 logis di 125% = 1600x1000 fisik; tingginya muat, tapi dilepas di y=80 bawahnya tertutup taskbar
        assertThat(WinCustomFrame.fitToWorkArea(new int[] {-1900, 80, -300, 1080}, WORK, true))
                .containsExactly(-1900, 20, -300, 1020);
        // lebih tinggi dari area kerja: diperkecil, lalu digeser masuk
        assertThat(WinCustomFrame.fitToWorkArea(new int[] {-1800, 100, -200, 1200}, WORK, true))
                .containsExactly(-1800, 0, -200, 1020);
        // terlalu lebar & menjorok keluar kiri
        assertThat(WinCustomFrame.fitToWorkArea(new int[] {-2000, 10, 100, 500}, WORK, true))
                .containsExactly(-1920, 10, 0, 500);
    }

    @Test
    void selamaDiseretHanyaUkuranYangDipaskan() {
        assertThat(WinCustomFrame.fitToWorkArea(new int[] {-1800, 100, -200, 1200}, WORK, false))
                .containsExactly(-1800, 100, -200, 1120); // tinggi 1020, posisi tetap ikut kursor
    }

    @Test
    void windowYangSudahMuatTidakBerubah() {
        int[] rect = {-1700, 50, -500, 850};
        assertThat(WinCustomFrame.fitToWorkArea(rect, WORK, true)).containsExactly(rect);
    }

    @Test
    void bagianDalamBukanTepi() {
        assertThat(hit(5, 300)).isEqualTo(1);
        assertThat(hit(400, 300)).isEqualTo(1);
        assertThat(hit(794, 594)).isEqualTo(1);
    }
}
