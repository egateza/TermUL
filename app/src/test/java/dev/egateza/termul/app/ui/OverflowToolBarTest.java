package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class OverflowToolBarTest {

    private static JButton button(String name) {
        var b = new JButton();
        b.setName(name);
        b.setPreferredSize(new Dimension(30, 24));
        return b;
    }

    private record Bar(OverflowToolBar bar, JButton a, JButton b, JButton c, JTextField filter) {
    }

    private static Bar bar() {
        var bar = new OverflowToolBar();
        bar.setBorder(null);
        var a = button("a");
        var b = button("b");
        var c = button("c");
        var filter = new JTextField();
        filter.setPreferredSize(new Dimension(100, 22));
        bar.add(a);
        bar.add(b);
        bar.addSeparator();
        bar.add(c);
        bar.add(Box.createHorizontalGlue());
        bar.add(filter);
        return new Bar(bar, a, b, c, filter);
    }

    private static void layout(OverflowToolBar bar, int width) {
        bar.setSize(width, bar.getPreferredSize().height);
        bar.doLayout();
    }

    @Test
    void muatSemuaTanpaTombolLainnyaDanGlueMengisiSisa() {
        var t = bar();
        int pref = t.bar().getPreferredSize().width;
        layout(t.bar(), pref + 50);

        assertThat(t.bar().hasOverflow()).isFalse();
        assertThat(t.c().getWidth()).as("tombol tidak menyempit").isEqualTo(30);
        assertThat(t.filter().getWidth()).isEqualTo(100);
        assertThat(t.filter().getX() + t.filter().getWidth()).as("filter tetap di ujung kanan").isEqualTo(pref + 50);
    }

    @Test
    void yangTidakMuatPindahKeTombolLainnyaTanpaMenyempit() {
        var t = bar();
        layout(t.bar(), 90);

        assertThat(t.bar().hasOverflow()).isTrue();
        assertThat(t.a().getWidth()).isEqualTo(30);
        assertThat(t.b().getWidth()).isEqualTo(30);
        assertThat(t.bar().overflowItems()).containsExactly(t.c(), t.filter()); // pemisah di awal popup dibuang
        assertThat(t.c().getWidth()).isZero();
        assertThat(t.bar().getMinimumSize().width).isLessThan(t.bar().getPreferredSize().width);
    }

    @Test
    void itemTersembunyiOlehKodeTidakIkutDihitung() {
        var t = bar();
        t.c().setVisible(false);
        int pref = t.bar().getPreferredSize().width;
        layout(t.bar(), pref);

        assertThat(t.bar().hasOverflow()).isFalse();
        assertThat(t.bar().overflowItems()).isEmpty();
    }
}
