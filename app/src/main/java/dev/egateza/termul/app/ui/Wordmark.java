package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Wordmark {@code termul_} di title bar (prototipe "TermUL Desktop" → Ikon, opsi B, sama dengan TermUL Mobile): font
 * mono huruf kecil dengan garis bawah amber. Font mono pertama yang terpasang (JetBrains Mono, Cascadia Mono, ...),
 * ukuran Font aplikasi + 1, tracking .04em. Bukan tombol: ikut area seret title bar.
 */
public final class Wordmark extends JComponent {

    static final String NAME = "termul";
    static final String CURSOR = "_";
    /** Warna prototipe di title bar gelap. */
    static final Color DARK_TEXT = new Color(0xE8EEF0);
    static final Color DARK_CURSOR = new Color(0xF0B34A);
    /** Amber yang tetap terbaca di title bar terang (kontras ≥ 3:1 terhadap putih). */
    static final Color LIGHT_CURSOR = new Color(0xA86A00);

    private static final String[] MONO = {"JetBrains Mono", "Cascadia Mono", "Consolas", "Menlo", "SF Mono",
            "DejaVu Sans Mono"};
    private static String monoFamily; // EDT; dicari sekali

    public Wordmark() {
        setOpaque(false);
        putClientProperty("JComponent.titleBarCaption", true); // seret window lewat wordmark tetap bisa
        getAccessibleContext().setAccessibleName("TermUL");
        setToolTipText(null);
    }

    /** JComponent polos tidak punya konteks aksesibilitas: dibaca sebagai label "TermUL". */
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

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(font());
        revalidate();
    }

    /** Font mono terpasang, ukuran Font aplikasi + 1 (13 px pada ukuran bawaan 12), tracking .04em. */
    static Font font() {
        Font base = UIManager.getFont("Label.font");
        float size = (base != null ? base.getSize2D() : 12f) + 1;
        return new Font(Map.of(TextAttribute.FAMILY, family(), TextAttribute.SIZE, size,
                TextAttribute.TRACKING, 0.04f));
    }

    private static String family() {
        if (monoFamily == null) {
            monoFamily = Font.MONOSPACED;
            var installed = Set.of(FontCatalog.allFamilies());
            for (String f : MONO) {
                if (installed.contains(f)) {
                    monoFamily = f;
                    break;
                }
            }
        }
        return monoFamily;
    }

    /** Warna huruf dan garis bawah untuk tema aktif: persis prototipe di tema gelap, warna teks tema di tema terang. */
    static Color[] colors(boolean dark, Color themeText) {
        return dark ? new Color[] {DARK_TEXT, DARK_CURSOR}
                : new Color[] {themeText != null ? themeText : Color.DARK_GRAY, LIGHT_CURSOR};
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(NAME + CURSOR) + 2, fm.getHeight());
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        var c = colors(FlatLaf.isLafDark(), UIManager.getColor("TitlePane.foreground"));
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(c[0]);
        g2.drawString(NAME, 0, y);
        g2.setColor(c[1]);
        g2.drawString(CURSOR, fm.stringWidth(NAME), y);
        g2.dispose();
    }
}
