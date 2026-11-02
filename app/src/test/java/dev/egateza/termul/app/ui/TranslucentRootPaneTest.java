package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TranslucentRootPaneTest {

    private static int alphaAt(TranslucentRootPane root, int x, int y) throws Exception {
        var img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            var g = img.createGraphics();
            root.paint(g);
            g.dispose();
        });
        return img.getRGB(x, y) >>> 24;
    }

    private static TranslucentRootPane rootWithSolidContent() throws Exception {
        var root = new TranslucentRootPane[1];
        SwingUtilities.invokeAndWait(() -> {
            var r = new TranslucentRootPane();
            var content = new JPanel();
            content.setBackground(Color.RED);
            content.setOpaque(true);
            r.setContentPane(content);
            r.setSize(100, 80);
            r.doLayout();
            r.getLayeredPane().setSize(100, 80);
            content.setBounds(0, 0, 100, 80);
            root[0] = r;
        });
        return root[0];
    }

    /** Bagian selain terminal dipudarkan dengan alpha slider "Jendela", termasuk isinya (bukan menumpuk). */
    @Test
    void bagianSelainTerminalDipudarkan() throws Exception {
        var root = rootWithSolidContent();
        SwingUtilities.invokeAndWait(() -> root.setChromeOpacity(50));

        assertThat(alphaAt(root, 50, 40)).isBetween(125, 130);
        assertThat(alphaAt(root, 50, 40)).isBetween(125, 130); // paint ulang tidak menumpuk alpha
    }

    @Test
    void opasitas100DigambarBiasa() throws Exception {
        var root = rootWithSolidContent();
        SwingUtilities.invokeAndWait(() -> root.setChromeOpacity(100));

        assertThat(alphaAt(root, 50, 40)).isEqualTo(255);
    }

    /**
     * Skala 125%: menggambar ulang sebagian window tidak boleh meninggalkan piksel transparan di tepi area itu (tampak
     * sebagai garis tipis, mis. di bawah jam yang digambar ulang tiap detik).
     */
    @Test
    void skalaPecahanTanpaGarisTransparanDiTepiAreaYangDigambarUlang() throws Exception {
        var root = rootWithSolidContent();
        SwingUtilities.invokeAndWait(() -> root.setChromeOpacity(50));
        var img = new BufferedImage(125, 100, BufferedImage.TYPE_INT_ARGB);
        var dirty = new Rectangle(10, 10, 30, 17);
        SwingUtilities.invokeAndWait(() -> {
            var full = img.createGraphics();
            full.scale(1.25, 1.25);
            root.paint(full);
            full.dispose();
            var part = img.createGraphics();
            part.scale(1.25, 1.25);
            part.setClip(dirty);
            root.paint(part);
            part.dispose();
        });

        var d = TranslucentRootPane.deviceBounds(dirty, 1.25, 1.25);
        for (int x = d.x; x < d.x + d.width; x++) {
            assertThat(img.getRGB(x, d.y) >>> 24).as("tepi atas x=%d", x).isBetween(120, 135);
            assertThat(img.getRGB(x, d.y + d.height - 1) >>> 24).as("tepi bawah x=%d", x).isBetween(120, 135);
        }
        for (int y = d.y; y < d.y + d.height; y++) {
            assertThat(img.getRGB(d.x, y) >>> 24).as("tepi kiri y=%d", y).isBetween(120, 135);
            assertThat(img.getRGB(d.x + d.width - 1, y) >>> 24).as("tepi kanan y=%d", y).isBetween(120, 135);
        }
    }

    @Test
    void pikselPerangkatDibulatkanKeluar() {
        assertThat(TranslucentRootPane.deviceBounds(new Rectangle(10, 10, 30, 17), 1.25, 1.25))
                .isEqualTo(new Rectangle(12, 12, 38, 22)); // 12.5..50 x 12.5..33.75
        assertThat(TranslucentRootPane.deviceBounds(new Rectangle(10, 10, 30, 17), 1, 1))
                .isEqualTo(new Rectangle(10, 10, 30, 17));
    }

    /**
     * "Teks tetap solid": latar dipudarkan seperti biasa, tetapi piksel yang tertutup teks tetap pekat. Garis bukan
     * teks (mis. border) tetap ikut pudar.
     */
    @Test
    void teksTetapSolidSaatLatarDipudarkan() throws Exception {
        var root = new TranslucentRootPane[1];
        SwingUtilities.invokeAndWait(() -> {
            var r = new TranslucentRootPane();
            var content = new JPanel(null) {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 70, 100, 10); // bukan teks
                    g.setFont(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.BOLD, 40));
                    g.drawString("█", 10, 50); // blok penuh: piksel teks yang pasti tertutup
                }
            };
            r.setContentPane(content);
            r.setSize(100, 80);
            r.doLayout();
            r.getLayeredPane().setSize(100, 80);
            content.setBounds(0, 0, 100, 80);
            r.setChromeOpacity(50);
            r.setSolidText(true);
            root[0] = r;
        });
        var img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            var g = img.createGraphics();
            root[0].paint(g);
            g.dispose();
        });

        assertThat(img.getRGB(90, 5) >>> 24).as("latar").isBetween(125, 130);
        assertThat(img.getRGB(50, 75) >>> 24).as("garis bukan teks").isBetween(125, 130);
        int textX = -1;
        for (int x = 10; x < 40 && textX < 0; x++) {
            if ((img.getRGB(x, 35) & 0xFFFFFF) == 0xFFFFFF) {
                textX = x;
            }
        }
        assertThat(textX).as("piksel teks putih ditemukan").isPositive();
        assertThat(img.getRGB(textX, 35) >>> 24).as("teks").isEqualTo(255);
    }
}
