package dev.egateza.termul.app.ui.idle;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * Layar beranda saat belum ada tab: jam, tanggal, tombol host "Terakhir dipakai" dan "Favorit", animasi pilihan user,
 * dan petunjuk shortcut. EDT.
 */
public final class HomeScreen extends JPanel {

    /** Batas tombol favorit supaya layar tidak penuh; sisanya tetap ada di daftar host. */
    static final int MAX_FAVORITES = 8;

    private final Consumer<HostProfile> onOpen;
    private final AnimationSlot animation = new AnimationSlot(2);
    private final JPanel recent = chipRow();
    private final JPanel favorites = chipRow();
    private final JLabel recentTitle = sectionTitle(I18n.t("home.recent"));
    private final JLabel favoritesTitle = sectionTitle(I18n.t("home.favorites"));
    private final JLabel hint = new JLabel("", SwingConstants.CENTER);

    /** @param onOpen dipanggil saat tombol host diklik (membuka terminal) */
    public HomeScreen(Consumer<HostProfile> onOpen) {
        super(new GridBagLayout()); // isi di tengah, vertikal maupun horizontal
        this.onOpen = onOpen;
        var column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(centered(new ClockFace(4f)));
        column.add(Box.createVerticalStrut(18));
        column.add(centered(animation.view()));
        column.add(Box.createVerticalStrut(18));
        for (var c : new Component[] {recentTitle, recent, favoritesTitle, favorites}) {
            column.add(centered(c));
        }
        column.add(Box.createVerticalStrut(18));
        column.add(centered(hint));
        add(column);
        setSnapshot(ProfileSnapshot.empty());
    }

    /** Isi ulang tombol host dari snapshot profil. */
    public void setSnapshot(ProfileSnapshot snapshot) {
        fill(recent, recentTitle, snapshot.recentProfiles());
        fill(favorites, favoritesTitle, snapshot.favoriteProfiles().stream().limit(MAX_FAVORITES).toList());
        revalidate();
        repaint();
    }

    /** Petunjuk di bawah (HTML, mis. shortcut host baru / cari). */
    public void setHint(String html) {
        hint.setText(html);
    }

    public void setAnimation(AnimationChoice choice) {
        animation.setChoice(choice);
    }

    /** Animasi acak: ganti setiap layar beranda tampil lagi (tab terakhir ditutup). */
    public void reshuffle() {
        animation.reshuffle();
    }

    List<String> recentNames() {
        return names(recent);
    }

    List<String> favoriteNames() {
        return names(favorites);
    }

    boolean favoritesVisible() {
        return favorites.isVisible();
    }

    private void fill(JPanel row, JLabel title, List<HostProfile> profiles) {
        row.removeAll();
        for (var p : profiles) {
            var button = new JButton(p.name());
            button.putClientProperty("JButton.buttonType", "roundRect");
            button.setToolTipText(p.username() == null || p.username().isBlank()
                    ? p.host() : p.username() + "@" + p.host());
            button.addActionListener(e -> onOpen.accept(p));
            row.add(button);
        }
        row.setVisible(!profiles.isEmpty());
        title.setVisible(!profiles.isEmpty());
    }

    private static List<String> names(JPanel row) {
        var list = new java.util.ArrayList<String>();
        for (var c : row.getComponents()) {
            if (c instanceof JButton b) {
                list.add(b.getText());
            }
        }
        return list;
    }

    private static JPanel chipRow() {
        var row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4)) {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize(); // BoxLayout: jangan melebar memenuhi kolom
            }
        };
        row.setOpaque(false);
        return row;
    }

    private static JLabel sectionTitle(String text) {
        var label = new JLabel(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                setForeground(UIManager.getColor("Label.disabledForeground"));
                setFont(getFont().deriveFont(Font.BOLD));
            }
        };
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 0, 2, 0));
        return label;
    }

    private static Component centered(Component c) {
        if (c instanceof javax.swing.JComponent j) {
            j.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        return c;
    }
}
