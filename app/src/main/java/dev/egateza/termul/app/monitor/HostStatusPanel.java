package dev.egateza.termul.app.monitor;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.config.AppConfig;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/**
 * "Host status" di kanan baris menu: CPU proses dan heap JVM (persen terhadap batas {@code -Xmx}) serta jumlah
 * thread, sebagai grafik, teks, atau keduanya ({@link Mode}). Klik kanan: GC manual atau sembunyikan. Tanggal/waktu
 * ada di panel bawah. EDT.
 */
public final class HostStatusPanel extends JPanel {

    /** Isi panel; disimpan di config sebagai {@link #id}. */
    public enum Mode {
        ALL(AppConfig.HOST_STATUS_ALL),
        GRAPH(AppConfig.HOST_STATUS_GRAPH),
        TEXT(AppConfig.HOST_STATUS_TEXT);

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String label() {
            return I18n.t("main.menu.settings.hostStatus.mode." + id);
        }

        /** @return mode dengan id tersebut, atau {@link #ALL} kalau tidak dikenal */
        public static Mode fromId(String id) {
            for (var m : values()) {
                if (m.id.equals(id)) {
                    return m;
                }
            }
            return ALL;
        }
    }

    private static final int GRAPH_WIDTH = 80;
    private static final int GRAPH_HEIGHT = 16;
    private static final Color TEXT_WARN = new Color(0xE0A000);
    private static final Color TEXT_CRITICAL = new Color(0xE5484D);

    private final Sparkline cpuGraph;
    private final Sparkline heapGraph;
    private final JLabel cpuCaption = caption(I18n.t("monitor.cpu"));
    private final JLabel heapCaption = caption(I18n.t("monitor.heap"));
    private final JLabel cpuPercent = new JLabel();  // mode teks
    private final JLabel heapPercent = new JLabel(); // mode teks
    private final JLabel heapText = new JLabel();
    private final JLabel threadsText = new JLabel();
    private final JSeparator divider = verticalDivider(GRAPH_HEIGHT);
    private Mode mode; // EDT

    /** @param onHide dipanggil saat user memilih "Sembunyikan" dari menu klik kanan */
    public HostStatusPanel(ResourceMonitor monitor, Runnable onHide) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        getAccessibleContext().setAccessibleName(I18n.t("monitor.hostStatus"));
        cpuGraph = new Sparkline(monitor.cpuHistory(), GRAPH_WIDTH, GRAPH_HEIGHT);
        heapGraph = new Sparkline(monitor.heapHistory(), GRAPH_WIDTH, GRAPH_HEIGHT);

        var popup = new JPopupMenu();
        var gc = new JMenuItem(I18n.t("monitor.runGc"));
        gc.addActionListener(e -> monitor.runGc());
        var hide = new JMenuItem(I18n.t("monitor.hide.hostStatus"));
        hide.addActionListener(e -> onHide.run());
        popup.add(gc);
        popup.addSeparator();
        popup.add(hide);
        setComponentPopupMenu(popup);
        for (var c : new JComponent[] {cpuCaption, heapCaption, cpuGraph, heapGraph, cpuPercent, heapPercent, heapText,
                threadsText, divider}) {
            c.setInheritsPopupMenu(true);
        }

        setMode(Mode.ALL);
        monitor.addView(this::render);
        if (monitor.last() == null) {
            render(null);
        }
    }

    /** Ganti isi: grafik + teks, grafik saja, atau teks saja. */
    public void setMode(Mode mode) {
        if (mode == this.mode) {
            return;
        }
        this.mode = mode;
        removeAll();
        add(cpuCaption);
        add(mode == Mode.TEXT ? cpuPercent : cpuGraph);
        add(Box.createHorizontalStrut(12));
        add(heapCaption);
        if (mode == Mode.TEXT) {
            add(heapPercent);
        } else {
            add(heapGraph);
        }
        if (mode != Mode.GRAPH) {
            add(Box.createHorizontalStrut(6));
            add(heapText);
            add(Box.createHorizontalStrut(12));
            add(threadsText);
        }
        add(Box.createHorizontalStrut(10));
        add(divider); // pemisah dari tombol di kanannya (terang/gelap, update)
        add(Box.createHorizontalStrut(4));
        revalidate();
        repaint();
    }

    public Mode mode() {
        return mode;
    }

    /** Di baris menu (BoxLayout) jangan melebar mengisi sisa ruang. */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /** Garis pemisah vertikal setinggi {@code height} px (tidak memanjang mengisi tinggi baris). */
    public static JSeparator verticalDivider(int height) {
        var separator = new JSeparator(SwingConstants.VERTICAL);
        var size = new Dimension(separator.getPreferredSize().width, height);
        separator.setPreferredSize(size);
        separator.setMaximumSize(size);
        return separator;
    }

    private static JLabel caption(String text) {
        var label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        return label;
    }

    private void render(JvmSample s) {
        String tip = ResourceMonitor.tooltip(s);
        for (var c : new JComponent[] {cpuGraph, heapGraph, cpuPercent, heapPercent, heapText, threadsText}) {
            c.setToolTipText(tip);
        }
        if (s == null) {
            cpuGraph.update("–", Level.NORMAL);
            heapGraph.update("–", Level.NORMAL);
            cpuPercent.setText("–");
            heapPercent.setText("–");
            heapText.setText(" ");
            threadsText.setText(" ");
            return;
        }
        cpuGraph.update(ResourceMonitor.percent(s.processCpu()), Level.of(s.processCpu()));
        heapGraph.update(ResourceMonitor.percent(s.heapRatio()), Level.of(s.heapRatio()));
        setLevelText(cpuPercent, ResourceMonitor.percent(s.processCpu()), Level.of(s.processCpu()));
        setLevelText(heapPercent, ResourceMonitor.percent(s.heapRatio()), Level.of(s.heapRatio()));
        heapText.setText(ResourceMonitor.size(s.heapUsed()) + " / " + ResourceMonitor.size(s.heapLimit()));
        threadsText.setText(I18n.t("monitor.threads", String.valueOf(s.threads())));
    }

    /** Teks persen; warna ikut tema saat normal, kuning/merah saat mendekati batas (seperti grafik). */
    private static void setLevelText(JLabel label, String text, Level level) {
        label.setText(text);
        label.setForeground(switch (level) {
            case NORMAL -> null;
            case WARN -> TEXT_WARN;
            case CRITICAL -> TEXT_CRITICAL;
        });
    }

    /** @return true kalau grafik CPU / teks persen CPU / jumlah thread sedang dipasang (untuk test) */
    boolean showsGraph() {
        return cpuGraph.getParent() == this;
    }

    boolean showsPercentText() {
        return cpuPercent.getParent() == this;
    }

    boolean showsThreads() {
        return threadsText.getParent() == this;
    }
}
