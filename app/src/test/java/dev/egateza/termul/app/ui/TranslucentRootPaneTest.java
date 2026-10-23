package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
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
}
