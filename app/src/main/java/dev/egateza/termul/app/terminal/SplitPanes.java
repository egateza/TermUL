package dev.egateza.termul.app.terminal;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
 * Isi satu tab: satu panel, atau beberapa tab yang digabung (split) berdampingan dalam satu arah, maksimal
 * {@value #MAX_PANES} panel. Panel aktif mengikuti fokus keyboard; saat ada lebih dari satu panel, panel aktif diberi
 * garis aksen. Semua method dipanggil di EDT.
 *
 * @param <T> jenis panel (di aplikasi: {@link TerminalTab})
 */
public final class SplitPanes<T extends JComponent> extends JPanel {

    /** Jumlah maksimal panel dalam satu grup. */
    public static final int MAX_PANES = 3;

    private static final int ACTIVE_BORDER = 2;

    private final Class<T> type;
    private final List<T> panes = new ArrayList<>(); // EDT; urutan tampil
    private final List<JSplitPane> splits = new ArrayList<>(); // EDT; split yang sedang dipakai layout
    private int orientation;  // EDT; JSplitPane.HORIZONTAL_SPLIT (menyamping) atau VERTICAL_SPLIT (atas-bawah)
    private T active;         // EDT; null setelah panel terakhir dilepas
    private Runnable activeListener = () -> { }; // EDT
    private final PropertyChangeListener focusTracker = e -> {
        if (e.getNewValue() instanceof Component owner) {
            paneOf(owner).ifPresent(this::setActive);
        }
    };

    public SplitPanes(Class<T> type, T first) {
        this(type, List.of(first), JSplitPane.HORIZONTAL_SPLIT);
    }

    /** @param panes 1..{@value #MAX_PANES} panel, panel pertama menjadi aktif */
    public SplitPanes(Class<T> type, List<T> panes, int orientation) {
        super(new BorderLayout());
        if (panes.isEmpty() || panes.size() > MAX_PANES) {
            throw new IllegalArgumentException("Jumlah panel harus 1.." + MAX_PANES + ": " + panes.size());
        }
        this.type = type;
        this.orientation = orientation;
        this.panes.addAll(panes);
        this.active = panes.getFirst();
        relayout();
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
        return List.copyOf(panes);
    }

    public int orientation() {
        return orientation;
    }

    /** Ganti arah split: {@link JSplitPane#HORIZONTAL_SPLIT} (menyamping) atau {@link JSplitPane#VERTICAL_SPLIT}. */
    public void setOrientation(int orientation) {
        if (orientation == this.orientation) {
            return;
        }
        this.orientation = orientation;
        relayout();
    }

    /** Panel yang memuat komponen {@code c} (mis. sumber key event), kalau ada di tab ini. */
    public Optional<T> paneOf(Component c) {
        if (c == null) {
            return Optional.empty();
        }
        return panes.stream().filter(p -> SwingUtilities.isDescendingFrom(c, p)).findFirst();
    }

    public void setActive(T pane) {
        if (pane == active || !panes.contains(pane)) {
            return;
        }
        active = pane;
        updateBorders();
        activeListener.run();
    }

    /** Panel berikutnya (berputar) menjadi aktif; null kalau hanya ada satu panel. */
    public T next() {
        if (panes.size() < 2) {
            return null;
        }
        T next = panes.get((panes.indexOf(active) + 1) % panes.size());
        setActive(next);
        return next;
    }

    /**
     * Melepas satu panel (tidak di-dispose); panel lain mengisi ruangnya.
     *
     * @return true kalau tidak ada panel tersisa (tab boleh ditutup)
     */
    public boolean removePane(T pane) {
        int index = panes.indexOf(pane);
        if (index < 0) {
            return panes.isEmpty();
        }
        panes.remove(index);
        if (active == pane) {
            active = panes.isEmpty() ? null : panes.get(Math.min(index, panes.size() - 1));
        }
        relayout();
        activeListener.run();
        return panes.isEmpty();
    }

    /** Melepas semua panel (untuk ungroup: tiap panel dipindah ke tab sendiri). Tab ini kosong sesudahnya. */
    public List<T> detachAll() {
        var result = List.copyOf(panes);
        panes.clear();
        active = null;
        relayout();
        result.forEach(p -> p.setBorder(null));
        return result;
    }

    /** Susun ulang: panel tunggal langsung, atau rantai JSplitPane dengan ukuran sama rata. */
    private void relayout() {
        for (var split : splits) {
            split.removeAll(); // lepaskan panel dari split lama sebelum dipasang lagi
        }
        splits.clear();
        removeAll();
        if (!panes.isEmpty()) {
            add(chain(0), BorderLayout.CENTER);
        }
        updateBorders();
        revalidate();
        repaint();
        if (!splits.isEmpty()) {
            SwingUtilities.invokeLater(this::equalize); // setelah ukuran split diketahui
        }
    }

    private Component chain(int from) {
        if (from == panes.size() - 1) {
            return panes.get(from);
        }
        var split = new JSplitPane(orientation, true);
        split.setBorder(null);
        int remaining = panes.size() - from;
        split.setResizeWeight(1.0 / remaining);
        split.setLeftComponent(panes.get(from));
        splits.add(split);
        split.setRightComponent(chain(from + 1));
        return split;
    }

    /** Bagi ruang sama rata: split ke-i memberi panel kirinya 1/(sisa panel). */
    private void equalize() {
        for (int i = 0; i < splits.size(); i++) {
            var split = splits.get(i);
            split.validate();
            split.setDividerLocation(1.0 / (panes.size() - i));
            split.validate(); // split dalam berikutnya ikut ukuran baru
        }
    }

    /** Garis aksen di panel aktif hanya saat tab di-split; panel tunggal tanpa border. */
    private void updateBorders() {
        Color accent = UIManager.getColor("Component.focusColor");
        if (accent == null) {
            accent = UIManager.getColor("Component.accentColor");
        }
        if (accent == null) {
            accent = new Color(0x4A88C7);
        }
        for (T p : panes) {
            if (panes.size() < 2) {
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
