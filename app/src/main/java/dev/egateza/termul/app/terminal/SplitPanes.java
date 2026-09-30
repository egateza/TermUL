package dev.egateza.termul.app.terminal;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Isi satu tab: satu panel, atau beberapa panel yang dibagi dengan {@link JSplitPane} bersarang (split terminal).
 * Panel aktif mengikuti fokus keyboard; saat ada lebih dari satu panel, panel aktif diberi garis aksen. Semua method
 * dipanggil di EDT.
 *
 * @param <T> jenis panel (di aplikasi: {@link TerminalTab})
 */
public final class SplitPanes<T extends JComponent> extends JPanel {

    private static final int ACTIVE_BORDER = 2;

    private final Class<T> type;
    private T active; // EDT; null setelah panel terakhir dilepas
    private Runnable activeListener = () -> { }; // EDT
    private final PropertyChangeListener focusTracker = e -> {
        if (e.getNewValue() instanceof Component owner) {
            paneOf(owner).ifPresent(this::setActive);
        }
    };

    public SplitPanes(Class<T> type, T first) {
        super(new BorderLayout());
        this.type = type;
        this.active = first;
        add(first, BorderLayout.CENTER);
    }

    /** Dipanggil (di EDT) setiap panel aktif berganti atau jumlah panel berubah. */
    public void setActiveListener(Runnable listener) {
        this.activeListener = listener;
    }

    public T active() {
        return active;
    }

    /** Semua panel, urut kiri → kanan / atas → bawah. */
    public List<T> panes() {
        var result = new ArrayList<T>();
        if (getComponentCount() > 0) {
            collect(getComponent(0), result);
        }
        return result;
    }

    private void collect(Component c, List<T> into) {
        if (type.isInstance(c)) {
            into.add(type.cast(c));
        } else if (c instanceof JSplitPane split) {
            if (split.getLeftComponent() != null) {
                collect(split.getLeftComponent(), into);
            }
            if (split.getRightComponent() != null) {
                collect(split.getRightComponent(), into);
            }
        }
    }

    /** Panel yang memuat komponen {@code c} (mis. sumber key event), kalau ada di tab ini. */
    public Optional<T> paneOf(Component c) {
        if (c == null) {
            return Optional.empty();
        }
        return panes().stream().filter(p -> SwingUtilities.isDescendingFrom(c, p)).findFirst();
    }

    public void setActive(T pane) {
        if (pane == active || !panes().contains(pane)) {
            return;
        }
        active = pane;
        updateBorders();
        activeListener.run();
    }

    /**
     * Membagi panel aktif menjadi dua; panel baru di kanan ({@link JSplitPane#HORIZONTAL_SPLIT}) atau di bawah
     * ({@link JSplitPane#VERTICAL_SPLIT}) dan menjadi panel aktif.
     */
    public void split(T pane, int orientation) {
        T target = active;
        Container parent = target.getParent();
        JSplitPane outer = parent instanceof JSplitPane p ? p : null;
        boolean left = outer != null && outer.getLeftComponent() == target;
        int outerDivider = outer == null ? -1 : outer.getDividerLocation();

        var split = new JSplitPane(orientation, true);
        split.setBorder(null);
        split.setResizeWeight(0.5);
        if (outer == null) {
            remove(target);
        }
        split.setLeftComponent(target); // melepas target dari split luar (kalau ada)
        split.setRightComponent(pane);
        if (outer == null) {
            add(split, BorderLayout.CENTER);
        } else {
            if (left) {
                outer.setLeftComponent(split);
            } else {
                outer.setRightComponent(split);
            }
            outer.setDividerLocation(outerDivider);
        }
        active = pane;
        updateBorders();
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5)); // setelah ukuran split diketahui
        activeListener.run();
    }

    /**
     * Melepas panel dari tab; saudaranya mengambil tempat split yang ditinggalkan. Panel tidak di-dispose di sini.
     *
     * @return true kalau tidak ada panel tersisa (tab boleh ditutup)
     */
    public boolean removePane(T pane) {
        Container parent = pane.getParent();
        if (parent == this) {
            remove(pane);
            active = null;
            activeListener.run();
            return true;
        }
        if (!(parent instanceof JSplitPane split) || !SwingUtilities.isDescendingFrom(split, this)) {
            return panes().isEmpty();
        }
        Component sibling = split.getLeftComponent() == pane ? split.getRightComponent() : split.getLeftComponent();
        Container grand = split.getParent();
        split.removeAll();
        if (grand == this) {
            remove(split);
            add(sibling, BorderLayout.CENTER);
        } else {
            var outer = (JSplitPane) grand;
            int divider = outer.getDividerLocation();
            if (outer.getLeftComponent() == split) {
                outer.setLeftComponent(sibling);
            } else {
                outer.setRightComponent(sibling);
            }
            outer.setDividerLocation(divider);
        }
        if (active == pane || !panes().contains(active)) {
            var rest = new ArrayList<T>();
            collect(sibling, rest);
            active = rest.getFirst();
        }
        updateBorders();
        revalidate();
        repaint();
        activeListener.run();
        return false;
    }

    /** Panel berikutnya (berputar) menjadi aktif; null kalau hanya ada satu panel. */
    public T next() {
        var all = panes();
        if (all.size() < 2) {
            return null;
        }
        T next = all.get((all.indexOf(active) + 1) % all.size());
        setActive(next);
        return next;
    }

    /** Garis aksen di panel aktif hanya saat tab di-split; panel tunggal tanpa border. */
    private void updateBorders() {
        var all = panes();
        Color accent = UIManager.getColor("Component.focusColor");
        if (accent == null) {
            accent = UIManager.getColor("Component.accentColor");
        }
        if (accent == null) {
            accent = new Color(0x4A88C7);
        }
        for (T p : all) {
            if (all.size() < 2) {
                p.setBorder(null);
            } else if (p == active) {
                p.setBorder(BorderFactory.createLineBorder(accent, ACTIVE_BORDER));
            } else {
                p.setBorder(BorderFactory.createEmptyBorder(ACTIVE_BORDER, ACTIVE_BORDER, ACTIVE_BORDER, ACTIVE_BORDER));
            }
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (type != null) { // updateUI dipanggil dari konstruktor JPanel sebelum field terisi
            updateBorders(); // warna aksen ikut tema
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("permanentFocusOwner", focusTracker);
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("permanentFocusOwner", focusTracker);
        super.removeNotify();
    }
}
