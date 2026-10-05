package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.monitor.HostStatusPanel;
import dev.egateza.termul.app.ui.anim.Animation;
import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.app.ui.anim.AnimationKind;
import dev.egateza.termul.app.ui.anim.AnimationView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Panel paling bawah window, selalu tampil: animasi kecil pilihan user di tengah dan tanggal/waktu mesin ini di
 * kanan. Timer jam dan rotasi animasi acak hanya berjalan selama panel terpasang. EDT.
 */
public final class BottomBar extends JPanel {

    /** Selang ganti animasi saat pilihan "Acak". */
    static final int RANDOM_ROTATE_MS = 5 * 60 * 1000;

    private final JLabel clock = new JLabel();
    private final AnimationView animation = new AnimationView(Animation.Size.COMPACT,
            AnimationKind.PACMAN.create(Animation.Size.COMPACT, null));
    private final Timer clockTimer = new Timer(1000, e -> updateClock());
    private final Timer rotate = new Timer(RANDOM_ROTATE_MS, e -> pickRandom());
    private AnimationChoice choice = new AnimationChoice.Fixed(AnimationKind.PACMAN); // EDT
    private AnimationKind shown = AnimationKind.PACMAN;                              // EDT

    public BottomBar() {
        super(new BorderLayout(10, 0));
        animation.getAccessibleContext().setAccessibleName(I18n.t("main.menu.settings.statusAnimation"));
        add(animation, BorderLayout.CENTER);
        var right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(HostStatusPanel.verticalDivider(16)); // pemisah di kiri jam
        right.add(Box.createHorizontalStrut(8));
        right.add(clock);
        add(right, BorderLayout.EAST);
        // penyeimbang tak terlihat selebar bagian jam, supaya animasi tepat di tengah panel (bukan di tengah sisa ruang)
        add(new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(right.getPreferredSize().width, 0);
            }
        }, BorderLayout.WEST);
        updateClock();
    }

    /** Ganti animasi; {@link AnimationChoice.Randomized} memilih acak sekarang lalu berganti tiap 5 menit. */
    public void setAnimation(AnimationChoice choice) {
        this.choice = choice;
        rotate.stop();
        switch (choice) {
            case AnimationChoice.Off _ -> animation.setVisible(false);
            case AnimationChoice.Randomized _ -> {
                pickRandom();
                if (isDisplayable()) {
                    rotate.start();
                }
            }
            case AnimationChoice.Fixed(var kind) -> show(kind);
        }
    }

    AnimationView animationView() {
        return animation;
    }

    AnimationKind shown() {
        return animation.isVisible() ? shown : null;
    }

    boolean rotating() {
        return rotate.isRunning();
    }

    private void pickRandom() {
        choice.pick(ThreadLocalRandom.current(), shown).ifPresent(this::show);
    }

    private void show(AnimationKind kind) {
        shown = kind;
        animation.setAnimation(kind.create(Animation.Size.COMPACT, null));
        animation.setVisible(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        clockTimer.start();
        if (choice instanceof AnimationChoice.Randomized) {
            rotate.start();
        }
    }

    @Override
    public void removeNotify() {
        clockTimer.stop();
        rotate.stop();
        super.removeNotify();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        var line = UIManager.getColor("Component.borderColor");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, line != null ? line : getBackground().darker()),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
    }

    private void updateClock() {
        var now = ZonedDateTime.now();
        clock.setText(clock(now, Locale.forLanguageTag(I18n.current())));
        clock.setToolTipText(I18n.t("monitor.clock.tooltip", zone(now)));
    }

    /** @return mis. {@code Kamis, 01 Okt 2026  14:23:05 WIB} */
    static String clock(ZonedDateTime time, Locale locale) {
        return DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy  HH:mm:ss z", locale).format(time);
    }

    /** @return mis. {@code Asia/Jakarta (UTC+07:00)} */
    static String zone(ZonedDateTime time) {
        ZoneId id = time.getZone();
        String offset = time.getOffset().getTotalSeconds() == 0 ? "+00:00" : time.getOffset().getId();
        return id.getId() + " (UTC" + offset + ")";
    }
}
