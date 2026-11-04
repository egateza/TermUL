package dev.egateza.termul.app.tunnel;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.ssh.tunnel.TunnelStatus;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Bagian tampilan Port forwarding yang dipakai dialog daftar dan form (prototipe "TermUL Desktop"). EDT. */
final class TunnelUi {

    /** Ikon panah bolak-balik (header, chip di panel bawah). */
    static final String ARROWS = "M4 8h13M13 4l4 4-4 4M20 16H7M11 12l-4 4 4 4";
    static final String ARROW_RIGHT = "M3 12h17M15 7l5 5-5 5";

    private TunnelUi() {
    }

    // --- warna status (prototipe: hijau aktif, kuning starting, abu stopped, merah error) ---

    static Color statusColor(TunnelStatus status) {
        return switch (status) {
            case TunnelStatus.Active _ -> SnippetUi.success();
            case TunnelStatus.Starting _ -> SnippetUi.variable();
            case TunnelStatus.Stopped _ -> stopped();
            case TunnelStatus.Failed _ -> SnippetUi.danger();
        };
    }

    static Color stopped() {
        return SnippetUi.mix(SnippetUi.muted(), SnippetUi.panel(), 0.35f);
    }

    /** Label status: titik warna + teks. */
    static JLabel statusLabel(TunnelStatus status, float fontDelta) {
        var l = SnippetUi.label(TunnelText.status(status), SnippetUi.font(fontDelta, Font.PLAIN), SnippetUi.fg());
        l.setIcon(SnippetUi.dot(fontDelta > 0 ? 8 : 7, () -> statusColor(status)));
        l.setIconTextGap(6);
        return l;
    }

    // --- switch ---

    /**
     * Saklar geser 32×18 (prototipe {@code .sw}): tombol sungguhan, bisa difokus dan ditekan Space.
     *
     * @param on    keadaan saat digambar
     * @param color warna saat menyala
     */
    static JButton switchButton(BooleanSupplier on, Supplier<Color> color, String accessibleName, Runnable toggle) {
        var b = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.45f)); // terkunci (mis. sedang starting)
                }
                int x = (getWidth() - 32) / 2;
                int y = (getHeight() - 18) / 2;
                boolean lit = on.getAsBoolean();
                g2.setColor(lit ? color.get() : SnippetUi.border());
                g2.fillRoundRect(x, y, 32, 18, 18, 18);
                g2.setColor(lit ? SnippetUi.onAccent(color.get()) : SnippetUi.muted());
                g2.fillOval(lit ? x + 16 : x + 2, y + 2, 14, 14);
                if (isFocusOwner()) {
                    g2.setColor(SnippetUi.accent());
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(x - 2, y - 2, 35, 21, 21, 21);
                }
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(38, 24));
        b.setMaximumSize(new Dimension(38, 24));
        b.getAccessibleContext().setAccessibleName(accessibleName);
        b.addActionListener(e -> toggle.run());
        return b;
    }

    /** Saklar + teks; klik teks juga mengubah saklar (prototipe {@code .toggle}). */
    static JPanel toggleRow(String text, BooleanSupplier on, Supplier<Color> color, Runnable toggle) {
        var sw = switchButton(on, color, text, toggle);
        var label = SnippetUi.label(text, SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                sw.doClick(0);
            }
        });
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(sw);
        row.add(javax.swing.Box.createHorizontalStrut(8));
        row.add(label);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    // --- item navigasi (prototipe .nav-item: 28 px, sudut 4 px) ---

    static final class NavItem extends JPanel {
        private final boolean on;
        private boolean hover;

        NavItem(String text, int count, boolean on, Runnable pick) {
            super(new BorderLayout(8, 0));
            this.on = on;
            setOpaque(false);
            setFocusable(true);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setPreferredSize(new Dimension(160, 28));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            add(SnippetUi.label(text, SnippetUi.font(0, Font.PLAIN), on ? SnippetUi.onSelection() : SnippetUi.fg()),
                    BorderLayout.CENTER);
            add(SnippetUi.label(String.valueOf(count), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()),
                    BorderLayout.EAST);
            getAccessibleContext().setAccessibleName(text + " " + count);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    pick.run();
                }
            });
            onKeys(this, pick, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (on || hover || isFocusOwner()) {
                var g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(on ? SnippetUi.selection() : SnippetUi.hover());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isFocusOwner()) {
                    g2.setColor(SnippetUi.accent());
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 7, 7);
                }
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    static void onKeys(JComponent c, Runnable action, int... keys) {
        for (int key : keys) {
            c.getInputMap().put(KeyStroke.getKeyStroke(key, 0), "pick");
        }
        c.getActionMap().put("pick", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // --- bar header / footer ---

    /** Panel bar (header/footer 40/28 px) yang sudutnya ikut membulat dengan bingkai dialog. */
    static final class Bar extends JPanel {
        private final boolean top;
        private final boolean round;

        Bar(boolean top, boolean round, int height) {
            this.top = top;
            this.round = round;
            setOpaque(false);
            setPreferredSize(new Dimension(0, height));
            setBorder(BorderFactory.createMatteBorder(top ? 0 : 1, 0, top ? 1 : 0, 0, SnippetUi.border()));
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SnippetUi.bar());
            int arc = round ? 14 : 0;
            g2.fill(new RoundRectangle2D.Float(0, top ? 0 : -arc, getWidth(), getHeight() + arc, arc, arc));
            g2.dispose();
        }
    }

    /** Header bisa diseret untuk memindah dialog tanpa dekorasi. */
    static void draggable(JComponent handle, Window window) {
        var drag = new MouseAdapter() {
            private Point start;

            @Override
            public void mousePressed(MouseEvent e) {
                start = e.getLocationOnScreen();
                start.translate(-window.getX(), -window.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (start != null) {
                    var p = e.getLocationOnScreen();
                    window.setLocation(p.x - start.x, p.y - start.y);
                }
            }
        };
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
    }

    static SnippetUi.Button closeButton(Runnable close) {
        var b = new SnippetUi.Button(null, SnippetUi.stroke(SnippetUi.CLOSE, 14, SnippetUi::muted, 2),
                SnippetUi.Variant.ICON);
        b.setToolTipText(I18n.t("tunnel.close"));
        b.getAccessibleContext().setAccessibleName(I18n.t("tunnel.close"));
        b.addActionListener(e -> close.run());
        return b;
    }

    // --- konfirmasi hapus (prototipe: kartu 420 px, garis merah, Cancel + "Delete tunnel") ---

    static boolean confirmDelete(Window owner, String name, String text) {
        var dialog = new JDialog(owner, I18n.t("tunnel.delete.title", name), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        boolean[] result = {false};
        var card = new SnippetUi.RoundPanel(new BorderLayout(0, 10), SnippetUi::panel, SnippetUi::danger, 16);
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.add(SnippetUi.label(I18n.t("tunnel.delete.title", name), SnippetUi.font(1, Font.BOLD), SnippetUi.fg()),
                BorderLayout.NORTH);
        card.add(SnippetUi.wrapped(SnippetUi.esc(text), 380, SnippetUi.font(0, Font.PLAIN), SnippetUi.muted()),
                BorderLayout.CENTER);
        var cancel = new SnippetUi.Button(I18n.t("tunnel.cancel"), null, SnippetUi.Variant.DEFAULT);
        var ok = new SnippetUi.Button(I18n.t("tunnel.delete.ok"), null, SnippetUi.Variant.DANGER_FILL);
        cancel.addActionListener(e -> dialog.dispose());
        ok.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        buttons.add(cancel);
        buttons.add(ok);
        card.add(buttons, BorderLayout.SOUTH);
        var root = dialog.getRootPane();
        root.setOpaque(false);
        root.registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.setDefaultButton(cancel);
        SnippetUi.roundWindow(dialog);
        dialog.setContentPane(card);
        dialog.setSize(420, card.getPreferredSize().height);
        SnippetUi.keepSizeAcrossScreens(dialog);
        dialog.setLocationRelativeTo(owner);
        Runnable undim = SnippetUi.dim(owner);
        SwingUtilities.invokeLater(cancel::requestFocusInWindow);
        try {
            dialog.setVisible(true);
        } finally {
            undim.run();
        }
        return result[0];
    }

    /** Ikon panah, untuk chip dan header. */
    static Icon arrows(int size, Supplier<Color> color) {
        return SnippetUi.stroke(ARROWS, size, color, 2.2f);
    }
}
