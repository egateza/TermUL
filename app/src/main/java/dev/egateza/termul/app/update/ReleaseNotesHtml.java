package dev.egateza.termul.app.update;

import dev.egateza.termul.update.UpdateProtocol;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catatan rilis (markdown sederhana, sama seperti yang dirender GitHub) menjadi HTML untuk {@code JEditorPane}.
 * Hanya subset kecil: heading {@code #}, bullet {@code -}/{@code *}, paragraf, {@code **tebal**}, {@code `kode`},
 * {@code [teks](https://…)}, dan {@code #123} (issue/PR). Semua teks di-escape; link selain http/https tetap teks biasa,
 * dan tidak pernah ada tag gambar, jadi renderer Swing tidak memuat apa pun dari jaringan.
 */
final class ReleaseNotesHtml {

    private static final Pattern HEADING = Pattern.compile("(#{1,3})\\s+(.*)");
    private static final Pattern BULLET = Pattern.compile("\\s*[-*]\\s+(.*)");
    private static final Pattern INLINE = Pattern.compile(
            "`([^`]+)`"                                        // 1: kode
                    + "|\\*\\*(.+?)\\*\\*"                     // 2: tebal
                    + "|\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)" // 3, 4: link
                    + "|(?<![\\w/&])#(\\d{1,7})\\b");          // 5: issue/PR
    private static final String ISSUES = UpdateProtocol.RELEASES.resolve("../issues/").toString();

    private ReleaseNotesHtml() {
    }

    /** @return isi {@code <body>} (tanpa pembungkus {@code <html>}) */
    static String toHtml(String notes) {
        var out = new StringBuilder();
        var paragraph = new StringBuilder();
        boolean inList = false;
        for (String line : notes.strip().split("\\R", -1)) {
            Matcher heading = HEADING.matcher(line);
            Matcher bullet = BULLET.matcher(line);
            if (line.isBlank() || heading.matches() || bullet.matches()) {
                flush(out, paragraph);
            }
            if (!bullet.matches() && !line.isBlank() && inList) {
                out.append("</ul>");
                inList = false;
            }
            if (heading.matches()) {
                int level = heading.group(1).length() + 1; // h1 dipakai judul versi
                out.append("<h").append(level).append('>').append(inline(heading.group(2).strip()))
                        .append("</h").append(level).append('>');
            } else if (bullet.matches()) {
                if (!inList) {
                    out.append("<ul>");
                    inList = true;
                }
                out.append("<li>").append(inline(bullet.group(1).strip())).append("</li>");
            } else if (!line.isBlank()) {
                if (!paragraph.isEmpty()) {
                    paragraph.append(' ');
                }
                paragraph.append(inline(line.strip()));
            }
        }
        flush(out, paragraph);
        if (inList) {
            out.append("</ul>");
        }
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            out.append("<p>").append(paragraph).append("</p>");
            paragraph.setLength(0);
        }
    }

    static String inline(String text) {
        var out = new StringBuilder();
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            out.append(escape(text.substring(last, m.start())));
            if (m.group(1) != null) {
                out.append("<code>").append(escape(m.group(1))).append("</code>");
            } else if (m.group(2) != null) {
                out.append("<b>").append(escape(m.group(2))).append("</b>");
            } else if (m.group(3) != null) {
                out.append(link(m.group(4), m.group(3)));
            } else {
                out.append(link(ISSUES + m.group(5), "#" + m.group(5)));
            }
            last = m.end();
        }
        return out.append(escape(text.substring(last))).toString();
    }

    static String link(String url, String text) {
        return "<a href=\"" + escape(url) + "\">" + escape(text) + "</a>";
    }

    static String escape(String text) {
        var out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
