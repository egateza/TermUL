package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Toolbar horizontal yang tidak menyempitkan isinya saat lebarnya kurang: item yang tidak muat (dari kanan)
 * disembunyikan dan tombol » di ujung kanan menampilkannya dalam popup, seperti toolbar WinSCP/Explorer.
 * Item di popup adalah komponen aslinya (listener & state tetap), dipindah sementara selama popup terbuka.
 * Semua method dipanggil di EDT.
 */
public class OverflowToolBar extends JToolBar {

    private final JButton more = new JButton(AppIcon.ANGLES_RIGHT.icon(12));
    private final JPopupMenu popup = new JPopupMenu();
    private final JToolBar popupBar = new JToolBar();
    /** Item yang tidak muat pada layout terakhir, urut dari kiri. */
    private final List<Component> overflow = new ArrayList<>();
    /** Item yang sedang dipindah ke popup + posisi asalnya. */
    private final List<Moved> moved = new ArrayList<>();
    private final OverflowLayout layout = new OverflowLayout();

    private record Moved(Component component, int index, ActionListener closer) {
    }

    public OverflowToolBar() {
        setFloatable(false);
        setLayout(layout);
        more.setFocusable(false);
        more.setToolTipText(I18n.t("toolbar.more.tip"));
        more.addActionListener(e -> showOverflow());
        add(more);
        popupBar.setFloatable(false);
        popup.add(popupBar);
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                restore();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (layout != null) { // null saat dipanggil dari constructor JToolBar
            setLayout(layout); // UI baru (ganti tema) bisa memasang layout bawaan lagi
            SwingUtilities.updateComponentTreeUI(popup); // popup tidak ada di component tree selama tertutup
        }
    }

    /** Item yang tidak muat pada layout terakhir (untuk test). */
    List<Component> overflowItems() {
        return List.copyOf(overflow);
    }

    /** true kalau tombol » sedang tampil. */
    public boolean hasOverflow() {
        return !overflow.isEmpty();
    }

    /**
     * Pastikan komponen bisa dipakai user: kalau sedang tidak muat, buka popup » yang berisi komponen itu
     * (mis. Ctrl+F ke kolom filter yang tersembunyi).
     */
    public void reveal(Component c) {
        if (overflow.contains(c)) {
            showOverflow();
        }
    }

    private void showOverflow() {
        if (overflow.isEmpty() || popup.isVisible()) {
            return;
        }
        moved.clear();
        for (Component c : overflow) {
            ActionListener closer = c instanceof AbstractButton
                    ? e -> SwingUtilities.invokeLater(() -> popup.setVisible(false)) : null;
            moved.add(new Moved(c, getComponentIndex(c), closer));
        }
        popupBar.removeAll();
        for (Moved m : moved) {
            remove(m.component());
            if (m.closer() != null) {
                ((AbstractButton) m.component()).addActionListener(m.closer());
            }
            popupBar.add(m.component());
            m.component().setBounds(0, 0, 0, 0);
        }
        popupBar.revalidate();
        revalidate();
        int width = popup.getPreferredSize().width;
        popup.show(more, more.getWidth() - width, more.getHeight());
    }

    /** Kembalikan item dari popup ke posisi asalnya di toolbar. */
    private void restore() {
        if (moved.isEmpty()) {
            return;
        }
        popupBar.removeAll();
        for (Moved m : moved) { // urut indeks naik: setiap item kembali tepat di posisinya
            if (m.closer() != null) {
                ((AbstractButton) m.component()).removeActionListener(m.closer());
            }
            add(m.component(), m.index());
        }
        moved.clear();
        revalidate();
        repaint();
    }

    private static boolean isFiller(Component c) {
        return c instanceof Box.Filler;
    }

    /** Item kiri→kanan dengan lebar preferred; glue menyerap sisa lebar; yang tidak muat masuk {@link #overflow}. */
    private final class OverflowLayout implements LayoutManager {

        private List<Component> items(Container parent) {
            var items = new ArrayList<Component>();
            for (Component c : parent.getComponents()) {
                if (c != more && c.isVisible()) {
                    items.add(c);
                }
            }
            return items;
        }

        private int height(Container parent) {
            int h = more.getPreferredSize().height;
            for (Component c : items(parent)) {
                h = Math.max(h, c.getPreferredSize().height);
            }
            Insets in = parent.getInsets();
            return h + in.top + in.bottom;
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            int w = in.left + in.right;
            for (Component c : items(parent)) {
                w += c.getPreferredSize().width;
            }
            return new Dimension(w, height(parent));
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            return new Dimension(in.left + in.right + more.getPreferredSize().width, height(parent));
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int avail = parent.getWidth() - in.left - in.right;
            int h = parent.getHeight() - in.top - in.bottom;
            var items = items(parent);
            int total = items.stream().mapToInt(c -> c.getPreferredSize().width).sum();
            overflow.clear();

            boolean fits = total <= avail;
            int limit = fits ? avail : avail - more.getPreferredSize().width;
            int fillers = fits ? (int) items.stream().filter(OverflowToolBar::isFiller).count() : 0;
            int extra = fits ? avail - total : 0;

            int x = in.left;
            Component lastPlaced = null;
            for (Component c : items) {
                int w = c.getPreferredSize().width;
                if (isFiller(c)) {
                    if (fillers > 0) {
                        int share = extra / fillers--;
                        extra -= share;
                        w += share;
                    }
                } else if (!overflow.isEmpty() || x - in.left + w > limit) {
                    overflow.add(c);
                    c.setBounds(0, 0, 0, 0);
                    continue;
                }
                place(c, x, w, in.top, h);
                x += w;
                if (!isFiller(c)) {
                    lastPlaced = c;
                }
            }
            if (!overflow.isEmpty() && lastPlaced instanceof JToolBar.Separator) {
                lastPlaced.setBounds(0, 0, 0, 0); // pemisah di ujung sebelum » tidak berguna
            }
            if (!overflow.isEmpty() && overflow.getFirst() instanceof JToolBar.Separator) {
                overflow.removeFirst(); // pemisah di awal popup juga tidak berguna
            }
            if (overflow.isEmpty()) {
                more.setBounds(0, 0, 0, 0);
            } else {
                int mw = more.getPreferredSize().width;
                more.setBounds(parent.getWidth() - in.right - mw, in.top, mw, h);
            }
        }

        /** Tombol & pemisah setinggi toolbar; field/combo memakai tinggi preferred, di tengah. */
        private void place(Component c, int x, int w, int top, int h) {
            int ch = c instanceof AbstractButton || c instanceof JToolBar.Separator || isFiller(c)
                    ? h : Math.min(h, c.getPreferredSize().height);
            c.setBounds(x, top + (h - ch) / 2, w, ch);
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }
    }
}
