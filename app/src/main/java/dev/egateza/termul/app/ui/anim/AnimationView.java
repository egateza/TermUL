package dev.egateza.termul.app.ui.anim;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Komponen yang menjalankan satu {@link Animation} berukuran tetap. Timer hanya berjalan selama komponen terpasang
 * ({@link #addNotify}/{@link #removeNotify}) dan tidak memajukan frame saat tidak terlihat (mis. bar disembunyikan).
 * EDT.
 */
public final class AnimationView extends JComponent {

    static final int FRAME_MS = 33;

    private final Animation.Size size;
    private Animation animation; // EDT
    private final Timer timer = new Timer(FRAME_MS, e -> {
        if (isShowing()) {
            animation.step();
            repaint();
        }
    });

    public AnimationView(Animation.Size size, Animation animation) {
        this.size = size;
        this.animation = animation;
        setOpaque(false);
        var dim = new Dimension(size.width(), size.height());
        setPreferredSize(dim);
        setMinimumSize(dim);
        setMaximumSize(dim);
    }

    /** Ganti animasi (mis. pilihan di menu berubah). */
    public void setAnimation(Animation animation) {
        this.animation = animation;
        repaint();
    }

    Animation animation() {
        return animation;
    }

    /** {@link JComponent} polos tidak punya AccessibleContext (null): sediakan, dengan peran progress bar. */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.PROGRESS_BAR;
                }
            };
        }
        return accessibleContext;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int x0 = (getWidth() - size.width()) / 2;
            int y0 = (getHeight() - size.height()) / 2;
            g2.clipRect(x0, y0, size.width(), size.height());
            g2.translate(x0, y0);
            animation.paint(g2, size.width(), size.height(), Palette.current());
        } finally {
            g2.dispose();
        }
    }
}
