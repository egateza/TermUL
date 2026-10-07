package dev.egateza.termul.app.bugreport;

import dev.egateza.termul.app.i18n.I18n;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Laporan bug yang dikirim lewat form "new issue" GitHub di browser: user login dan menekan Submit di GitHub, jadi
 * aplikasi tidak pernah memegang token GitHub.
 *
 * @param system info sistem, atau null kalau user tidak mau menyertakannya
 */
public record BugReport(String title, String description, String steps, SystemInfo system) {

    static final URI NEW_ISSUE = URI.create("https://github.com/egateza/TermUL/issues/new");
    /** Batas aman panjang URL: GitHub menolak URL yang terlalu panjang (414). */
    static final int MAX_URI_LENGTH = 7000;
    static final int MAX_TITLE = 200;
    static final String TRUNCATED_MARK = "\n\n…";

    /** URL form issue; {@code truncated} = deskripsi/langkah dipotong supaya URL tidak terlalu panjang. */
    public record Link(URI uri, String body, boolean truncated) {
    }

    public BugReport {
        title = Objects.requireNonNullElse(title, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
        steps = Objects.requireNonNullElse(steps, "").strip();
    }

    public boolean isSubmittable() {
        return !title.isEmpty();
    }

    /** Isi issue (Markdown) tanpa pemotongan. */
    public String body() {
        return join(userPart(), systemPart());
    }

    /** URL form issue. Kalau terlalu panjang, bagian yang ditulis user dipotong; info sistem selalu utuh. */
    public Link link() {
        String full = userPart();
        String user = full;
        String system = systemPart();
        String shortTitle = cut(title, MAX_TITLE);
        URI uri = uri(shortTitle, join(user, system));
        int keep = full.length();
        boolean truncated = false;
        while (uri.toString().length() > MAX_URI_LENGTH && keep > 0) {
            keep = keep * 9 / 10;
            truncated = true;
            user = cut(full, keep) + TRUNCATED_MARK;
            uri = uri(shortTitle, join(user, system));
        }
        return new Link(uri, join(user, system), truncated);
    }

    private String userPart() {
        var sb = new StringBuilder();
        section(sb, I18n.t("bugReport.section.description"), description);
        section(sb, I18n.t("bugReport.section.steps"), steps);
        return sb.toString();
    }

    private String systemPart() {
        if (system == null) {
            return "";
        }
        var sb = new StringBuilder();
        section(sb, I18n.t("bugReport.section.system"), String.join("\n", system.lines()));
        return sb.toString();
    }

    private static String join(String user, String system) {
        return user.isEmpty() || system.isEmpty() ? user + system : user + "\n\n" + system;
    }

    private static void section(StringBuilder sb, String heading, String text) {
        if (text.isEmpty()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append("### ").append(heading).append("\n\n").append(text);
    }

    private static URI uri(String title, String body) {
        return URI.create(NEW_ISSUE + "?labels=bug&title=" + encode(title) + "&body=" + encode(body));
    }

    /** Form-encoding memakai '+' untuk spasi; %20 lebih aman untuk query string GitHub. */
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Potong ke maksimal {@code max} char tanpa memecah surrogate pair (emoji). */
    private static String cut(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int end = max > 0 && Character.isHighSurrogate(s.charAt(max - 1)) ? max - 1 : max;
        return s.substring(0, end).stripTrailing();
    }
}
