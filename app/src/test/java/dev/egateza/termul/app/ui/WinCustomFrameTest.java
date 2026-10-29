package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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

    @Test
    void bagianDalamBukanTepi() {
        assertThat(hit(5, 300)).isEqualTo(1);
        assertThat(hit(400, 300)).isEqualTo(1);
        assertThat(hit(794, 594)).isEqualTo(1);
    }
}
