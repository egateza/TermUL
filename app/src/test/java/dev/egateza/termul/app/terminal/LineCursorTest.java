package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.terminal.CursorShape;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LineCursorTest {

    @Test
    void garisTegakSetinggiHurufTerlihat() {
        // sel 9x20, baseline 16 px dari atas; huruf: puncak 12 px di atas baseline, ekor 3 px di bawahnya
        var glyphs = new LineCursor.Glyphs(-12, 3);
        var bar = LineCursor.bounds(CursorShape.BLINK_VERTICAL_BAR, 100, 40, 9, 20, 16, glyphs);
        assertThat(bar).isEqualTo(new Rectangle(100, 44, LineCursor.THICKNESS, 15));
        assertThat(bar.height).as("dulu setinggi sel penuh").isLessThan(20);
    }

    @Test
    void garisBawahTipisTepatDiBawahEkorHuruf() {
        var under = LineCursor.bounds(CursorShape.STEADY_UNDERLINE, 100, 40, 9, 20, 16, new LineCursor.Glyphs(-12, 3));
        assertThat(under).isEqualTo(new Rectangle(100, 58, 9, LineCursor.THICKNESS));
        assertThat(under.y + under.height).as("dulu di luar sel, menimpa baris berikutnya").isLessThanOrEqualTo(60);
    }

    @Test
    void selaluDiDalamSelWalauFontLebihBesarDariSel() {
        var bar = LineCursor.bounds(CursorShape.STEADY_VERTICAL_BAR, 0, 0, 9, 20, 16, new LineCursor.Glyphs(-30, 10));
        assertThat(bar.y).isGreaterThanOrEqualTo(0);
        assertThat(bar.y + bar.height).isLessThanOrEqualTo(20);
    }

    @Test
    void baselineSamaDenganRumusJediTerm() {
        assertThat(LineCursor.baseline(20, 20, 4)).isEqualTo(16); // tanpa spasi baris tambahan
        assertThat(LineCursor.baseline(24, 20, 4)).isEqualTo(18); // spasi 4 px: 2 di atas, 2 di bawah
    }

    @Test
    void blokTetapDigambarJediTerm() {
        assertThat(LineCursor.bounds(CursorShape.BLINK_BLOCK, 0, 0, 9, 20, 16, new LineCursor.Glyphs(-12, 3))).isNull();
        assertThat(LineCursor.isLine(CursorShape.STEADY_BLOCK)).isFalse();
        assertThat(LineCursor.isLine(null)).isFalse();
    }

    @Test
    void kursorGarisMengikutiPosisiKursorTerminal() throws Exception {
        var result = new AtomicReference<Rectangle[]>();
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            widget.setSize(800, 300);
            widget.doLayout();
            widget.getTerminalPanel().setSize(780, 300);
            var terminal = widget.getTerminal();
            terminal.writeCharacters("Last login: today");
            terminal.carriageReturn();
            terminal.newLine();
            terminal.writeCharacters("ega@gapura08:~$ ");
            var panel = widget.getTerminalPanel();
            var cell = widget.charSize();

            panel.setDefaultCursorShape(CursorShape.BLINK_VERTICAL_BAR);
            var bar = widget.lineCursorBounds();
            snapshot(widget, "cursor-bar.png");
            panel.setDefaultCursorShape(CursorShape.STEADY_UNDERLINE);
            var under = widget.lineCursorBounds();
            snapshot(widget, "cursor-underline.png");
            panel.setCursorVisible(false); // program menyembunyikan kursor (mis. htop)
            var hidden = widget.lineCursorBounds();
            panel.setCursorVisible(true);
            panel.setDefaultCursorShape(CursorShape.BLINK_BLOCK);
            var block = widget.lineCursorBounds();
            result.set(new Rectangle[] {bar, under, hidden, block, new Rectangle(cell)});
        });
        var r = result.get();
        var cell = r[4];
        assertThat(r[0]).isNotNull();
        assertThat(r[0].x).as("setelah 16 karakter prompt").isEqualTo(16 * cell.width + insetX(r[0], cell));
        assertThat(r[0].y).isBetween(cell.height, 2 * cell.height - 1); // baris kedua
        assertThat(r[0].height).isLessThan(cell.height);
        assertThat(r[1].width).isEqualTo(cell.width);
        assertThat(r[2]).as("disembunyikan program").isNull();
        assertThat(r[3]).as("blok digambar JediTerm").isNull();
    }

    private static int insetX(Rectangle cursor, Rectangle cell) {
        return Math.floorMod(cursor.x, cell.width); // inset kiri panel (bukan kelipatan lebar sel)
    }

    /** Render panel ke PNG di folder sementara sistem (untuk dicek mata saat mengubah geometri). */
    private static void snapshot(ZoomableTermWidget widget, String name) {
        var dir = System.getenv("TERMUL_SNAPSHOT_DIR");
        if (dir == null) {
            return;
        }
        var panel = widget.getTerminalPanel();
        var image = new BufferedImage(Math.max(1, panel.getWidth()), Math.max(1, panel.getHeight()),
                BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        try {
            javax.imageio.ImageIO.write(image, "png", Path.of(dir, name).toFile());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
