package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.egateza.termul.core.config.AppConfig;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TermTabbedPaneUITest {

    private LookAndFeel previous;

    @BeforeEach
    void installFlatLaf() throws Exception {
        previous = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new FlatDarkLaf());
    }

    @AfterEach
    void restoreLaf() throws Exception {
        UIManager.setLookAndFeel(previous);
    }

    @Test
    void powerlineMasukKeLekukanTabBerikutnyaKecualiTabTerakhir() {
        var middle = TermTabbedPaneUI.powerlineShape(100, 0, 80, 30, false, false);
        assertThat(middle.contains(100 + 80 + TermTabbedPaneUI.OVERLAP - 1, 15)).as("ujung panah melewati kotak")
                .isTrue();
        assertThat(middle.contains(101, 15)).as("lekukan kiri kosong").isFalse();
        assertThat(middle.contains(101, 1)).isTrue();

        var first = TermTabbedPaneUI.powerlineShape(0, 0, 80, 30, true, false);
        assertThat(first.contains(1, 15)).as("tab pertama tanpa lekukan").isTrue();

        var last = TermTabbedPaneUI.powerlineShape(100, 0, 80, 30, false, true);
        assertThat(last.getBounds2D().getMaxX()).as("tab terakhir tidak keluar kotak").isLessThanOrEqualTo(180);
    }

    @Test
    void tabBrowserAktifMelengkungKeluarDiKakiBawah() {
        var shape = TermTabbedPaneUI.browserShape(100, 0, 80, 30);
        assertThat(shape.getBounds2D().getMinX()).isEqualTo(100 - TermTabbedPaneUI.FOOT);
        assertThat(shape.getBounds2D().getMaxX()).isEqualTo(180 + TermTabbedPaneUI.FOOT);
        assertThat(shape.contains(140, 2)).as("lebih rendah dari tab lain di atas").isFalse();
        assertThat(shape.contains(140, 29)).isTrue();
    }

    @Test
    void kapsulDiTengahDanTidakLebihDari25Px() {
        var pill = TermTabbedPaneUI.pillShape(0, 0, 80, 40).getBounds2D();
        assertThat(pill.getHeight()).isEqualTo(25);
        assertThat(pill.getCenterY()).isEqualTo(20);
    }

    @Test
    void teksDiAtasAksenTetapTerbaca() {
        assertThat(TermTabbedPaneUI.onColor(new Color(0x7AA2F7))).isEqualTo(new Color(0x16161E));
        assertThat(TermTabbedPaneUI.onColor(new Color(0x1E3A8A))).isEqualTo(Color.WHITE);
    }

    /** Setiap gaya menggambar tab bar tanpa error, dan gaya yang berbeda memberi gambar yang berbeda. */
    @Test
    void setiapGayaTergambarDanBerbeda() throws Exception {
        var images = new java.util.LinkedHashMap<String, int[]>();
        var frame = new javax.swing.JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new javax.swing.JFrame();
            frame[0].setSize(420, 160);
            frame[0].setVisible(true); // tab bar scroll baru menata tab setelah tampil
            for (String style : AppConfig.TAB_STYLES) {
                var tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
                tabs.setUI(new TermTabbedPaneUI());
                tabs.putClientProperty(TermTabbedPaneUI.STYLE, style);
                tabs.putClientProperty("JTabbedPane.tabType", AppConfig.TAB_CARD.equals(style) ? "card" : "underlined");
                tabs.addTab("Gateway", new JPanel());
                tabs.addTab("stg-web", new JPanel());
                tabs.addTab("Monitor", new JPanel());
                tabs.setSelectedIndex(1);
                frame[0].getContentPane().removeAll();
                frame[0].getContentPane().add(tabs);
                frame[0].validate();
                var img = new BufferedImage(400, 40, BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                tabs.paint(g);
                g.dispose();
                images.put(style, img.getRGB(0, 0, 400, 40, null, 0, 400));
            }
            frame[0].dispose();
        });
        var distinct = images.values().stream().map(java.util.Arrays::hashCode).distinct().count();
        assertThat(distinct).isEqualTo(AppConfig.TAB_STYLES.size());
    }

    /** Tab aktif tetap tampak aktif selama kursor masih di atasnya (FlatLaf mendahulukan warna hover). */
    @Test
    void tabAktifTidakMemakaiWarnaHover() throws Exception {
        UIManager.put("TabbedPane.selectedBackground", new Color(0x112233));
        UIManager.put("TabbedPane.hoverColor", new Color(0x445566));
        try {
            SwingUtilities.invokeAndWait(() -> {
                var tabs = new JTabbedPane();
                var ui = new TermTabbedPaneUI();
                tabs.setUI(ui);
                tabs.addTab("a", new JPanel());
                tabs.addTab("b", new JPanel());
                tabs.setSelectedIndex(0);

                ui.setRolloverTab(0);
                assertThat(ui.getTabBackground(JTabbedPane.TOP, 0, true)).isEqualTo(new Color(0x112233));
                ui.setRolloverTab(1);
                assertThat(ui.getTabBackground(JTabbedPane.TOP, 1, false)).as("tab lain tetap hover")
                        .isEqualTo(new Color(0x445566));
            });
        } finally {
            UIManager.put("TabbedPane.selectedBackground", null);
            UIManager.put("TabbedPane.hoverColor", null);
        }
    }
}
