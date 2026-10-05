package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
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
    private boolean tabBarButton; // gaya TAB_BAR dan ikonnya di tab bar terlihat: tombol melayang hanya saat laci terbuka

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
        // Layered pane sengaja hanya didengarkan ukurannya: posisi anchor relatif terhadap layered pane tidak berubah
        // saat layered pane itu digeser (mis. ShakeEffect), dan revalidate() di layout() akan mengembalikannya ke 0.
        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layout();
            }
        });
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

    /**
     * @param style        gaya tombol
     * @param tabBarButton ikon panel host di tab bar sedang terlihat (gaya {@link HostToggleStyle#TAB_BAR});
     *                     tombol melayang lalu hanya tampil saat laci terbuka (laci menutupi ikon itu)
     */
    public void setStyle(HostToggleStyle style, boolean tabBarButton) {
        toggle.setStyle(style);
        this.tabBarButton = style == HostToggleStyle.TAB_BAR && tabBarButton;
        layout();
        layers.repaint();
    }

    private boolean toggleWanted() {
        return active && (!tabBarButton || open);
    }

    /** @param percent {@value AppConfig#MIN_OPACITY}..100; di luar itu dibatasi */
    public void setOpacity(int percent) {
        toggle.setOpacity(percent);
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
        toggle.place(new java.awt.Rectangle(x, origin.y, anchor.getWidth() - (x - origin.x), anchor.getHeight()),
                x - GRIP_WIDTH / 2, open);
        toggle.setVisible(toggleWanted());
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

    /**
     * Tombol melayang di tepi kiri area terminal; bentuknya mengikuti {@link HostToggleStyle}. Opasitas diatur, kembali
     * solid saat kursor di atasnya. Gaya {@link HostToggleStyle#HOVER_REVEAL} baru tampil saat kursor mendekati tepi.
     */
    static final class Toggle extends JComponent {
        private static final int ARC = 14;

        Runnable onClick = () -> { };
        int opacity = AppConfig.DEFAULT_OPACITY;
        private HostToggleStyle style = HostToggleStyle.EDGE_CIRCLE;
        private boolean hover;
        private boolean open;
        private boolean near; // kursor di revealZone (hanya untuk HOVER_REVEAL)
        private java.awt.Rectangle revealZone = new java.awt.Rectangle(); // koordinat parent
        private final java.awt.event.AWTEventListener proximity = this::onPointer;

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

        @Override
        public void addNotify() {
            super.addNotify();
            Toolkit.getDefaultToolkit().addAWTEventListener(proximity,
                    AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        }

        @Override
        public void removeNotify() {
            Toolkit.getDefaultToolkit().removeAWTEventListener(proximity);
            super.removeNotify();
        }

        /** @param percent {@value AppConfig#MIN_OPACITY}..100; di luar itu dibatasi */
        void setOpacity(int percent) {
            opacity = Math.max(AppConfig.MIN_OPACITY, Math.min(100, percent));
            repaint();
        }

        void setStyle(HostToggleStyle style) {
            this.style = style;
            near = false;
            repaint();
        }

        HostToggleStyle style() {
            return style;
        }

        /**
         * Pasang posisi sesuai gaya (lihat {@link HostToggleStyle#bounds}).
         *
         * @param area area terminal yang terlihat, dalam koordinat parent tombol ini
         */
        void place(java.awt.Rectangle area, int lineX, boolean openLeft) {
            setBounds(style.bounds(area, lineX, openLeft));
            revealZone = HostToggleStyle.revealZone(area);
        }

        void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        void setOpen(boolean open) {
            this.open = open;
            setToolTipText(I18n.t(open ? "drawer.hide" : "drawer.show"));
            repaint();
        }

        float alpha() {
            if (style.floating() == HostToggleStyle.HOVER_REVEAL) {
                return hover || near ? 1f : 0f;
            }
            return hover ? 1f : opacity / 100f;
        }

        private void onPointer(AWTEvent e) {
            if (style.floating() != HostToggleStyle.HOVER_REVEAL || !isShowing() || getParent() == null
                    || !(e instanceof MouseEvent me) || !(e.getSource() instanceof Component c)) {
                return;
            }
            boolean inside = SwingUtilities.getWindowAncestor(c) == SwingUtilities.getWindowAncestor(this)
                    && me.getID() != MouseEvent.MOUSE_EXITED
                    && revealZone.contains(SwingUtilities.convertPoint(c, me.getPoint(), getParent()));
            if (inside != near) {
                near = inside;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            float alpha = alpha();
            if (alpha <= 0f) {
                return;
            }
            var g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                int w = getWidth();
                int h = getHeight();
                switch (style.floating()) {
                    case EDGE_TAB -> {
                        g2.setColor(buttonColor());
                        // sisi kiri sengaja di luar komponen supaya hanya sudut kanan yang membulat
                        g2.fillRoundRect(-ARC, 0, w + ARC, h, ARC, ARC);
                        g2.setColor(borderColor());
                        g2.drawRoundRect(-ARC, 0, w + ARC - 1, h - 1, ARC, ARC);
                        Icon icon = (open ? AppIcon.ANGLES_LEFT : AppIcon.ANGLES_RIGHT).icon();
                        icon.paintIcon(this, g2, (w - icon.getIconWidth()) / 2 - 1, (h - icon.getIconHeight()) / 2);
                    }
                    case GRABBER -> {
                        if (hover) {
                            g2.setColor(buttonColor());
                            g2.fillRoundRect(0, 0, w - 2, h, 10, 10);
                            g2.setColor(borderColor());
                            g2.drawRoundRect(0, 0, w - 3, h - 1, 10, 10);
                            chevron(g2, (w - 2) / 2f, h / 2f, foreground());
                        } else {
                            // garis tipis samar: lebih samar dari opasitas pilihan user, supaya benar-benar minimal
                            g2.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.55f));
                            g2.setColor(foreground());
                            g2.fillRoundRect(3, (h - 38) / 2, 4, 38, 4, 4);
                        }
                    }
                    case HOVER_REVEAL -> {
                        g2.setColor(hover ? hoverColor() : buttonColor());
                        g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
                        g2.setColor(borderColor());
                        g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                        chevron(g2, w / 2f, h / 2f, foreground());
                    }
                    case CORNER -> {
                        int d = w - 8;
                        shadow(g2, 4, 4, d);
                        Color accent = UIManager.getColor("Component.accentColor");
                        g2.setColor(accent != null ? accent : new Color(0xE2733A));
                        g2.fillOval(4, 3, d, d);
                        chevron(g2, 4 + d / 2f, 3 + d / 2f, Color.WHITE);
                    }
                    default -> { // EDGE_CIRCLE
                        int d = w - 6;
                        shadow(g2, 3, 3, d);
                        g2.setColor(hover ? hoverColor() : buttonColor());
                        g2.fillOval(3, 2, d, d);
                        g2.setColor(borderColor());
                        g2.drawOval(3, 2, d - 1, d - 1);
                        chevron(g2, 3 + d / 2f, 2 + d / 2f, foreground());
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        /** Bayangan lembut di bawah lingkaran berdiameter {@code d} yang digambar di (x, y - 1). */
        private static void shadow(Graphics2D g2, int x, int y, int d) {
            var old = g2.getComposite();
            float base = old instanceof AlphaComposite ac ? ac.getAlpha() : 1f;
            g2.setColor(Color.BLACK);
            for (int i = 3; i >= 1; i--) {
                g2.setComposite(AlphaComposite.SrcOver.derive(base * 0.10f));
                g2.fillOval(x - i, y - i + 1, d + 2 * i, d + 2 * i);
            }
            g2.setComposite(old);
        }

        /** Satu chevron berpusat di (cx, cy): menunjuk ke kiri saat panel terbuka, ke kanan saat tertutup. */
        private void chevron(Graphics2D g2, float cx, float cy, Color color) {
            float s = 3.5f;
            float dir = open ? 1f : -1f;
            var path = new java.awt.geom.Path2D.Float();
            path.moveTo(cx + dir * s * 0.5f, cy - s);
            path.lineTo(cx - dir * s * 0.5f, cy);
            path.lineTo(cx + dir * s * 0.5f, cy + s);
            g2.setColor(color);
            g2.setStroke(new java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }

        private static Color buttonColor() {
            Color c = UIManager.getColor("Button.background");
            return c != null ? c : Color.DARK_GRAY;
        }

        private static Color hoverColor() {
            Color c = UIManager.getColor("Button.hoverBackground");
            return c != null ? c : buttonColor().brighter();
        }

        private static Color foreground() {
            Color c = UIManager.getColor("Label.foreground");
            return c != null ? c : Color.LIGHT_GRAY;
        }
    }
}
