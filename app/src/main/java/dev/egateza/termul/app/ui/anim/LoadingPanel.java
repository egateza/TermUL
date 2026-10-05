package dev.egateza.termul.app.ui.anim;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Layar "Menghubungkan…": animasi + pesan di tengah. Isinya baru muncul setelah {@value #DELAY_MS} ms, jadi koneksi
 * yang cepat langsung berganti ke terminal tanpa kedip loading sekejap. Timer hanya berjalan selama panel terpasang.
 * EDT.
 */
public final class LoadingPanel extends JPanel {

    static final int DELAY_MS = 300;

    private static AnimationChoice current = new AnimationChoice.Fixed(AnimationKind.PACMAN); // EDT; pilihan di menu
    private static AnimationKind last; // EDT; animasi panel terakhir, supaya pilihan acak tidak berulang

    private final String message;
    private final Optional<AnimationKind> kind;
    private final String who;
    private final Timer reveal = new Timer(DELAY_MS, e -> reveal());
    private AnimationView view; // EDT; null sebelum reveal atau tanpa animasi
    private boolean revealed;   // EDT

    /** Animasi untuk panel baru (pilihan user di menu Pengaturan). EDT. */
    public static void use(AnimationChoice choice) {
        current = choice;
    }

    /**
     * Panel dengan animasi sesuai pilihan menu; kalau acak, dipilih ulang untuk tiap panel.
     *
     * @param message pesan di bawah animasi (juga nama aksesibel)
     * @param who     prompt untuk animasi mengetik, lihat {@link AnimationKind#promptFor}
     */
    public LoadingPanel(String message, String who) {
        this(message, next(), who);
    }

    private static Optional<AnimationKind> next() {
        var kind = current.pick(ThreadLocalRandom.current(), last);
        kind.ifPresent(k -> last = k);
        return kind;
    }

    LoadingPanel(String message, Optional<AnimationKind> kind, String who) {
        super(new GridBagLayout());
        this.message = message;
        this.kind = kind;
        this.who = who;
        reveal.setRepeats(false);
        getAccessibleContext().setAccessibleName(message);
    }

    /** Tampilkan animasi + pesan (dipanggil timer setelah {@value #DELAY_MS} ms). */
    void reveal() {
        if (revealed) {
            return;
        }
        revealed = true;
        var c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        if (kind.isPresent()) {
            view = new AnimationView(Animation.Size.LARGE, kind.get().create(Animation.Size.LARGE, who));
            view.getAccessibleContext().setAccessibleName(message);
            add(view, c);
            c.gridy = 1;
            c.insets = new Insets(12, 0, 0, 0);
        }
        add(new JLabel(message, SwingConstants.CENTER), c);
        revalidate();
        repaint();
    }

    boolean isRevealed() {
        return revealed;
    }

    AnimationView view() {
        return view;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!revealed) {
            reveal.start();
        }
    }

    @Override
    public void removeNotify() {
        reveal.stop();
        super.removeNotify();
    }
}
