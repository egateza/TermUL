package dev.egateza.termul.app.ui.idle;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.anim.AnimationChoice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Layar idle: dipasang sebagai glass pane window utama dan menutupi seluruh isinya (menu, daftar host, terminal)
 * dengan jam dan animasi. Sesi SSH tetap berjalan di belakangnya. Klik atau gerakan mouse menutupnya; event mouse
 * ditelan glass pane sehingga tidak sampai ke terminal. Keyboard ditangani {@link IdleState} di window utama. EDT.
 */
public final class IdleOverlay extends JComponent {

    /** Gerakan mouse sekecil ini (px) belum membangunkan layar (meja tersenggol, sensor mouse goyang). */
    static final int MOVE_THRESHOLD = 8;

    private final AnimationSlot animation = new AnimationSlot(3);
    private final JLabel info = new JLabel();
    private Point moveStart; // posisi mouse pertama setelah layar tampil

    /** @param onWake dipanggil saat user membangunkan layar dengan mouse */
    public IdleOverlay(Runnable onWake) {
        setLayout(new GridBagLayout());
        setOpaque(true);
        var column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        var clock = new ClockFace(6f);
        clock.setAlignmentX(Component.CENTER_ALIGNMENT);
        animation.view().setAlignmentX(Component.CENTER_ALIGNMENT);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);
        var hint = new JLabel(I18n.t("idle.hint"));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(clock);
        column.add(Box.createVerticalStrut(28));
        column.add(animation.view());
        column.add(Box.createVerticalStrut(28));
        column.add(info);
        column.add(Box.createVerticalStrut(6));
        column.add(hint);
        add(column);
        var mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                e.consume();
                onWake.run();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (moveStart == null) {
                    moveStart = e.getPoint();
                } else if (moveStart.distance(e.getPoint()) > MOVE_THRESHOLD) {
                    onWake.run();
                }
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                e.consume(); // jangan menggulir terminal di belakang layar
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        updateUI(); // JComponent tidak memanggilnya sendiri: warna teks sekunder dari tema
        setVisible(false);
    }

    public void setAnimation(AnimationChoice choice) {
        animation.setChoice(choice);
    }

    /** Tampilkan layar idle. @param openTabs jumlah tab terminal yang tetap berjalan di belakang */
    public void showIdle(int openTabs) {
        info.setText(openTabs > 0 ? I18n.t("idle.tabs", String.valueOf(openTabs)) : "");
        info.setVisible(openTabs > 0);
        animation.reshuffle();
        moveStart = null;
        setVisible(true);
        requestFocusInWindow(); // fokus keluar dari terminal; kursor terminal tidak berkedip di belakang
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (info != null) {
            info.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color bg = UIManager.getColor("Panel.background");
        g.setColor(bg != null ? bg : Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    AnimationSlot animationSlot() {
        return animation;
    }

    String infoText() {
        return info.isVisible() ? info.getText() : "";
    }
}
