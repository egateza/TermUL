package dev.egateza.termul.app.ui;

import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Teks kolom input tepat di tengah secara visual. Swing menaruh baris teks (ascent + descent) di tengah kolom, tetapi
 * letak huruf di dalam baris itu berbeda antarfont: mis. Bahnschrift ber-ascent rapat dan descent besar, sehingga
 * huruf tampak ±2 px terlalu tinggi. Margin atas dan bawah digeser sama besar (tinggi kolom tetap) supaya pusat huruf
 * kapital jatuh di pusat kolom; dihitung ulang saat font atau LaF berganti. EDT.
 */
public final class TextCentering {

    private TextCentering() {
    }

    public static void install(JTextField field) {
        Runnable later = () -> SwingUtilities.invokeLater(() -> apply(field));
        field.addPropertyChangeListener("font", e -> later.run());
        field.addPropertyChangeListener("UI", e -> later.run());
        apply(field);
    }

    static void apply(JTextField field) {
        Insets base = UIManager.getInsets("TextField.margin");
        base = base == null ? new Insets(2, 6, 2, 6) : new Insets(base.top, base.left, base.bottom, base.right);
        field.setMargin(base);
        int shift = Math.clamp(shift(field), -base.top, base.bottom);
        if (shift != 0) {
            field.setMargin(new Insets(base.top + shift, base.left, base.bottom - shift, base.right));
        }
    }

    /** @return piksel yang perlu digeser ke bawah (negatif = ke atas) supaya huruf kapital tepat di tengah */
    static int shift(JTextField field) {
        Dimension size = field.getPreferredSize();
        int h = size.height;
        int baseline = field.getBaseline(Math.max(size.width, 50), h);
        if (baseline < 0) {
            return 0;
        }
        Insets in = field.getInsets();
        var frc = field.getFontMetrics(field.getFont()).getFontRenderContext();
        double cap = field.getFont().createGlyphVector(frc, "H").getVisualBounds().getHeight();
        double center = in.top + (h - in.top - in.bottom) / 2.0;
        return (int) Math.round(center + cap / 2 - baseline);
    }
}
