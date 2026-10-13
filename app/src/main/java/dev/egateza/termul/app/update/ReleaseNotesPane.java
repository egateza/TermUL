package dev.egateza.termul.app.update;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.io.IOException;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tampilan catatan rilis (markdown sederhana lewat {@link ReleaseNotesHtml}) dengan warna dari tema yang aktif.
 * Dipakai dialog update dan halaman Yang baru. Link http/https dibuka di browser. Warna diambil saat dibuat, jadi
 * dialog yang dibuat ulang setiap dibuka otomatis mengikuti ganti tema. EDT.
 */
final class ReleaseNotesPane extends JEditorPane {

    private static final Logger log = LoggerFactory.getLogger(ReleaseNotesPane.class);

    /** Milik pane ini saja: stylesheet bawaan HTMLEditorKit dipakai bersama semua JEditorPane. */
    private final StyleSheet css = new StyleSheet();

    /** @param padding jarak isi dari tepi, dalam pixel */
    ReleaseNotesPane(int padding) {
        css.addStyleSheet(new HTMLEditorKit().getStyleSheet());
        styleSheet();
        setEditable(false);
        setEditorKit(new HTMLEditorKit() {
            @Override
            public StyleSheet getStyleSheet() {
                return css;
            }
        });
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        setFont(UIManager.getFont("Label.font"));
        setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                browse(e.getURL().toString());
            }
        });
    }

    /** Tampilkan catatan rilis (markdown sederhana). */
    void setNotes(String markdown) {
        setBody(ReleaseNotesHtml.toHtml(markdown));
    }

    /** Tampilkan HTML yang sudah di-escape (isi {@code <body>}). */
    void setBody(String html) {
        setText("<html><body>" + html + "</body></html>");
        setCaretPosition(0);
    }

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

    private static void browse(String url) {
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
}
