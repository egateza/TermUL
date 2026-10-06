package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatScrollBarUI;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.model.CharBuffer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerminalScrollBarTest {

    private LookAndFeel previous;

    @BeforeEach
    void flatLaf() {
        previous = UIManager.getLookAndFeel();
        FlatDarkLaf.setup();
    }

    @AfterEach
    void restore() throws Exception {
        UIManager.setLookAndFeel(previous);
    }

    @Test
    void memakaiGayaFlatLafDanTetapSetelahGantiTema() {
        var bar = new TerminalScrollBar(() -> null, () -> null, () -> null);
        assertThat(bar.getUI()).isInstanceOf(FlatScrollBarUI.class);

        FlatLightLaf.setup();
        SwingUtilities.updateComponentTreeUI(bar);

        assertThat(bar.getUI()).isInstanceOf(FlatScrollBarUI.class)
                .extracting(ui -> ui.getClass().getEnclosingClass()).isEqualTo(TerminalScrollBar.class);
    }

    @Test
    void penandaHasilCariDigambarDiTrack() {
        var finder = new SubstringFinder("err", true);
        var chars = new CharBuffer("err");
        for (int i = 0; i < chars.length(); i++) {
            finder.nextChar(i, 50, chars, i); // hasil di baris 50 dari 100
        }
        var bar = new TerminalScrollBar(finder::getResult, () -> Color.RED, () -> Color.BLUE);
        bar.setModel(new javax.swing.DefaultBoundedRangeModel(0, 10, 0, 100));
        bar.setSize(14, 400);
        bar.doLayout();

        var img = new BufferedImage(14, 400, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        bar.paint(g);
        g.dispose();

        boolean red = false;
        for (int y = 150; y < 250 && !red; y++) {
            red = new Color(img.getRGB(7, y), true).equals(Color.RED);
        }
        assertThat(red).as("penanda merah di sekitar tengah track").isTrue();
        assertThat(new Color(img.getRGB(1, 5), true)).as("latar track = latar terminal").isEqualTo(Color.BLUE);
    }
}
