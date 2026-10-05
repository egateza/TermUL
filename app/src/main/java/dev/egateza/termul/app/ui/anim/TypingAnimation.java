package dev.egateza.termul.app.ui.anim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Prompt shell yang mengetik perintah bercanda huruf demi huruf, lalu menghapusnya. Perintah sengaja tidak realistis
 * dan agak redup supaya tidak disangka sungguh dikirim ke server (shell belum terbuka saat animasi ini tampil).
 */
final class TypingAnimation implements Animation {

    /** Prompt saat tidak ada host (bar "Host status"). */
    static final String GENERIC = "termul";
    /** Panjang maksimum {@code user@host} sebelum dipotong dengan {@code …}. */
    static final int MAX_PROMPT = 18;
    static final List<String> COMMANDS = List.of("make coffee", "uptime", "ping kopi", "whoami", "df -h");

    private enum Mode { TYPE, HOLD, DELETE }

    private final boolean large;
    private final String who;
    private final SplittableRandom random = new SplittableRandom(7);
    private int frame;
    private int command;
    private int typed;
    private Mode mode = Mode.TYPE;
    private int wait = 6;

    /** @param who teks sebelum {@code :~$}, mis. hasil {@link #prompt}; null = {@value #GENERIC} */
    TypingAnimation(Size size, String who) {
        this.large = size.large();
        this.who = who == null || who.isBlank() ? GENERIC : who;
    }

    /**
     * Teks prompt {@code user@host} untuk profil, seperti prompt bawaan bash: hostname dipendekkan ke label pertama, alamat IP
     * utuh, lalu dipotong dengan {@code …} kalau lebih dari {@value #MAX_PROMPT} karakter.
     */
    static String prompt(String user, String host) {
        if (host == null || host.isBlank()) {
            return GENERIC;
        }
        String h = host.strip();
        boolean ip = h.matches("\\d{1,3}(\\.\\d{1,3}){3}") || h.contains(":");
        if (!ip) {
            int dot = h.indexOf('.');
            if (dot > 0) {
                h = h.substring(0, dot);
            }
        }
        String full = user == null || user.isBlank() ? h : user.strip() + "@" + h;
        return full.length() <= MAX_PROMPT ? full : full.substring(0, MAX_PROMPT - 1) + "…";
    }

    String who() {
        return who;
    }

    @Override
    public void step() {
        frame++;
        if (wait > 0) {
            wait--;
            return;
        }
        switch (mode) {
            case TYPE -> {
                typed++;
                wait = 1 + random.nextInt(3);
                if (typed >= COMMANDS.get(command).length()) {
                    mode = Mode.HOLD;
                    wait = 30;
                }
            }
            case HOLD -> mode = Mode.DELETE;
            case DELETE -> {
                if (--typed <= 0) {
                    typed = 0;
                    command = (command + 1) % COMMANDS.size();
                    mode = Mode.TYPE;
                    wait = 10;
                }
            }
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        g.setFont(Palette.mono(large ? 12f : 11f));
        var fm = g.getFontMetrics();
        int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
        int x = large ? 8 : 4;
        x = text(g, who, large ? c.green() : c.muted(), x, baseline);
        x = text(g, ":", large ? c.fg() : c.muted(), x, baseline);
        x = text(g, "~", large ? c.accent() : c.muted(), x, baseline);
        x = text(g, "$ ", large ? c.fg() : c.muted(), x, baseline);
        Color dim = Palette.alpha(c.fg(), 0.7);
        x = text(g, COMMANDS.get(command).substring(0, typed), dim, x, baseline);
        if (mode != Mode.HOLD || frame % 16 < 8) {
            g.setColor(dim);
            g.fillRect(x + 1, baseline - fm.getAscent(), fm.charWidth('M'), fm.getAscent() + fm.getDescent());
        }
    }

    private static int text(Graphics2D g, String s, Color color, int x, int baseline) {
        g.setColor(color);
        g.drawString(s, x, baseline);
        return x + g.getFontMetrics().stringWidth(s);
    }
}
