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

    /**
     * Window beralpha per piksel: teks tetap ClearType (LCD, piksel berwarna di tepi huruf). Java hanya menggambar
     * teks LCD ke permukaan pekat; tanpa buffer pekat, teks jatuh ke antialiasing abu-abu dan tampak berpiksel.
     */
    @Test
    void teksTetapClearTypeSaatWindowTembus() throws Exception {
        var root = new TranslucentRootPane[1];
        SwingUtilities.invokeAndWait(() -> {
            var r = new TranslucentRootPane();
            var content = new JPanel(null) {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    var g2 = (java.awt.Graphics2D) g;
                    g2.setColor(new Color(60, 63, 65));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                    g2.setColor(new Color(220, 220, 220));
                    g2.setFont(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 14));
                    g2.drawString("Terminal lokal", 5, 25);
                }
            };
            r.setContentPane(content);
            r.setSize(140, 40);
            r.doLayout();
            r.getLayeredPane().setSize(140, 40);
            content.setBounds(0, 0, 140, 40);
            r.setChromeOpacity(85);
            r.setLayered(true);
            root[0] = r;
        });
        var img = new BufferedImage(140, 40, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            var g = img.createGraphics();
            root[0].paint(g);
            g.dispose();
        });

        int colored = 0;
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 140; x++) {
                int c = img.getRGB(x, y);
                int r = c >> 16 & 0xFF, gr = c >> 8 & 0xFF, b = c & 0xFF;
                if (Math.max(Math.abs(r - gr), Math.abs(gr - b)) > 25) {
                    colored++;
                }
            }
        }
        assertThat(colored).as("piksel subpiksel (ClearType) di tepi huruf").isGreaterThan(20);
        assertThat(img.getRGB(135, 35) >>> 24).as("latar tetap dipudarkan").isBetween(214, 219);
    }

    /**
     * Blur Windows: lapisan warna latar setengah tembus di bawah isi window, supaya bagian tembus tidak tampak
     * abu-abu. Alpha akhir = isi di atas lapisan: 0,3 + 0,45 × 0,7 = 0,615; tanpa lapisan tetap 0,3.
     */
    @Test
    void lapisanGelapDiBawahIsiSaatBlurMenyala() throws Exception {
        var root = rootWithSolidContent();
        SwingUtilities.invokeAndWait(() -> {
            root.setChromeOpacity(30);
            root.setLayered(true);
        });
        assertThat(alphaAt(root, 50, 40)).isBetween(74, 79);

        SwingUtilities.invokeAndWait(() -> root.setBackdropTint(true));
        assertThat(alphaAt(root, 50, 40)).isBetween(154, 160);

        SwingUtilities.invokeAndWait(() -> root.setBackdropTint(false));
        assertThat(alphaAt(root, 50, 40)).isBetween(74, 79);
    }

    /**
     * Teks solid: tepi huruf (cakupan sebagian) tetap berintensitas penuh, hanya bagian latarnya yang dipudarkan. Teks
     * putih 50% di atas latar (60,60,60), jendela 30% (alpha 77): bagian teks 127,5 + latar 30 × 0,3 ≈ 137, alpha
     * 77 + 178 × 0,5 ≈ 166. Cara lama (p × max(base, c)) hanya ≈ 79 per kanal.
     */
    @Test
    void tepiHurufTidakMenipisSaatLatarDipudarkan() {
        int bg = 0xFF3C3C3C;
        int p = 0xFF000000 | 158 << 16 | 158 << 8 | 158; // putih 50% di atas latar 60
        int out = TranslucentRootPane.fadeKeepingText(p, 128, bg & 0xFFFFFF, 77);
        assertThat(out >>> 24).isBetween(164, 168);
        assertThat(out >> 16 & 0xFF).isBetween(133, 141);

        int none = TranslucentRootPane.fadeKeepingText(bg, 0, bg & 0xFFFFFF, 77); // latar tanpa teks: pudar ke 30%
        assertThat(none >>> 24).isEqualTo(77);
        assertThat(none >> 16 & 0xFF).isBetween(17, 19);

        int full = TranslucentRootPane.fadeKeepingText(0xFFF0F0F0, 255, bg & 0xFFFFFF, 77); // tertutup penuh: utuh
        assertThat(full).isEqualTo(0xFFF0F0F0);

        // ClearType: selisih kanal merah dan biru di tepi huruf (90) tetap terasa setelah dipudarkan; cara lama ≈ 45
        int clearType = 0xFF000000 | 200 << 16 | 158 << 8 | 110;
        int ct = TranslucentRootPane.fadeKeepingText(clearType, 128, bg & 0xFFFFFF, 77);
        assertThat((ct >> 16 & 0xFF) - (ct & 0xFF)).isGreaterThan(70);
    }

    /** Tema gelap: warna lapisan digelapkan supaya tidak abu-abu; tema terang memakai warna latar apa adanya. */
    @Test
    void warnaLapisanLebihGelapDiTemaGelap() {
        var dark = TranslucentRootPane.tintColor(new java.awt.Color(60, 63, 65), true);
        assertThat(dark).isEqualTo(new java.awt.Color(30, 32, 33, 115));
        var light = TranslucentRootPane.tintColor(new java.awt.Color(240, 240, 240), false);
        assertThat(light).isEqualTo(new java.awt.Color(240, 240, 240, 115));
        assertThat(TranslucentRootPane.tintColor(null, true)).isEqualTo(new java.awt.Color(0, 0, 0, 115));
    }

    /**
     * Saat bergetar, window transparan hanya menempel tangkapan isinya yang digeser (menggambar ulang seluruh window di
     * tiap langkah terlalu lambat): isinya tidak digambar ulang, dan celah di sisi yang ditinggalkan tembus.
     */
    @Test
    void getarMenggeserTangkapanTanpaMenggambarUlangIsi() throws Exception {
        var root = rootWithSolidContent();
        SwingUtilities.invokeAndWait(() -> root.setChromeOpacity(50));
        var snapshot = new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB_PRE);
        var sg = snapshot.createGraphics();
        sg.setColor(Color.BLUE);
        sg.fillRect(0, 0, 100, 80);
        sg.dispose();
        assertThat(root.buffered()).isTrue();

        SwingUtilities.invokeAndWait(() -> root.setShake(snapshot, 10));
        var img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            var g = img.createGraphics();
            root.paint(g);
            g.dispose();
        });
        assertThat(img.getRGB(5, 40) >>> 24).as("celah kiri").isZero();
        assertThat(img.getRGB(50, 40)).as("tangkapan, bukan isi merah").isEqualTo(Color.BLUE.getRGB());

        SwingUtilities.invokeAndWait(() -> root.setShake(null, 0));
        assertThat(alphaAt(root, 5, 40)).as("selesai: isi digambar lagi").isBetween(125, 130);
    }
}
