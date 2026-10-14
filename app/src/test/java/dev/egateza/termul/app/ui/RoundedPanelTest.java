package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class RoundedPanelTest {

    @Test
    void squareByDefaultWithoutGapsOrPaintingOrigin() {
        var panel = new RoundedPanel();
        panel.setGaps(new Insets(6, 6, 6, 6));

        assertThat(panel.isRounded()).isFalse();
        assertThat(panel.getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(panel.isPaintingOrigin()).isFalse();
    }

    @Test
    void roundedUsesGapsAndBecomesPaintingOrigin() {
        var panel = new RoundedPanel();
        panel.setGaps(new Insets(6, 3, 6, 6));
        panel.setRounded(true);

        assertThat(panel.getInsets()).isEqualTo(new Insets(6, 3, 6, 6));
        assertThat(panel.isPaintingOrigin()).isTrue();

        panel.setRounded(false);
        assertThat(panel.getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
    }

    @Test
    void cornerMaskCoversOnlyTheCorners() {
        var mask = new RoundedPanel().cornerMask(200, 100);

        assertThat(mask.contains(0.5, 0.5)).isTrue();
        assertThat(mask.contains(199.5, 99.5)).isTrue();
        assertThat(mask.contains(100, 50)).isFalse(); // tengah
        assertThat(mask.contains(100, 0.5)).isFalse(); // tepi atas di luar sudut
        assertThat(mask.contains(RoundedPanel.ARC, RoundedPanel.ARC)).isFalse();
    }

    @Test
    void cornersArePaintedOverChildrenWithPanelBackground() {
        var panel = new RoundedPanel();
        panel.setBackground(Color.BLUE);
        var child = new JPanel();
        child.setBackground(Color.RED);
        panel.add(child, BorderLayout.CENTER);
        panel.setGaps(new Insets(4, 4, 4, 4));
        panel.setRounded(true);
        panel.setSize(120, 80);
        panel.doLayout();

        var image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        panel.paint(g);
        g.dispose();

        assertThat(new Color(image.getRGB(0, 0))).isEqualTo(Color.BLUE); // celah
        assertThat(new Color(image.getRGB(4, 4))).isEqualTo(Color.BLUE); // sudut isi ditutup
        assertThat(new Color(image.getRGB(60, 40))).isEqualTo(Color.RED); // isi tetap terlihat
    }

    @Test
    void viewportTanpaBlitSelamaMembulatSupayaTepiTidakTersalinSaatScroll() {
        var panel = new RoundedPanel();
        var log = new javax.swing.JScrollPane(new javax.swing.JTextArea("baris"));
        var own = new javax.swing.JScrollPane(new javax.swing.JTextArea());
        own.getViewport().setScrollMode(javax.swing.JViewport.BACKINGSTORE_SCROLL_MODE); // pilihan komponen sendiri
        var inner = new JPanel(new BorderLayout());
        inner.add(log, BorderLayout.CENTER);
        inner.add(own, BorderLayout.SOUTH);
        panel.add(inner, BorderLayout.CENTER);

        panel.setRounded(true);
        assertThat(log.getViewport().getScrollMode()).isEqualTo(javax.swing.JViewport.SIMPLE_SCROLL_MODE);
        assertThat(own.getViewport().getScrollMode()).isEqualTo(javax.swing.JViewport.BACKINGSTORE_SCROLL_MODE);

        panel.setRounded(false);
        assertThat(log.getViewport().getScrollMode()).isEqualTo(javax.swing.JViewport.BLIT_SCROLL_MODE);
        assertThat(own.getViewport().getScrollMode()).isEqualTo(javax.swing.JViewport.BACKINGSTORE_SCROLL_MODE);
    }

    @Test
    void scrollPaneYangDitambahkanKemudianIkutSaatLayout() {
        var panel = new RoundedPanel();
        panel.setRounded(true);
        var later = new javax.swing.JScrollPane(new javax.swing.JTextArea());
        panel.add(later, BorderLayout.CENTER);
        panel.setSize(300, 200);
        panel.doLayout();

        assertThat(later.getViewport().getScrollMode()).isEqualTo(javax.swing.JViewport.SIMPLE_SCROLL_MODE);
    }
}
