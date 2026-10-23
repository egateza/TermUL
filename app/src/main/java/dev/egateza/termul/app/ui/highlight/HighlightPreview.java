package dev.egateza.termul.app.ui.highlight;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.AwtTransformers;
import dev.egateza.termul.app.terminal.TextHighlighter;
import dev.egateza.termul.app.ui.DerivedFonts;
import dev.egateza.termul.core.highlight.HighlightKeyword;
import dev.egateza.termul.core.highlight.HighlightMatcher;
import dev.egateza.termul.core.highlight.HighlightProfile;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.JComponent;

/**
 * Satu baris teks yang digambar seperti di terminal (font dan palet tema terminal), dengan keyword yang diwarnai. Dipakai
 * untuk kolom keyword di tabel dan baris Uji di editor keyword.
 */
final class HighlightPreview extends JComponent {

    /** Warna dan font terminal untuk preview. */
    record Look(ColorPalette palette, TerminalColor foreground, TerminalColor background, Font font) {

        Color awt(TerminalColor c, boolean fg) {
            if (c == null) {
                return awt(fg ? foreground : background, fg);
            }
            return AwtTransformers.toAwtColor(c.isIndexed()
                    ? (fg ? palette.getForeground(c) : palette.getBackground(c)) : c.toColor());
        }
    }

    private final Look look;
    private String text = "";
    private List<HighlightMatcher.Span> spans = List.of();
    private List<TextHighlighter.Patch> patches = List.of();

    HighlightPreview(Look look) {
        this.look = look;
        setFont(look.font());
        setOpaque(true);
    }

    /** Teks diwarnai dengan satu keyword (yang tidak aktif tetap diwarnai supaya warnanya terlihat). */
    void show(String text, HighlightKeyword keyword) {
        var shown = keyword.usable() || keyword.js() ? keyword : keyword.withEnabled(true);
        var matcher = HighlightMatcher.compile(HighlightProfile.create("preview", List.of(shown)));
        this.text = text == null ? "" : text;
        this.spans = matcher.match(this.text);
        this.patches = List.of(TextHighlighter.Patch.of(keyword));
        repaint();
    }

    /** Teks diwarnai penuh dengan gaya keyword (untuk kolom keyword di tabel). */
    void showWhole(String text, HighlightKeyword keyword) {
        this.text = text == null ? "" : text;
        this.spans = this.text.isEmpty() ? List.of() : List.of(new HighlightMatcher.Span(0, this.text.length(), 0));
        this.patches = List.of(TextHighlighter.Patch.of(keyword));
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(Math.max(120, fm.stringWidth(text) + 16), fm.getHeight() + 8);
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(look.awt(null, false));
            g2.fillRect(0, 0, getWidth(), getHeight());
            FontMetrics fm = g2.getFontMetrics(getFont());
            int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            int x = 8;
            int pos = 0;
            for (HighlightMatcher.Span s : spans) {
                x = run(g2, text.substring(pos, s.start()), TextStyle.EMPTY, x, baseline);
                x = run(g2, text.substring(s.start(), s.end()), patches.get(s.keyword()).apply(TextStyle.EMPTY), x,
                        baseline);
                pos = s.end();
            }
            run(g2, text.substring(pos), TextStyle.EMPTY, x, baseline);
        } finally {
            g2.dispose();
        }
    }

    private int run(Graphics2D g, String part, TextStyle style, int x, int baseline) {
        if (part.isEmpty()) {
            return x;
        }
        int fontStyle = (style.hasOption(TextStyle.Option.BOLD) ? Font.BOLD : 0)
                | (style.hasOption(TextStyle.Option.ITALIC) ? Font.ITALIC : 0);
        Font font = DerivedFonts.derive(getFont(), fontStyle);
        FontMetrics fm = g.getFontMetrics(font);
        int width = fm.stringWidth(part);
        Color fg = look.awt(style.getForeground(), true);
        Color bg = look.awt(style.getBackground(), false);
        if (style.hasOption(TextStyle.Option.INVERSE)) {
            Color t = fg;
            fg = bg;
            bg = t;
        }
        if (style.getBackground() != null || style.hasOption(TextStyle.Option.INVERSE)) {
            g.setColor(bg);
            g.fillRect(x, baseline - fm.getAscent(), width, fm.getHeight());
        }
        if (!style.hasOption(TextStyle.Option.HIDDEN)) {
            var old = g.getComposite();
            if (style.hasOption(TextStyle.Option.DIM)) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            }
            g.setColor(fg);
            g.setFont(font);
            g.drawString(part, x, baseline);
            if (style.hasOption(TextStyle.Option.UNDERLINED)) {
                g.drawLine(x, baseline + 2, x + width - 1, baseline + 2);
            }
            g.setComposite(old);
        }
        return x + width;
    }
}
