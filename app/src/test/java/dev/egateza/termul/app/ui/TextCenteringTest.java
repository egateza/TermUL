package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import org.junit.jupiter.api.Test;

class TextCenteringTest {

    /**
     * Huruf di kolom input tepat di tengah vertikal (ruang atas dan bawah berselisih paling banyak 1 px), juga dengan
     * font yang metriknya timpang seperti Bahnschrift. Tinggi kolom tidak berubah.
     */
    @Test
    void hurufDiTengahKolomTanpaMengubahTinggi() throws Exception {
        var originalMargin = UIManager.getInsets("TextField.margin");
        UIManager.put("TextField.margin", new Insets(2, 6, 2, 6)); // seperti FlatLaf
        try {
            for (String family : new String[] {"Bahnschrift", Font.DIALOG}) {
                var font = new Font(family, Font.PLAIN, 12);
                if (!font.getFamily().equals(family) && !family.equals(Font.DIALOG)) {
                    continue; // font tidak terpasang di mesin ini
                }
                int[] result = new int[3];
                SwingUtilities.invokeAndWait(() -> {
                    var field = new JTextField("Cari host");
                    field.setFont(font);
                    // seperti border FlatLaf: tepi 1 px + margin kolom
                    field.setBorder(new CompoundBorder(new EmptyBorder(1, 1, 1, 1),
                            new javax.swing.plaf.basic.BasicBorders.MarginBorder()));
                    field.setForeground(Color.BLACK);
                    field.setBackground(Color.WHITE);
                    field.setCaretColor(Color.WHITE);
                    field.setMargin(new Insets(2, 6, 2, 6));
                    int heightBefore = field.getPreferredSize().height;
                    TextCentering.apply(field);
                    assertThat(field.getPreferredSize().height).as("tinggi kolom tetap").isEqualTo(heightBefore);
                    field.setSize(200, heightBefore);
                    var img = new BufferedImage(200, heightBefore, BufferedImage.TYPE_INT_RGB);
                    var g = img.createGraphics();
                    field.paint(g);
                    g.dispose();
                    int first = -1;
                    int last = -1;
                    for (int y = 1; y < heightBefore - 1; y++) {
                        for (int x = 4; x < 120; x++) {
                            if ((img.getRGB(x, y) & 0xFF) < 128) {
                                first = first < 0 ? y : first;
                                last = y;
                                break;
                            }
                        }
                    }
                    result[0] = first - 1;
                    result[1] = heightBefore - 2 - last;
                    result[2] = heightBefore;
                });
                assertThat(Math.abs(result[0] - result[1]))
                        .as(family + ": ruang atas " + result[0] + ", bawah " + result[1]).isLessThanOrEqualTo(1);
            }
        } finally {
            UIManager.put("TextField.margin", originalMargin);
        }
    }
}
