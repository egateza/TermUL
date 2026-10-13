package dev.egateza.termul.app.ui.fab;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class FloatingOverlayTest {

    private static JPanel box(int w, int h) {
        var p = new JPanel();
        p.setPreferredSize(new Dimension(w, h));
        return p;
    }

    @Test
    void kontenMemenuhiAreaDanTombolDiPojokKananBawah() {
        var overlay = new FloatingOverlay();
        var base = new JLabel("terminal");
        var fab = box(52, 52);
        overlay.setBase(base);
        overlay.addFloating(fab, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        overlay.setMargins(28, 16);
        overlay.setSize(800, 600);

        overlay.doLayout();

        assertThat(base.getBounds()).isEqualTo(new Rectangle(0, 0, 800, 600));
        assertThat(fab.getBounds()).isEqualTo(new Rectangle(800 - 28 - 52, 600 - 16 - 52, 52, 52));
    }

    @Test
    void tengahBawahDanTombolTersembunyiTidakMakanTempat() {
        var overlay = new FloatingOverlay();
        var first = box(52, 52);
        var second = box(52, 40);
        var mini = box(120, 38);
        overlay.addFloating(first, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        overlay.addFloating(second, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        overlay.addFloating(mini, FloatingOverlay.Anchor.BOTTOM_CENTER);
        overlay.setSize(400, 300);

        overlay.doLayout();
        assertThat(second.getY()).as("ditumpuk di atas tombol pertama")
                .isEqualTo(300 - 16 - 52 - FloatingOverlay.GAP - 40);
        assertThat(mini.getBounds()).isEqualTo(new Rectangle((400 - 120) / 2, 300 - 16 - 38, 120, 38));

        first.setVisible(false);
        overlay.doLayout();
        assertThat(second.getY()).isEqualTo(300 - 16 - 40);
    }

    @Test
    void gantiKontenTidakMenghapusTombol() {
        var overlay = new FloatingOverlay();
        var fab = box(52, 52);
        overlay.addFloating(fab, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        overlay.setBase(new JLabel("loading"));
        var terminal = new JLabel("terminal");

        overlay.setBase(terminal);

        assertThat(overlay.getComponents()).containsExactly(fab, terminal);
    }
}
