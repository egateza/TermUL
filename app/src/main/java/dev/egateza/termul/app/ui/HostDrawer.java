package dev.egateza.termul.app.ui;

import dev.egateza.termul.core.config.AppConfig;
import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;

/**
 * Panel host sebagai laci melayang: sehari-hari hanya tombol kecil di tepi kiri area terminal (bisa dibuat
 * transparan), sehingga terminal memakai seluruh lebar. Klik tombol menampilkan daftar host di <i>atas</i> terminal
 * (terminal tidak di-resize, jadi remote tidak menerima perubahan ukuran). Laci menutup dengan tombolnya, Esc, atau klik
 * di luar laci. Semua di lapisan {@link JLayeredPane} milik window; hanya dipakai di EDT.
 */
public final class HostDrawer {

    static final int TOGGLE_WIDTH = 22;
    static final int TOGGLE_HEIGHT = 48;
    static final int MIN_WIDTH = 220;
    static final int DEFAULT_WIDTH = 280;
    private static final int GRIP_WIDTH = 5;
    private static final int MIN_TERMINAL_WIDTH = 160;

    private final JLayeredPane layers;
    private final JComponent anchor;
    private final JComponent content;
    private final Runnable onClosedWithFocus;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final Toggle toggle = new Toggle();
    private final java.awt.event.AWTEventListener outsideClick = this::onAwtEvent;
    private int width = DEFAULT_WIDTH;
    private boolean active; // false = mode panel di samping: laci dan tombol tidak dipakai
    private boolean open;

    /**
     * @param layers            layered pane window (tempat laci dan tombol digambar di atas konten)
     * @param anchor            area yang ditutupi laci (area terminal); laci mengikuti posisi dan tingginya
     * @param content           isi laci (daftar host)
     * @param onClosedWithFocus dipanggil saat laci menutup sementara fokus ada di dalamnya
     *                          (mengembalikan fokus ke terminal)
     */
    public HostDrawer(JLayeredPane layers, JComponent anchor, JComponent content, Runnable onClosedWithFocus) {
        this.layers = layers;
        this.anchor = anchor;
        this.content = content;
        this.onClosedWithFocus = onClosedWithFocus;

        var grip = new JPanel();
        grip.setPreferredSize(new Dimension(GRIP_WIDTH, 0));
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        grip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, borderColor()));
        var resize = new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int left = SwingUtilities.convertPoint(anchor, 0, 0, layers).x;
                setWidth(SwingUtilities.convertPoint(grip, e.getX(), 0, layers).x - left);
            }
        };
        grip.addMouseListener(resize);
        grip.addMouseMotionListener(resize);

        panel.add(grip, BorderLayout.EAST);
        panel.setVisible(false);
        panel.registerKeyboardAction(e -> setOpen(false), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        toggle.onClick = () -> setOpen(!open);

        layers.add(panel, JLayeredPane.PALETTE_LAYER);
        layers.add(toggle, Integer.valueOf(JLayeredPane.PALETTE_LAYER + 10));
        var relayout = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layout();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                layout();
            }
        };
        layers.addComponentListener(relayout);
        anchor.addComponentListener(relayout);
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClick, AWTEvent.MOUSE_EVENT_MASK);
        toggle.setOpen(false);
        toggle.setVisible(false);
    }

    /** Mulai dipakai (mode tombol melayang): {@code content} dipindah ke laci, tombol tampil. Laci mulai tertutup. */
    public void activate() {
        if (active) {
            return;
        }
        active = true;
        panel.add(content, BorderLayout.CENTER);
        toggle.setVisible(true);
        layout();
        layers.repaint();
    }

    /** Berhenti dipakai (mode panel di samping): laci ditutup dan {@code content} dilepas agar bisa dipasang di tempat lain. */
    public void deactivate() {
        if (!active) {
            return;
        }
        close(false);
        active = false;
        panel.remove(content);
        toggle.setVisible(false);
        layers.repaint();
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean wanted) {
        if (!active || wanted == open) {
            return;
        }
        if (wanted) {
            open = true;
            panel.setVisible(true);
            toggle.setOpen(true);
            layout();
            layers.repaint();
        } else {
            close(true);
        }
    }

    private void close(boolean restoreFocus) {
        if (!open) {
            return;
        }
        boolean hadFocus = restoreFocus && isFocusInside();
        open = false;
        panel.setVisible(false);
        toggle.setOpen(false);
        layout();
        layers.repaint();
        if (hadFocus) {
            onClosedWithFocus.run();
        }
    }

    /** @param percent {@value AppConfig#MIN_OPACITY}..100; di luar itu dibatasi */
    public void setOpacity(int percent) {
        toggle.opacity = Math.max(AppConfig.MIN_OPACITY, Math.min(100, percent));
        toggle.repaint();
    }

    public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClick);
    }

    // --- internal (package-private untuk test) ---

    Component panel() {
        return panel;
    }

    Toggle toggle() {
        return toggle;
    }

    int width() {
        return width;
    }

    void setWidth(int wanted) {
        int max = Math.max(MIN_WIDTH, anchor.getWidth() - MIN_TERMINAL_WIDTH);
        width = Math.max(MIN_WIDTH, Math.min(max, wanted));
        layout();
    }

    void layout() {
        if (!active) {
            return;
        }
        var origin = SwingUtilities.convertPoint(anchor, 0, 0, layers);
        int w = Math.min(width, Math.max(MIN_WIDTH, anchor.getWidth() - MIN_TERMINAL_WIDTH));
        int x = origin.x;
        if (open) {
            panel.setBounds(origin.x, origin.y, w, anchor.getHeight());
            x += w;
        }
        toggle.setBounds(x, origin.y + (anchor.getHeight() - TOGGLE_HEIGHT) / 2, TOGGLE_WIDTH, TOGGLE_HEIGHT);
        panel.revalidate();
    }

    /** Klik di luar laci (bukan menu, popup, atau dialog) menutupnya. */
    void pressed(Component source) {
        if (!active || !open || source == null || source == toggle || SwingUtilities.isDescendingFrom(source, panel)) {
            return;
        }
        Window window = SwingUtilities.getWindowAncestor(layers);
        if (SwingUtilities.getWindowAncestor(source) != window
                || source instanceof JPopupMenu
                || SwingUtilities.getAncestorOfClass(JPopupMenu.class, source) != null) {
            return;
        }
        JRootPane root = SwingUtilities.getRootPane(layers);
        if (root != null && root.getJMenuBar() != null && SwingUtilities.isDescendingFrom(source, root.getJMenuBar())) {
            return;
        }
        setOpen(false);
    }

    private void onAwtEvent(AWTEvent e) {
        if (open && e.getID() == MouseEvent.MOUSE_PRESSED && e.getSource() instanceof Component c) {
            pressed(c);
        }
    }

    private boolean isFocusInside() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner != null && SwingUtilities.isDescendingFrom(owner, panel);
    }

    private static Color borderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : Color.GRAY;
    }

    /** Tombol melayang: kapsul menempel di tepi kiri; opasitas diatur, kembali solid saat kursor di atasnya. */
    static final class Toggle extends JComponent {
        private static final int ARC = 14;

        Runnable onClick = () -> { };
        int opacity = AppConfig.DEFAULT_OPACITY;
        private boolean hover;
        private boolean open;

        Toggle() {
            setOpaque(false);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setHover(false);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (contains(e.getPoint())) {
                        onClick.run();
                    }
                }
            });
        }

        void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        void setOpen(boolean open) {
            this.open = open;
            setToolTipText(open ? "Sembunyikan daftar host" : "Tampilkan daftar host");
            repaint();
        }

        float alpha() {
            return hover ? 1f : opacity / 100f;
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha()));
                Color bg = UIManager.getColor("Button.background");
                g2.setColor(bg != null ? bg : Color.DARK_GRAY);
                // sisi kiri sengaja di luar komponen supaya hanya sudut kanan yang membulat
                g2.fillRoundRect(-ARC, 0, getWidth() + ARC, getHeight(), ARC, ARC);
                g2.setColor(borderColor());
                g2.drawRoundRect(-ARC, 0, getWidth() + ARC - 1, getHeight() - 1, ARC, ARC);
                Icon icon = (open ? AppIcon.ANGLES_LEFT : AppIcon.ANGLES_RIGHT).icon();
                icon.paintIcon(this, g2, (getWidth() - icon.getIconWidth()) / 2 - 1,
                        (getHeight() - icon.getIconHeight()) / 2);
            } finally {
                g2.dispose();
            }
        }
    }
}
