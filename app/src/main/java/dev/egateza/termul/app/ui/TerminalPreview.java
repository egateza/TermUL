package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.terminal.BackgroundImages;
import dev.egateza.termul.core.theme.CustomTheme.TerminalPalette;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;

/** Contoh output terminal bergambar sesuai palet yang sedang diedit; bukan terminal sungguhan. */
final class TerminalPreview extends JPanel {

    private static final int DEFAULT = -1;
    private static final int SELECTION = -2;

    private record Seg(String text, int color) {
    }

    private static final List<List<Seg>> LINES = List.of(
            List.of(new Seg("user@server", 10), new Seg(":", DEFAULT), new Seg("~", 12),
                    new Seg("$ ls -la --color", DEFAULT)),
            List.of(new Seg("app/", 4), new Seg("  ", DEFAULT), new Seg("deploy.sh", 2), new Seg("  ", DEFAULT),
                    new Seg("error.log", 1), new Seg("  ", DEFAULT), new Seg("notes.txt", 3)),
            List.of(new Seg("build.tar", 5), new Seg("  ", DEFAULT), new Seg("link", 6)),
            List.of(new Seg("[sudo] password for user: ", DEFAULT)),
            List.of(new Seg("warning: ", 3), new Seg("disk usage 91%", DEFAULT)),
            List.of(new Seg("teks yang sedang diseleksi", SELECTION)));

    private TerminalPalette palette;
    private BufferedImage image; // gambar latar, null = tanpa
    private int visibility;
    private BufferedImage scaled; // gambar yang sudah disesuaikan dengan ukuran panel
    private BufferedImage scaledSource;

    TerminalPreview(TerminalPalette palette, Font font) {
        this.palette = palette;
        setFont(font);
        setPreferredSize(new Dimension(420, 210));
        setMinimumSize(new Dimension(420, 210)); // ukuran tetap: dialog tidak ikut bergeser saat font berubah
    }

    void setPalette(TerminalPalette palette) {
        this.palette = palette;
        repaint();
    }

    /** @param image gambar latar yang sudah dimuat, null = tanpa gambar; @param visibility persen 0..100 */
    void setBackdrop(BufferedImage image, int visibility) {
        this.image = image;
        this.visibility = visibility;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.decode(palette.background()));
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (image != null && getWidth() > 0 && getHeight() > 0) {
            if (scaled == null || scaledSource != image || scaled.getWidth() != getWidth()
                    || scaled.getHeight() != getHeight()) {
                scaled = BackgroundImages.cover(image, getWidth(), getHeight());
                scaledSource = image;
            }
            g2.setComposite(AlphaComposite.SrcOver.derive(visibility / 100f));
            g2.drawImage(scaled, 0, 0, null);
            g2.setComposite(AlphaComposite.SrcOver);
        }
        g2.setFont(getFont());
        var fm = g2.getFontMetrics();
        int lineHeight = fm.getHeight() + 2;
        int y = 8 + fm.getAscent();
        for (var line : LINES) {
            int x = 10;
            for (var seg : line) {
                int w = fm.stringWidth(seg.text());
                if (seg.color() == SELECTION) {
                    g2.setColor(Color.decode(palette.selection()));
                    g2.fillRect(x, y - fm.getAscent(), w, fm.getHeight());
                }
                g2.setColor(Color.decode(seg.color() >= 0 ? palette.ansi().get(seg.color()) : palette.foreground()));
                g2.drawString(seg.text(), x, y);
                x += w;
            }
            y += lineHeight;
        }
        // 16 warna ANSI: baris atas normal (0-7), baris bawah terang (8-15)
        int cell = Math.max(16, fm.getHeight());
        int top = y - fm.getAscent() + 6;
        for (int i = 0; i < 16; i++) {
            int x = 10 + (i % 8) * (cell + 4);
            int row = top + (i / 8) * (cell + 4);
            g2.setColor(Color.decode(palette.ansi().get(i)));
            g2.fillRect(x, row, cell, cell);
        }
        g2.dispose();
    }
}
