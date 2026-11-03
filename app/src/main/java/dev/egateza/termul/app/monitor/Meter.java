package dev.egateza.termul.app.monitor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Meter CPU / heap di title bar (prototipe "Meter CPU/Heap"): batang 78×13 bergaris tepi, track bergaris vertikal,
 * isi selebar persennya (minimal 2% kalau tidak nol). Angka digambar dua kali: warna meter di atas track, dan gelap di
 * dalam isi (dipotong selebar isi), jadi selalu terbaca di level berapa pun. Hijau &lt; 70%, amber 70%, merah 90%.
 */
final class Meter extends JComponent {

    static final int WIDTH = 78;
    static final int HEIGHT = 13;
    private static final int STRIPE = 2; // track: 2 px garis, 2 px kosong
    /**
     * Warna isi, garis tepi, garis track, dan teks di dalam isi per level. Tema gelap: persis prototipe ({@code --m},
     * {@code --mb}, {@code --mt}). Tema terang: hijau/amber/merah yang sama tetapi lebih tua, karena warna prototipe di
     * atas title bar terang hanya berkontras 1,5–2,7:1 dan angkanya sulit dibaca; versi terang ≥ 4,5:1 terhadap
     * latar putih sampai #E8E8E8, track-nya tint muda, dan teks di dalam isi putih.
     */
    record Palette(Color fill, Color border, Color track, Color textInFill) {
        private static final Color DARK_TEXT_IN_FILL = new Color(0x0F1117);

        /** Palet tema gelap (prototipe). */
        static Palette of(Level level) {
            return of(level, true);
        }

        static Palette of(Level level, boolean dark) {
            if (dark) {
                return switch (level) {
                    case NORMAL -> new Palette(new Color(0x3DD68C), new Color(0x2E7D32), new Color(0x14361B),
                            DARK_TEXT_IN_FILL);
                    case WARN -> new Palette(new Color(0xE0AF68), new Color(0x8A6A2E), new Color(0x3A2E14),
                            DARK_TEXT_IN_FILL);
                    case CRITICAL -> new Palette(new Color(0xF7768E), new Color(0x9A3B4C), new Color(0x3D1820),
                            DARK_TEXT_IN_FILL);
                };
            }
            return switch (level) {
                case NORMAL -> new Palette(new Color(0x146C3A), new Color(0x146C3A), new Color(0xCDE8D6), Color.WHITE);
                case WARN -> new Palette(new Color(0x8C5A00), new Color(0x8C5A00), new Color(0xF0DFC0), Color.WHITE);
                case CRITICAL -> new Palette(new Color(0xB02A43), new Color(0xB02A43), new Color(0xF5CED6),
                        Color.WHITE);
            };
        }

        /** Palet untuk tema yang sedang aktif. */
        static Palette current(Level level) {
            return of(level, com.formdev.flatlaf.FlatLaf.isLafDark());
        }
    }

    private final String name;
    private double ratio = Double.NaN; // EDT
    private String text = "–";         // EDT
    private Level level = Level.NORMAL; // EDT

    /** @param name nama untuk pembaca layar ("CPU", "Heap") */
    Meter(String name) {
        this.name = name;
        setOpaque(false);
        getAccessibleContext().setAccessibleName(name);
    }

    /** JComponent polos tidak punya konteks aksesibilitas: dibaca sebagai label "CPU 4%". */
    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.LABEL;
                }
            };
        }
        return accessibleContext;
    }

    /** @param ratio 0..1, NaN = belum diketahui (isi kosong) */
    void update(double ratio, String text, Level level) {
        this.ratio = ratio;
        this.text = text;
        this.level = level;
        getAccessibleContext().setAccessibleName(name + " " + text);
        repaint();
    }

    /** Lebar isi dalam piksel untuk area dalam selebar {@code inner}: 0 untuk 0% / tidak diketahui, minimal 2%. */
    static int fillWidth(double ratio, int inner) {
        if (Double.isNaN(ratio) || ratio <= 0) {
            return 0;
        }
        long percent = Math.round(Math.min(1, ratio) * 100);
        if (percent == 0) {
            return 0;
        }
        return (int) Math.round(inner * Math.max(2, percent) / 100.0);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();
        var p = Palette.current(level);
        int inner = w - 2;
        g2.setColor(p.track());
        for (int x = 1; x < w - 1; x += 2 * STRIPE) {
            g2.fillRect(x, 1, Math.min(STRIPE, w - 1 - x), h - 2);
        }
        int fill = fillWidth(ratio, inner);
        g2.setColor(p.fill());
        g2.fillRect(1, 1, fill, h - 2);
        g2.setColor(p.border());
        g2.drawRect(0, 0, w - 1, h - 1);

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font base = font();
        g2.setFont(base);
        int y = baseline(base, g2.getFontRenderContext(), h);
        g2.setColor(p.fill());
        g2.drawString(text, 3, y);
        if (fill > 0) {
            var inside = (Graphics2D) g2.create();
            inside.clipRect(1, 1, fill, h - 2);
            inside.setFont(base.deriveFont(Font.BOLD));
            inside.setColor(p.textInFill());
            inside.drawString(text, 3, y);
            inside.dispose();
        }
        g2.dispose();
    }

    /**
     * Baseline yang menaruh angka tepat di tengah tinggi {@code h}: dari batas visual {@code 0123456789%}, bukan dari
     * metrik baris font. Metrik baris (ascent, descent, leading) berbeda-beda antarfont; mis. Bahnschrift punya leading
     * 2 px dan ascent rapat, sehingga angka naik 2 px dan menempel di tepi atas. String acuan yang sama untuk semua nilai
     * supaya angka tidak bergeser saat nilainya berubah.
     */
    static int baseline(Font font, java.awt.font.FontRenderContext frc, int h) {
        var bounds = font.createGlyphVector(frc, "0123456789%").getVisualBounds();
        return (int) Math.round(h / 2.0 - (bounds.getMinY() + bounds.getMaxY()) / 2);
    }

    /** Font aplikasi 2 px lebih kecil (10 px pada ukuran bawaan 12), angka sama lebar. */
    private static Font font() {
        Font f = UIManager.getFont("Label.font");
        if (f == null) {
            f = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return f.deriveFont(Font.PLAIN, Math.max(8f, f.getSize2D() - 2));
    }
}
