package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Wordmark {@code termul_} di title bar (prototipe "TermUL Desktop" → Ikon, opsi B; sama dengan TermUL Mobile): huruf
 * kecil JetBrains Mono Medium dengan garis bawah amber, 13 px, tracking .04em. Statis: font dibundel di jar dan tidak
 * mengikuti Pengaturan font, supaya sama di semua mesin. Bukan tombol: ikut area seret title bar.
 */
public final class Wordmark extends JComponent {

    static final String NAME = "termul";
    static final String CURSOR = "_";
    /** Warna prototipe di title bar gelap. */
    static final Color DARK_TEXT = new Color(0xE8EEF0);
    static final Color DARK_CURSOR = new Color(0xF0B34A);
    /** Amber yang tetap terbaca di title bar terang (kontras ≥ 3:1 terhadap putih). */
    static final Color LIGHT_CURSOR = new Color(0xA86A00);

    /** Ukuran tetap di title bar 30 px (prototipe desktop); tidak ikut Pengaturan font. */
    static final float SIZE = 13f;
    /** JetBrains Mono Medium (OFL, lisensi di folder yang sama), font wordmark TermUL Mobile. */
    static final String FONT = "/dev/egateza/termul/app/fonts/JetBrainsMono-Medium.ttf";
    private static final String[] FALLBACK = {"JetBrains Mono Medium", "JetBrains Mono", "Cascadia Mono", "Consolas",
            "Menlo", "SF Mono", "DejaVu Sans Mono"};
    private static Font base; // EDT; dimuat sekali

    public Wordmark() {
        // JComponent polos tidak memanggil updateUI() saat dibuat: tanpa ini font diwarisi dari menu bar (font UI,
        // mis. Segoe UI) sampai tema/font aplikasi diganti
        setFont(font());
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

    /**
     * Font wordmark: tetap, tidak mengikuti Pengaturan font. JetBrains Mono Medium 13 px (bobot 500 seperti
     * TermUL Mobile) dari jar, tracking .04em; kalau gagal dimuat, font mono terpasang.
     */
    static Font font() {
        return base().deriveFont(Map.of(TextAttribute.SIZE, SIZE, TextAttribute.TRACKING, 0.04f));
    }

    /** Font bawaan jar, dimuat sekali; fallback ke font terpasang (Medium dulu, supaya bobotnya tetap 500). */
    static Font base() {
        if (base == null) {
            base = bundled();
            if (base == null) {
                base = installedFallback();
            }
        }
        return base;
    }

    static Font bundled() {
        try (InputStream in = Wordmark.class.getResourceAsStream(FONT)) {
            return in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            return null;
        }
    }

    private static Font installedFallback() {
        var installed = Set.of(FontCatalog.allFamilies());
        for (String f : FALLBACK) {
            if (installed.contains(f)) {
                return new Font(f, Font.PLAIN, 1);
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, 1);
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
