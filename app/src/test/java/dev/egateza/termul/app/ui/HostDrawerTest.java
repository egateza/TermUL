package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostDrawerTest {

    private static final int ANCHOR_X = 0;
    private static final int ANCHOR_Y = 30;
    private static final int ANCHOR_W = 1000;
    private static final int ANCHOR_H = 500;

    private final JLayeredPane layers = new JLayeredPane();
    private final JPanel anchor = new JPanel();
    private final JPanel content = new JPanel();
    private final AtomicInteger focusRestored = new AtomicInteger();
    private HostDrawer drawer;

    @BeforeEach
    void setUp() {
        layers.setSize(1000, 600);
        anchor.setBounds(ANCHOR_X, ANCHOR_Y, ANCHOR_W, ANCHOR_H);
        layers.add(anchor, JLayeredPane.DEFAULT_LAYER);
        drawer = new HostDrawer(layers, anchor, content, focusRestored::incrementAndGet);
    }

    @AfterEach
    void tearDown() {
        drawer.dispose();
    }

    @Test
    void inactiveByDefaultAndIgnoresOpen() {
        drawer.setOpen(true);

        assertThat(drawer.isOpen()).isFalse();
        assertThat(drawer.toggle().isVisible()).isFalse();
        assertThat(drawer.panel().isVisible()).isFalse();
        assertThat(content.getParent()).isNull();
    }

    @Test
    void activeClosedShowsOnlyCenteredToggleAtAnchorEdge() {
        drawer.setStyle(HostToggleStyle.EDGE_TAB, false);
        drawer.activate();

        assertThat(drawer.panel().isVisible()).isFalse();
        assertThat(drawer.toggle().isVisible()).isTrue();
        var b = drawer.toggle().getBounds();
        assertThat(b.x).isEqualTo(ANCHOR_X);
        assertThat(b.y).isEqualTo(ANCHOR_Y + (ANCHOR_H - HostToggleStyle.TAB_HEIGHT) / 2);
        assertThat(b.getSize()).isEqualTo(new Dimension(HostToggleStyle.TAB_WIDTH, HostToggleStyle.TAB_HEIGHT));
        assertThat(content.getParent()).isNotNull();
    }

    @Test
    void openCoversAnchorHeightAndMovesToggleToDrawerEdge() {
        drawer.setStyle(HostToggleStyle.EDGE_TAB, false);
        drawer.activate();
        drawer.setOpen(true);

        var p = drawer.panel().getBounds();
        assertThat(drawer.isOpen()).isTrue();
        assertThat(drawer.panel().isVisible()).isTrue();
        assertThat(p.x).isEqualTo(ANCHOR_X);
        assertThat(p.y).isEqualTo(ANCHOR_Y);
        assertThat(p.width).isEqualTo(HostDrawer.DEFAULT_WIDTH);
        assertThat(p.height).isEqualTo(ANCHOR_H);
        assertThat(drawer.toggle().getX()).isEqualTo(ANCHOR_X + HostDrawer.DEFAULT_WIDTH);
    }

    @Test
    void tabBarStyleShowsFloatingToggleOnlyWhileOpen() {
        drawer.setStyle(HostToggleStyle.TAB_BAR, true);
        drawer.activate();
        assertThat(drawer.toggle().isVisible()).isFalse();

        drawer.setOpen(true); // laci menutupi ikon di tab bar: tombol melayang dipakai untuk menutup
        assertThat(drawer.toggle().isVisible()).isTrue();

        drawer.setOpen(false);
        assertThat(drawer.toggle().isVisible()).isFalse();

        drawer.setStyle(HostToggleStyle.TAB_BAR, false); // belum ada tab: ikon tab bar tidak terlihat
        assertThat(drawer.toggle().isVisible()).isTrue();
    }

    @Test
    void hoverRevealIsInvisibleUntilHovered() {
        drawer.setStyle(HostToggleStyle.HOVER_REVEAL, false);
        drawer.activate();
        assertThat(drawer.toggle().alpha()).isZero();

        drawer.toggle().setHover(true);
        assertThat(drawer.toggle().alpha()).isEqualTo(1f);
    }

    @Test
    void toggleClickOpensAndCloses() {
        drawer.activate();

        drawer.toggle().onClick.run();
        assertThat(drawer.isOpen()).isTrue();
        drawer.toggle().onClick.run();
        assertThat(drawer.isOpen()).isFalse();
    }

    @Test
    void widthIsClampedAndFollowsAnchor() {
        drawer.activate();
        drawer.setOpen(true);

        drawer.setWidth(10);
        assertThat(drawer.width()).isEqualTo(HostDrawer.MIN_WIDTH);
        drawer.setWidth(5000);
        assertThat(drawer.width()).isEqualTo(ANCHOR_W - 160);

        anchor.setBounds(ANCHOR_X, ANCHOR_Y, 500, ANCHOR_H);
        drawer.layout();
        assertThat(drawer.panel().getWidth()).isEqualTo(500 - 160);
    }

    @Test
    void clickOutsideClosesButInsideDoesNot() {
        drawer.activate();
        drawer.setOpen(true);

        drawer.pressed(content);
        drawer.pressed(drawer.toggle());
        drawer.pressed(new JPopupMenu());
        assertThat(drawer.isOpen()).isTrue();

        var terminal = new JPanel();
        anchor.add(terminal);
        drawer.pressed(terminal);
        assertThat(drawer.isOpen()).isFalse();
    }

    @Test
    void clickInAnotherWindowDoesNotClose() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        var main = new JFrame();
        var dialog = new JFrame();
        try {
            main.getContentPane().add(layers);
            var button = new JButton();
            dialog.getContentPane().add(button);
            drawer.activate();
            drawer.setOpen(true);

            drawer.pressed(button);

            assertThat(drawer.isOpen()).isTrue();
        } finally {
            main.dispose();
            dialog.dispose();
        }
    }

    @Test
    void deactivateClosesAndReleasesContent() {
        drawer.activate();
        drawer.setOpen(true);

        drawer.deactivate();

        assertThat(drawer.isOpen()).isFalse();
        assertThat(drawer.toggle().isVisible()).isFalse();
        assertThat(drawer.panel().isVisible()).isFalse();
        assertThat(content.getParent()).isNull();
        assertThat(focusRestored).hasValue(0);
        drawer.activate();
        assertThat(content.getParent()).isNotNull();
    }

    @Test
    void opacityIsClampedAndHoverRestoresSolid() {
        drawer.setOpacity(0);
        assertThat(drawer.toggle().opacity).isEqualTo(10);
        drawer.setOpacity(500);
        assertThat(drawer.toggle().opacity).isEqualTo(100);

        drawer.setOpacity(40);
        assertThat(drawer.toggle().alpha()).isEqualTo(0.4f);
        drawer.toggle().setHover(true);
        assertThat(drawer.toggle().alpha()).isEqualTo(1f);
    }

    @Test
    void lowerOpacityPaintsMoreTransparentPixels() {
        drawer.activate();
        drawer.setOpacity(100);
        int solid = centerAlpha();
        drawer.setOpacity(20);
        int faded = centerAlpha();
        drawer.toggle().setHover(true);
        int hovered = centerAlpha();

        assertThat(solid).isGreaterThan(200);
        assertThat(faded).isLessThan(solid / 2);
        assertThat(hovered).isEqualTo(solid);
    }

    private int centerAlpha() {
        var t = drawer.toggle();
        var img = new BufferedImage(t.getWidth(), t.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        t.paint(g);
        g.dispose();
        // titik di badan kapsul, jauh dari ikon (ikon ada di tengah)
        return img.getRGB(t.getWidth() / 2, 4) >>> 24;
    }
}
