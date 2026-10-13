package dev.egateza.termul.app.update;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.update.UpdateHistory;
import dev.egateza.termul.update.UpdateProtocol;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bantuan → Yang baru: catatan rilis dari {@link UpdateHistory#MAX_ENTRIES} update terakhir yang sudah dipakai di
 * komputer ini. Kiri daftar rilis (bulan + versi), kanan catatan rilis versi terpilih. Tidak modal, supaya bisa
 * dibaca sambil bekerja. Riwayat dibaca pemanggil di luar EDT; dialog ini hanya UI (EDT).
 */
public final class WhatsNewDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(WhatsNewDialog.class);

    private final JList<UpdateHistory.Entry> releases;
    /** Milik dialog ini saja: stylesheet bawaan HTMLEditorKit dipakai bersama semua JEditorPane. */
    private final StyleSheet css = new StyleSheet();
    private final JEditorPane page = new JEditorPane();
    private final JScrollPane pageScroll = new JScrollPane(page);
    private final Locale locale = Locale.forLanguageTag(I18n.current());

    /**
     * @param history riwayat, terbaru di depan
     * @param running versi yang sedang berjalan; null = build dev
     */
    public WhatsNewDialog(Window owner, List<UpdateHistory.Entry> history, ReleaseVersion running) {
        super(owner, I18n.t("whatsNew.title"), ModalityType.MODELESS);
        releases = new JList<>(history.toArray(UpdateHistory.Entry[]::new));
        releases.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        releases.setCellRenderer(new ReleaseRenderer());
        releases.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showRelease(releases.getSelectedValue());
            }
        });

        page.setEditable(false);
        css.addStyleSheet(new HTMLEditorKit().getStyleSheet());
        styleSheet();
        page.setEditorKit(new HTMLEditorKit() {
            @Override
            public StyleSheet getStyleSheet() {
                return css;
            }
        });
        page.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        page.setFont(UIManager.getFont("Label.font"));
        page.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        page.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                browse(e.getURL().toString());
            }
        });
        pageScroll.setBorder(BorderFactory.createEmptyBorder());
        pageScroll.getVerticalScrollBar().setUnitIncrement(16);

        var content = new JPanel(new BorderLayout());
        if (history.isEmpty()) {
            showEmpty(running);
        } else {
            var listScroll = new JScrollPane(releases);
            listScroll.setPreferredSize(new Dimension(190, 0));
            listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Component.borderColor")));
            content.add(listScroll, BorderLayout.WEST);
            releases.setSelectedIndex(0);
        }
        content.add(pageScroll, BorderLayout.CENTER);
        setContentPane(content);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(860, 600);
        setMinimumSize(new Dimension(520, 360));
        setLocationRelativeTo(owner);
    }

    private void showRelease(UpdateHistory.Entry entry) {
        if (entry == null) {
            return;
        }
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
                .format(entry.appliedAt().atZone(ZoneId.systemDefault()));
        String github = UpdateProtocol.RELEASES.resolve("tag/" + entry.version().tag()).toString();
        String notes = entry.notes().isBlank()
                ? "<p class=\"muted\">" + ReleaseNotesHtml.escape(I18n.t("whatsNew.noNotes")) + "</p>"
                : ReleaseNotesHtml.toHtml(entry.notes());
        setPage("<h1>" + ReleaseNotesHtml.escape(I18n.t("whatsNew.heading", entry.version().toString())) + "</h1>"
                + "<p class=\"meta\">" + ReleaseNotesHtml.escape(date) + " &bull; "
                + ReleaseNotesHtml.link(github, I18n.t("whatsNew.viewOnGitHub")) + "</p>"
                + notes);
    }

    private void showEmpty(ReleaseVersion running) {
        String version = running == null ? "dev" : running.toString();
        setPage("<h1>" + ReleaseNotesHtml.escape(I18n.t("whatsNew.empty")) + "</h1>"
                + "<p>" + ReleaseNotesHtml.escape(I18n.t("whatsNew.empty.detail")) + "</p>"
                + "<p class=\"meta\">" + ReleaseNotesHtml.escape(I18n.t("whatsNew.running", version)) + " &bull; "
                + ReleaseNotesHtml.link(UpdateProtocol.RELEASES.toString(), I18n.t("whatsNew.viewOnGitHub")) + "</p>");
    }

    private void setPage(String body) {
        page.setText("<html><body>" + body + "</body></html>");
        page.setCaretPosition(0);
    }

    /** Warna dari tema yang aktif (dialog dibuat ulang setiap dibuka, jadi ganti tema cukup buka ulang). */
    private void styleSheet() {
        Color fg = UIManager.getColor("Label.foreground");
        Color muted = UIManager.getColor("Label.disabledForeground");
        Color link = UIManager.getColor("Component.linkColor");
        Color codeBg = UIManager.getColor("TextField.background");
        Font font = UIManager.getFont("Label.font");
        css.addRule("body { color: " + hex(fg) + "; font-family: '" + font.getFamily() + "'; font-size: "
                + font.getSize() + "pt; }");
        css.addRule("h1 { font-size: " + (font.getSize() + 10) + "pt; margin-top: 0; margin-bottom: 4px; }");
        css.addRule("h2 { font-size: " + (font.getSize() + 5) + "pt; margin-top: 14px; margin-bottom: 4px; }");
        css.addRule("h3 { font-size: " + (font.getSize() + 3) + "pt; margin-top: 12px; margin-bottom: 4px; }");
        css.addRule("h4 { font-size: " + (font.getSize() + 1) + "pt; margin-top: 10px; margin-bottom: 4px; }");
        css.addRule("p { margin-top: 4px; margin-bottom: 6px; }");
        css.addRule("ul { margin-top: 2px; margin-left: 18px; }");
        css.addRule("li { margin-bottom: 4px; }");
        css.addRule(".meta { color: " + hex(muted) + "; margin-bottom: 18px; }");
        css.addRule(".muted { color: " + hex(muted) + "; }");
        css.addRule("a { color: " + hex(link) + "; }");
        css.addRule("code { font-family: monospaced; font-size: " + font.getSize() + "pt; background-color: "
                + hex(codeBg) + "; }");
    }

    private static String hex(Color c) {
        return c == null ? "inherit" : String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    private void browse(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            return;
        }
        Thread.ofVirtual().name("open-release-notes-link").start(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("browser tidak tersedia");
                }
                Desktop.getDesktop().browse(uri);
            } catch (IOException | RuntimeException e) {
                log.warn("Link catatan rilis tidak bisa dibuka: {}", e.toString());
            }
        });
    }

    /** Dua baris seperti daftar rilis PowerToys: bulan + tahun, lalu nomor versi yang lebih kecil. */
    private final class ReleaseRenderer extends JPanel implements ListCellRenderer<UpdateHistory.Entry> {

        private final JLabel month = new JLabel();
        private final JLabel version = new JLabel();
        private final DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", locale);

        ReleaseRenderer() {
            super(new GridLayout(2, 1, 0, 2));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            Font base = UIManager.getFont("Label.font");
            month.setFont(base.deriveFont(base.getSize2D() + 1f));
            version.setFont(base.deriveFont(base.getSize2D() - 1f));
            add(month);
            add(version);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends UpdateHistory.Entry> list,
                                                      UpdateHistory.Entry value, int index, boolean selected,
                                                      boolean focused) {
            String m = monthFormat.format(value.appliedAt().atZone(ZoneId.systemDefault()));
            month.setText(m.substring(0, 1).toUpperCase(locale) + m.substring(1));
            version.setText(value.version().toString());
            Color fg = selected ? list.getSelectionForeground() : list.getForeground();
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            month.setForeground(fg);
            version.setForeground(selected ? fg : UIManager.getColor("Label.disabledForeground"));
            return this;
        }
    }
}
