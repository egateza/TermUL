package dev.egateza.termul.app.ui.anim;

import java.awt.Component;
import java.awt.Frame;
import javax.swing.SwingUtilities;

/**
 * Apakah animasi dekoratif (api streak, animasi status bar) perlu digambar ulang sekarang. Window yang di-minimize
 * atau tertutup layar idle tetap menjalankan timer, dan di window transparan setiap frame menggambar ulang hierarki
 * komponen beberapa kali lewat root pane; sampah Graphics-nya membuat heap membengkak walau tidak ada sesi. EDT.
 */
public final class AnimationGate {

    private AnimationGate() {
    }

    /** @return true kalau {@code c} tampil dan tidak tertutup apa pun */
    public static boolean shouldAnimate(Component c) {
        return c.isShowing() && !hidden(c);
    }

    /**
     * @return true kalau window {@code c} di-minimize, atau {@code c} tertutup glass pane pekat yang tampil (layar idle);
     *     isi glass pane itu sendiri tidak tertutup
     */
    static boolean hidden(Component c) {
        if (SwingUtilities.getWindowAncestor(c) instanceof Frame f && (f.getExtendedState() & Frame.ICONIFIED) != 0) {
            return true;
        }
        var root = SwingUtilities.getRootPane(c);
        Component glass = root == null ? null : root.getGlassPane();
        return glass != null && glass.isVisible() && glass.isOpaque() && glass != c
                && !SwingUtilities.isDescendingFrom(c, glass);
    }
}
