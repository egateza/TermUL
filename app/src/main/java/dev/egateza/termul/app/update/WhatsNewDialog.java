package dev.egateza.termul.app.update;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.update.UpdateHistory;
import dev.egateza.termul.update.UpdateProtocol;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Bantuan → Yang baru: catatan rilis dari {@link UpdateHistory#MAX_ENTRIES} update terakhir yang sudah dipakai di
 * komputer ini. Kiri daftar rilis (bulan + versi), kanan catatan rilis versi terpilih. Tidak modal, supaya bisa
 * dibaca sambil bekerja. Riwayat dibaca pemanggil di luar EDT; dialog ini hanya UI (EDT).
 */
public final class WhatsNewDialog extends JDialog {

    private final JList<UpdateHistory.Entry> releases;
    private final ReleaseNotesPane page = new ReleaseNotesPane(24);
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
        page.setBody("<h1>" + ReleaseNotesHtml.escape(I18n.t("whatsNew.heading", entry.version().toString())) + "</h1>"
                + "<p class=\"meta\">" + ReleaseNotesHtml.escape(date) + " &bull; "
                + ReleaseNotesHtml.link(github, I18n.t("whatsNew.viewOnGitHub")) + "</p>"
                + notes);
    }

    private void showEmpty(ReleaseVersion running) {
        String version = running == null ? "dev" : running.toString();
        page.setBody("<h1>" + ReleaseNotesHtml.escape(I18n.t("whatsNew.empty")) + "</h1>"
                + "<p>" + ReleaseNotesHtml.escape(I18n.t("whatsNew.empty.detail")) + "</p>"
                + "<p class=\"meta\">" + ReleaseNotesHtml.escape(I18n.t("whatsNew.running", version)) + " &bull; "
                + ReleaseNotesHtml.link(UpdateProtocol.RELEASES.toString(), I18n.t("whatsNew.viewOnGitHub")) + "</p>");
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
