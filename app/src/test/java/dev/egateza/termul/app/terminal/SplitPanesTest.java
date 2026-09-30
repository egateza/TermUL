package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.Test;

class SplitPanesTest {

    private static JPanel pane(String name) {
        var p = new JPanel();
        p.setName(name);
        p.add(new JLabel(name));
        return p;
    }

    @Test
    void satuPanelTanpaSplitDanTanpaBorder() {
        var a = pane("a");
        var group = new SplitPanes<>(JPanel.class, a);

        assertThat(group.panes()).containsExactly(a);
        assertThat(group.active()).isSameAs(a);
        assertThat(group.getComponent(0)).isSameAs(a);
        assertThat(a.getBorder()).isNull();
        assertThat(group.next()).isNull();
    }

    @Test
    void splitMenaruhPanelBaruDiKananDanMenjadikannyaAktif() {
        var a = pane("a");
        var b = pane("b");
        var group = new SplitPanes<>(JPanel.class, a);
        var changes = new AtomicInteger();
        group.setActiveListener(changes::incrementAndGet);

        group.split(b, JSplitPane.HORIZONTAL_SPLIT);

        var split = (JSplitPane) group.getComponent(0);
        assertThat(split.getOrientation()).isEqualTo(JSplitPane.HORIZONTAL_SPLIT);
        assertThat(split.getLeftComponent()).isSameAs(a);
        assertThat(split.getRightComponent()).isSameAs(b);
        assertThat(group.panes()).containsExactly(a, b);
        assertThat(group.active()).isSameAs(b);
        assertThat(changes).hasValue(1);
        assertThat(a.getBorder()).isNotNull();
        assertThat(b.getBorder()).isNotNull();
    }

    @Test
    void splitBersarangMengikutiPanelAktif() {
        var a = pane("a");
        var b = pane("b");
        var c = pane("c");
        var group = new SplitPanes<>(JPanel.class, a);
        group.split(b, JSplitPane.HORIZONTAL_SPLIT);
        group.setActive(a);

        group.split(c, JSplitPane.VERTICAL_SPLIT); // a dibagi atas/bawah, b tetap di kanan

        var outer = (JSplitPane) group.getComponent(0);
        var inner = (JSplitPane) outer.getLeftComponent();
        assertThat(inner.getOrientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
        assertThat(inner.getLeftComponent()).isSameAs(a);
        assertThat(inner.getRightComponent()).isSameAs(c);
        assertThat(outer.getRightComponent()).isSameAs(b);
        assertThat(group.panes()).containsExactly(a, c, b);
        assertThat(group.active()).isSameAs(c);
    }

    @Test
    void nextBerputarSesuaiUrutanPanel() {
        var a = pane("a");
        var b = pane("b");
        var c = pane("c");
        var group = new SplitPanes<>(JPanel.class, a);
        group.split(b, JSplitPane.HORIZONTAL_SPLIT);
        group.split(c, JSplitPane.VERTICAL_SPLIT);

        assertThat(group.next()).isSameAs(a);
        assertThat(group.next()).isSameAs(b);
        assertThat(group.next()).isSameAs(c);
    }

    @Test
    void menutupPanelMembuatSaudaranyaMengisiTempatSplit() {
        var a = pane("a");
        var b = pane("b");
        var c = pane("c");
        var group = new SplitPanes<>(JPanel.class, a);
        group.split(b, JSplitPane.HORIZONTAL_SPLIT);
        group.split(c, JSplitPane.VERTICAL_SPLIT); // [a | [b / c]]

        assertThat(group.removePane(c)).isFalse();

        var split = (JSplitPane) group.getComponent(0);
        assertThat(split.getLeftComponent()).isSameAs(a);
        assertThat(split.getRightComponent()).isSameAs(b);
        assertThat(group.panes()).containsExactly(a, b);
        assertThat(group.active()).isSameAs(b);

        assertThat(group.removePane(a)).isFalse();

        assertThat(group.getComponentCount()).isEqualTo(1);
        assertThat(group.getComponent(0)).isSameAs(b);
        assertThat(group.panes()).containsExactly(b);
        assertThat(b.getBorder()).isNull();
    }

    @Test
    void menutupPanelDiSplitDalamMempertahankanStrukturLuar() {
        var a = pane("a");
        var b = pane("b");
        var c = pane("c");
        var group = new SplitPanes<>(JPanel.class, a);
        group.split(b, JSplitPane.HORIZONTAL_SPLIT);
        group.setActive(a);
        group.split(c, JSplitPane.VERTICAL_SPLIT); // [[a / c] | b]
        group.setActive(b);

        group.removePane(a);

        var split = (JSplitPane) group.getComponent(0);
        assertThat(split.getLeftComponent()).isSameAs(c);
        assertThat(split.getRightComponent()).isSameAs(b);
        assertThat(group.active()).isSameAs(b); // panel aktif lain tidak berubah
    }

    @Test
    void menutupPanelTerakhirMengosongkanTab() {
        var a = pane("a");
        var group = new SplitPanes<>(JPanel.class, a);

        assertThat(group.removePane(a)).isTrue();
        assertThat(group.panes()).isEmpty();
    }

    @Test
    void paneOfMenemukanPanelDariKomponenDiDalamnya() {
        var a = pane("a");
        var b = pane("b");
        var group = new SplitPanes<>(JPanel.class, a);
        group.split(b, JSplitPane.HORIZONTAL_SPLIT);

        assertThat(group.paneOf(b.getComponent(0))).contains(b);
        assertThat(group.paneOf(a)).contains(a);
        assertThat(group.paneOf(new JLabel("lain"))).isEmpty();
        assertThat(group.paneOf(null)).isEmpty();
    }
}
