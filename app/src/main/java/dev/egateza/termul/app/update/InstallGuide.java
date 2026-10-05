package dev.egateza.termul.app.update;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.update.UpdateProtocol;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panduan "pasang ulang installer" di dialog update: link unduhan zip dan halaman rilis, plus langkah memasang.
 * Link hanya dibuka dengan {@link Shortcuts#menuKeyName() Ctrl}+klik (Cmd+klik di Mac), supaya klik biasa saat
 * membaca/menyeleksi teks tidak membuka browser. Hanya link ke {@link UpdateProtocol#RELEASES} yang dibuka.
 */
final class InstallGuide extends JEditorPane {

    private static final Logger log = LoggerFactory.getLogger(InstallGuide.class);
    static final int PREFERRED_WIDTH = 520;

    private final int preferredHeight;

    /**
     * Panduan yang membuka link di browser default.
     *
     * @param onBrowseFailed dipanggil di EDT dengan URL kalau browser tidak bisa dibuka (supaya bisa disalin)
     */
    static InstallGuide withBrowser(ReleaseVersion version, Path installDir, Consumer<URI> onBrowseFailed) {
        return new InstallGuide(version, installDir, uri -> browse(uri, onBrowseFailed));
    }

    /** @param opener membuka URL yang sudah lolos {@link #allowed} (test memakai pengganti browser) */
    InstallGuide(ReleaseVersion version, Path installDir, Consumer<URI> opener) {
        super("text/html", html(version, installDir, Shortcuts.menuKeyName()));
        setEditable(false);
        setOpaque(false);
        putClientProperty(HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        setFont(UIManager.getFont("Label.font"));
        setBorder(BorderFactory.createTitledBorder(I18n.t("update.guide.title")));
        setToolTipText(I18n.t("update.guide.linkHint", Shortcuts.menuKeyName()));
        // tinggi HTML bergantung lebar: hitung sekali untuk lebar tetap, supaya pack() dialog tidak memakai lebar
        // teks yang belum di-wrap (dialog jadi sangat lebar)
        setSize(PREFERRED_WIDTH, Short.MAX_VALUE);
        preferredHeight = super.getPreferredSize().height;
        addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null
                    && e.getInputEvent() != null && Shortcuts.isMenuDown(e.getInputEvent())) {
                try {
                    URI uri = e.getURL().toURI();
                    if (allowed(uri)) {
                        opener.accept(uri);
                    } else {
                        log.warn("Link di luar halaman rilis diabaikan: {}", uri);
                    }
                } catch (java.net.URISyntaxException ex) {
                    log.warn("Link panduan tidak valid: {}", e.getURL());
                }
            }
        });
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        return new java.awt.Dimension(PREFERRED_WIDTH, preferredHeight);
    }

    /** Isi panduan; path folder instalasi di-escape karena masuk ke HTML. */
    static String html(ReleaseVersion version, Path installDir, String menuKey) {
        return I18n.t("update.guide.html", InstallerInfo.windowsZip(version), InstallerInfo.windowsZipName(version),
                InstallerInfo.releasePage(version), escape(String.valueOf(installDir)), menuKey);
    }

    /** @return true kalau URL boleh dibuka dari panduan (hanya halaman rilis TermUL) */
    static boolean allowed(URI uri) {
        return uri.toString().startsWith(UpdateProtocol.RELEASES.toString());
    }

    /** Buka di browser default (di virtual thread, bukan EDT). */
    private static void browse(URI uri, Consumer<URI> onBrowseFailed) {
        Thread.ofVirtual().name("open-release-link").start(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("browser tidak tersedia");
                }
                Desktop.getDesktop().browse(uri);
            } catch (IOException | RuntimeException e) {
                log.warn("Link rilis tidak bisa dibuka: {}", e.toString());
                javax.swing.SwingUtilities.invokeLater(() -> onBrowseFailed.accept(uri));
            }
        });
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
