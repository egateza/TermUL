package dev.egateza.termul.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Menangkap OSC 7 ({@code ESC ] 7 ; file://host/path BEL}, atau diakhiri {@code ESC \}) dari output terminal: cara
 * standar shell memberi tahu folder kerjanya (zsh/fish dengan integrasi terminal, bash dengan {@code PROMPT_COMMAND}).
 * Menyimpan state, jadi sequence yang terpotong di antara dua chunk tetap terbaca. Tidak thread-safe: dipakai dari
 * thread pembaca terminal saja.
 */
public final class Osc7Parser implements SshTtyConnector.OutputListener {

    /** Panjang maksimum isi OSC yang disimpan; sequence yang lebih panjang dibuang (bukan path wajar). */
    static final int MAX_LENGTH = 4096;

    private enum State { TEXT, ESC, OSC, OSC_ESC }

    private final Consumer<String> sink;
    private final StringBuilder payload = new StringBuilder();
    private State state = State.TEXT;
    private boolean overflow;

    /** @param sink menerima path (sudah di-decode) setiap OSC 7 lengkap; dipanggil di thread pembaca terminal */
    public Osc7Parser(Consumer<String> sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    @Override
    public void onOutput(char[] buf, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            char c = buf[i];
            switch (state) {
                case TEXT -> {
                    if (c == 0x1B) {
                        state = State.ESC;
                    }
                }
                case ESC -> {
                    if (c == ']') {
                        state = State.OSC;
                        payload.setLength(0);
                        overflow = false;
                    } else {
                        state = c == 0x1B ? State.ESC : State.TEXT;
                    }
                }
                case OSC -> {
                    if (c == 0x07) {
                        finish();
                    } else if (c == 0x1B) {
                        state = State.OSC_ESC;
                    } else if (payload.length() < MAX_LENGTH) {
                        payload.append(c);
                    } else {
                        overflow = true;
                    }
                }
                case OSC_ESC -> {
                    if (c == '\\') {
                        finish();
                    } else { // OSC terpotong oleh sequence lain: buang, mulai dari ESC ini
                        state = State.ESC;
                        i--;
                    }
                }
            }
        }
    }

    private void finish() {
        state = State.TEXT;
        if (!overflow) {
            parse(payload.toString()).ifPresent(sink);
        }
        payload.setLength(0);
    }

    /**
     * Isi OSC (tanpa {@code ESC ]} dan terminator) menjadi path, kalau berupa OSC 7 {@code file://} yang valid.
     * Host diabaikan: yang dikirim shell di sesi SSH selalu host remote itu sendiri.
     */
    static Optional<String> parse(String osc) {
        if (!osc.startsWith("7;")) {
            return Optional.empty();
        }
        String url = osc.substring(2);
        if (!url.regionMatches(true, 0, "file://", 0, 7)) {
            return Optional.empty();
        }
        int slash = url.indexOf('/', 7); // setelah host (boleh kosong: file:///path)
        if (slash < 0) {
            return Optional.empty();
        }
        return decode(url.substring(slash)).filter(p -> p.startsWith("/") && p.indexOf('\0') < 0);
    }

    /** Percent-decoding UTF-8; {@code +} tetap {@code +} (bukan form encoding). Encoding rusak = kosong. */
    static Optional<String> decode(String s) {
        if (s.indexOf('%') < 0) {
            return Optional.of(s);
        }
        var out = new ByteArrayOutputStream(s.length());
        int i = 0;
        while (i < s.length()) {
            int percent = s.indexOf('%', i);
            if (percent != i) { // potongan teks biasa (utuh, supaya surrogate pair tidak terbelah)
                int end = percent < 0 ? s.length() : percent;
                out.writeBytes(s.substring(i, end).getBytes(StandardCharsets.UTF_8));
                i = end;
                continue;
            }
            if (i + 2 >= s.length()) {
                return Optional.empty();
            }
            int hi = Character.digit(s.charAt(i + 1), 16);
            int lo = Character.digit(s.charAt(i + 2), 16);
            if (hi < 0 || lo < 0) {
                return Optional.empty();
            }
            out.write(hi << 4 | lo);
            i += 3;
        }
        return Optional.of(out.toString(StandardCharsets.UTF_8));
    }
}
