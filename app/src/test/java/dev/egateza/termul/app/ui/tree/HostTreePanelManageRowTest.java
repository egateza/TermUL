package dev.egateza.termul.app.ui.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class HostTreePanelManageRowTest {

    /** Prototipe: baris Manage profiles + shortcut di bawah daftar host; klik membuka Profile manager. */
    @Test
    void barisManageProfilesDiBawahDaftarMembukaProfileManager() {
        var opened = new AtomicInteger();
        var actions = (HostTreePanel.Actions) Proxy.newProxyInstance(HostTreePanel.Actions.class.getClassLoader(),
                new Class<?>[] {HostTreePanel.Actions.class}, (proxy, method, args) -> {
                    if (method.getName().equals("manageProfiles")) {
                        opened.incrementAndGet();
                    }
                    return null;
                });
        var panel = new HostTreePanel(actions);
        var row = (JComponent) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        assertThat(row.getInsets()).isEqualTo(new java.awt.Insets(1, 10, 0, 10)); // garis atas + padding 0 10px

        panel.setManageShortcut("Ctrl+Shift+H");
        row.setSize(240, 32);
        // klik cepat: kursor bergeser sedikit antara tekan dan lepas (AWT tidak mengirim MOUSE_CLICKED)
        press(row, MouseEvent.MOUSE_PRESSED, 20, 10);
        press(row, MouseEvent.MOUSE_RELEASED, 23, 12);
        assertThat(opened).hasValue(1);

        // ditekan lalu dilepas di luar baris: batal, seperti tombol
        press(row, MouseEvent.MOUSE_PRESSED, 20, 10);
        press(row, MouseEvent.MOUSE_RELEASED, 20, -40);
        assertThat(opened).hasValue(1);
        assertThat(row.getPreferredSize().height).isEqualTo(32);
        assertThat(((JLabel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST)).getText())
                .isEqualTo("Ctrl+Shift+H");
    }

    private static void press(JComponent c, int id, int x, int y) {
        int mask = id == MouseEvent.MOUSE_PRESSED ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK : 0;
        c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), mask, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
