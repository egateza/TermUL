package dev.egateza.termul.app.ui;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * Tab bar window utama: gaya tab dari prototipe ({@link TermTabbedPaneUI}, juga setelah tema berganti), dan pindah
 * tab tanpa fokus mampir ke komponen lain.
 *
 * <p>Saat tab berganti, panel tab lama disembunyikan. Kalau fokus ada di dalamnya (terminal), Swing langsung
 * memindahkan fokus ke komponen berikutnya di window, yaitu kolom "Search hosts", sebelum terminal tab baru
 * difokuskan. Kolom cari sesaat menyala, dan penanda tab aktif sesaat digambar dengan warna "tidak fokus" (terasa
 * seperti highlight yang terlambat). Permintaan fokus ke tab bar sendiri sebelum berganti membuat Swing tidak
 * memindahkan fokus otomatis (ada permintaan fokus yang tertunda); terminal tab baru difokuskan sesudahnya. EDT.
 */
final class TermTabbedPane extends JTabbedPane {

    TermTabbedPane() {
        super(TOP, SCROLL_TAB_LAYOUT);
    }

    @Override
    public void updateUI() {
        setUI(new TermTabbedPaneUI());
    }

    @Override
    public void setSelectedIndex(int index) {
        var old = getSelectedComponent();
        if (index != getSelectedIndex() && old != null && SwingUtilities.findFocusOwner(old) != null) {
            requestFocusInWindow();
        }
        super.setSelectedIndex(index);
    }
}
