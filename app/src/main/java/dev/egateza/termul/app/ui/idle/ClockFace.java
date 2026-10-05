package dev.egateza.termul.app.ui.idle;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.Component;
import java.awt.Font;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Jam besar (jam:menit) dengan tanggal di bawahnya, untuk layar beranda dan layar idle. Timer hanya berjalan selama
 * komponen terpasang. EDT.
 */
final class ClockFace extends JPanel {

    private final float timeScale;
    private final JLabel time = new JLabel();
    private final JLabel date = new JLabel();
    private final Timer timer = new Timer(1000, e -> update());

    /** @param timeScale ukuran jam relatif terhadap font label (mis. 4 = empat kali) */
    ClockFace(float timeScale) {
        this.timeScale = timeScale;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        time.setAlignmentX(Component.CENTER_ALIGNMENT);
        date.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(time);
        add(date);
        applyFonts();
        update();
    }

    static String time(ZonedDateTime now) {
        return now.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    static String date(ZonedDateTime now, Locale locale) {
        return now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale));
    }

    private void update() {
        var now = ZonedDateTime.now();
        time.setText(time(now));
        date.setText(date(now, Locale.forLanguageTag(I18n.current())));
    }

    /** Font diturunkan dari font label tema, supaya ikut pilihan font aplikasi. */
    private void applyFonts() {
        if (time == null) {
            return; // updateUI() dari konstruktor JPanel, sebelum field terisi
        }
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = time.getFont();
        }
        time.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() * timeScale));
        date.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() * 1.3f));
        date.setForeground(UIManager.getColor("Label.disabledForeground"));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyFonts();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        update();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }
}
