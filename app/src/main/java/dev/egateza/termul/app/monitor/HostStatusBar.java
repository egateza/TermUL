package dev.egateza.termul.app.monitor;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.BorderLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

/**
 * Bar "Host status" di bagian paling bawah window: grafik CPU proses dan heap JVM (persen terhadap batas
 * {@code -Xmx}), jumlah thread, serta tanggal/waktu mesin tempat aplikasi berjalan. Klik kanan: GC manual atau
 * sembunyikan bar. EDT.
 */
public final class HostStatusBar extends JPanel {

    private static final int GRAPH_WIDTH = 110;
    private static final int GRAPH_HEIGHT = 18;

    private final Sparkline cpuGraph;
    private final Sparkline heapGraph;
    private final JLabel heapText = new JLabel();
    private final JLabel threadsText = new JLabel();
    private final JLabel clock = new JLabel();

    /** @param onHide dipanggil saat user memilih "Sembunyikan" dari menu klik kanan */
    public HostStatusBar(ResourceMonitor monitor, Runnable onHide) {
        super(new BorderLayout(10, 0));
        cpuGraph = new Sparkline(monitor.cpuHistory(), GRAPH_WIDTH, GRAPH_HEIGHT);
        heapGraph = new Sparkline(monitor.heapHistory(), GRAPH_WIDTH, GRAPH_HEIGHT);

        var title = new JLabel(I18n.t("monitor.hostStatus"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.WEST);

        var metrics = new JPanel();
        metrics.setOpaque(false);
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.X_AXIS));
        metrics.add(caption(I18n.t("monitor.cpu")));
        metrics.add(cpuGraph);
        metrics.add(Box.createHorizontalStrut(14));
        metrics.add(caption(I18n.t("monitor.heap")));
        metrics.add(heapGraph);
        metrics.add(Box.createHorizontalStrut(6));
        metrics.add(heapText);
        metrics.add(Box.createHorizontalStrut(14));
        metrics.add(threadsText);
        add(metrics, BorderLayout.CENTER);
        add(clock, BorderLayout.EAST);

        var popup = new JPopupMenu();
        var gc = new JMenuItem(I18n.t("monitor.runGc"));
        gc.addActionListener(e -> monitor.runGc());
        var hide = new JMenuItem(I18n.t("monitor.hide.hostStatus"));
        hide.addActionListener(e -> onHide.run());
        popup.add(gc);
        popup.addSeparator();
        popup.add(hide);
        setComponentPopupMenu(popup);
        for (var c : new JComponent[] {title, metrics, cpuGraph, heapGraph, heapText, threadsText, clock}) {
            c.setInheritsPopupMenu(true);
        }

        updateClock();
        monitor.addView(this::render);
        if (monitor.last() == null) {
            render(null);
        }
    }

    private static JLabel caption(String text) {
        var label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        return label;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        var line = UIManager.getColor("Component.borderColor");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, line != null ? line : getBackground().darker()),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
    }

    private void render(JvmSample s) {
        updateClock();
        String tip = ResourceMonitor.tooltip(s);
        for (var c : new JComponent[] {cpuGraph, heapGraph, heapText, threadsText}) {
            c.setToolTipText(tip);
        }
        if (s == null) {
            cpuGraph.update("–", Level.NORMAL);
            heapGraph.update("–", Level.NORMAL);
            heapText.setText(" ");
            threadsText.setText(" ");
            return;
        }
        cpuGraph.update(ResourceMonitor.percent(s.processCpu()), Level.of(s.processCpu()));
        heapGraph.update(ResourceMonitor.percent(s.heapRatio()), Level.of(s.heapRatio()));
        heapText.setText(ResourceMonitor.size(s.heapUsed()) + " / " + ResourceMonitor.size(s.heapLimit()));
        threadsText.setText(I18n.t("monitor.threads", String.valueOf(s.threads())));
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
