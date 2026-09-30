package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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

    private static SplitPanes<JPanel> group(int orientation, JPanel... panes) {
        return new SplitPanes<>(JPanel.class, List.of(panes), orientation);
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
    void duaPanelMenyampingDenganPanelPertamaAktif() {
        var a = pane("a");
        var b = pane("b");
        var group = group(JSplitPane.HORIZONTAL_SPLIT, a, b);

        var split = (JSplitPane) group.getComponent(0);
        assertThat(split.getOrientation()).isEqualTo(JSplitPane.HORIZONTAL_SPLIT);
        assertThat(split.getLeftComponent()).isSameAs(a);
        assertThat(split.getRightComponent()).isSameAs(b);
        assertThat(group.active()).isSameAs(a);
        assertThat(a.getBorder()).isNotNull();
        assertThat(b.getBorder()).isNotNull();
    }

    @Test
    void tigaPanelDibagiRataDalamSatuArah() {
        var a = pane("a");
        var b = pane("b");
        var c = pane("c");
        var group = group(JSplitPane.VERTICAL_SPLIT, a, b, c);

        var outer = (JSplitPane) group.getComponent(0);
        var inner = (JSplitPane) outer.getRightComponent();
        assertThat(outer.getOrientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
        assertThat(inner.getOrientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
        assertThat(outer.getLeftComponent()).isSameAs(a);
        assertThat(inner.getLeftComponent()).isSameAs(b);
        assertThat(inner.getRightComponent()).isSameAs(c);
        assertThat(outer.getResizeWeight()).isEqualTo(1.0 / 3);
        assertThat(inner.getResizeWeight()).isEqualTo(0.5);
        assertThat(group.panes()).containsExactly(a, b, c);
    }

    @Test
    void lebihDariMaksimalDitolak() {
        assertThatThrownBy(() -> group(JSplitPane.HORIZONTAL_SPLIT, pane("a"), pane("b"), pane("c"), pane("d")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SplitPanes.MAX_PANES).isEqualTo(3);
    }

    @Test
    void gantiArahMenyusunUlangPanelYangSama() {
        var a = pane("a");
        var b = pane("b");
        var group = group(JSplitPane.HORIZONTAL_SPLIT, a, b);

        group.setOrientation(JSplitPane.VERTICAL_SPLIT);

        var split = (JSplitPane) group.getComponent(0);
        assertThat(split.getOrientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
        assertThat(split.getLeftComponent()).isSameAs(a);
        assertThat(split.getRightComponent()).isSameAs(b);
        assertThat(group.orientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
    }

    @Test
    void nextBerputarDanMemberitahuListener() {
        var a = pane("a");
        var b = pane("b");
        var c = pane("c");
        var group = group(JSplitPane.HORIZONTAL_SPLIT, a, b, c);
        var changes = new AtomicInteger();
        group.setActiveListener(changes::incrementAndGet);

        assertThat(group.next()).isSameAs(b);
        assertThat(group.next()).isSameAs(c);
        assertThat(group.next()).isSameAs(a);
        assertThat(changes).hasValue(3);
    }

    @Test
    void menutupPanelMembuatPanelLainMengisiRuang() {
        var a = pane("a");
        var b = pane("b");
        var c = pane("c");
        var group = group(JSplitPane.HORIZONTAL_SPLIT, a, b, c);
        group.setActive(b);

        assertThat(group.removePane(b)).isFalse();

        var split = (JSplitPane) group.getComponent(0);
        assertThat(split.getLeftComponent()).isSameAs(a);
        assertThat(split.getRightComponent()).isSameAs(c);
        assertThat(group.active()).isSameAs(c); // panel di posisi yang sama

        assertThat(group.removePane(a)).isFalse();

        assertThat(group.getComponent(0)).isSameAs(c);
        assertThat(c.getBorder()).isNull();
        assertThat(group.removePane(c)).isTrue();
        assertThat(group.panes()).isEmpty();
    }

    @Test
    void detachAllMelepasSemuaPanelUntukUngroup() {
        var a = pane("a");
        var b = pane("b");
        var group = group(JSplitPane.HORIZONTAL_SPLIT, a, b);

        var detached = group.detachAll();

        assertThat(detached).containsExactly(a, b);
        assertThat(a.getParent()).isNull();
        assertThat(b.getParent()).isNull();
        assertThat(a.getBorder()).isNull();
        assertThat(group.panes()).isEmpty();
        assertThat(group.getComponentCount()).isZero();

        var single = new SplitPanes<>(JPanel.class, a); // panel bisa langsung dipasang di tab baru
        assertThat(single.getComponent(0)).isSameAs(a);
    }

    @Test
    void paneOfMenemukanPanelDariKomponenDiDalamnya() {
        var a = pane("a");
        var b = pane("b");
        var group = group(JSplitPane.HORIZONTAL_SPLIT, a, b);

        assertThat(group.paneOf(b.getComponent(0))).contains(b);
        assertThat(group.paneOf(a)).contains(a);
        assertThat(group.paneOf(new JLabel("lain"))).isEmpty();
        assertThat(group.paneOf(null)).isEmpty();
    }
}
