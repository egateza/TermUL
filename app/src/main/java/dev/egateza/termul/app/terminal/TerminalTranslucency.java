package dev.egateza.termul.app.terminal;

import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Transparansi latar terminal (Pengaturan > Transparansi jendela, slider "Latar terminal"): berlaku untuk semua panel
 * terminal, teks tetap solid. Hanya berarti kalau window-nya sedang beralpha per piksel; selain itu 100.
 *
 * <p>Juga mencatat panel terminal yang sedang tampil, supaya window bisa mengecualikan area terminal dari efek pudar
 * bagian lain (slider "Jendela"). EDT.
 */
public final class TerminalTranslucency {

    private static volatile int percent = 100; // dibaca saat paint (EDT); ditulis di EDT
    private static final Set<JComponent> panels = Collections.newSetFromMap(new WeakHashMap<>()); // EDT

    private TerminalTranslucency() {
    }

    /** @return opasitas latar terminal yang berlaku, persen 0..100 (100 = solid) */
    public static int percent() {
        return percent;
    }

    /** @param value persen; 100 = solid (juga dipakai kalau window tidak beralpha per piksel) */
    public static void setPercent(int value) {
        percent = Math.clamp(value, 0, 100);
    }

    static void register(JComponent panel) {
        panels.add(panel);
    }

    static void unregister(JComponent panel) {
        panels.remove(panel);
    }

    /** @return area panel terminal yang terlihat di {@code root}, dalam koordinat {@code root} */
    public static List<Rectangle> visibleAreasIn(Component root) {
        var result = new ArrayList<Rectangle>();
        for (var p : panels) {
            if (!p.isShowing() || SwingUtilities.getRoot(p) != SwingUtilities.getRoot(root)) {
                continue;
            }
            Rectangle visible = p.getVisibleRect();
            if (!visible.isEmpty()) {
                result.add(SwingUtilities.convertRectangle(p, visible, root));
            }
        }
        return result;
    }
}
