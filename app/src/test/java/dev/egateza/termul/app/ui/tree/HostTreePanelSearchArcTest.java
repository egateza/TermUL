package dev.egateza.termul.app.ui.tree;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatBorder;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostTreePanelSearchArcTest {

    private LookAndFeel previous;

    @BeforeEach
    void installFlatLaf() throws Exception {
        previous = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new FlatLightLaf());
    }

    @AfterEach
    void restoreLaf() throws Exception {
        UIManager.setLookAndFeel(previous);
    }

    @Test
    void searchFieldMengikutiSudutPanel() {
        var panel = new HostTreePanel(noActions());
        var search = find(panel);

        // Style "arc" hanya sampai ke border FlatLaf yang terpasang langsung (bukan dibungkus CompoundBorder).
        assertThat(search.getBorder()).isInstanceOf(FlatBorder.class);

        panel.setSearchArc(14);
        assertThat(search.getClientProperty("FlatLaf.style")).isEqualTo("arc: 14");

        panel.setSearchArc(0);
        assertThat(search.getClientProperty("FlatLaf.style")).isEqualTo("arc: 0");
    }

    private static HostTreePanel.Actions noActions() {
        return (HostTreePanel.Actions) Proxy.newProxyInstance(HostTreePanel.Actions.class.getClassLoader(),
                new Class<?>[] {HostTreePanel.Actions.class}, (proxy, method, args) -> null);
    }

    private static JTextField find(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField field) {
                return field;
            }
            if (c instanceof Container child) {
                var found = find(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
