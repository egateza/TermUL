package dev.egateza.termul.terminal;

/**
 * Membuang escape sequence ANSI (CSI, OSC, DCS/PM/APC, ESC + satu karakter) dan karakter kontrol selain
 * {@code \n}/{@code \r} dari stream teks. Menyimpan state, jadi sequence yang terpotong di antara dua chunk tetap
 * terbuang. Tidak thread-safe.
 */
final class AnsiStripper {

    private enum State { TEXT, ESC, CSI, STRING, STRING_ESC }

    private State state = State.TEXT;

    /** Menambahkan teks yang sudah dibersihkan dari {@code buf[off, off+len)} ke {@code out}. */
    void strip(char[] buf, int off, int len, StringBuilder out) {
        for (int i = off; i < off + len; i++) {
            char c = buf[i];
            switch (state) {
                case TEXT -> {
                    if (c == 0x1B) {
                        state = State.ESC;
                    } else if (c == 0x9B) {
                        state = State.CSI; // CSI 8-bit
                    } else if (c == '\n' || c == '\r' || (c >= 0x20 && c != 0x7F && (c < 0x80 || c > 0x9F))) {
                        out.append(c);
                    }
                }
                case ESC -> state = switch (c) {
                    case '[' -> State.CSI;
                    case ']', 'P', '^', '_', 'X' -> State.STRING; // OSC, DCS, PM, APC, SOS: sampai BEL/ST
                    default -> State.TEXT; // ESC + satu karakter (mis. ESC =, ESC 7)
                };
                case CSI -> {
                    if (c >= 0x40 && c <= 0x7E) {
                        state = State.TEXT; // final byte
                    }
                }
                case STRING -> {
                    if (c == 0x07) {
                        state = State.TEXT;
                    } else if (c == 0x1B) {
                        state = State.STRING_ESC;
                    }
                }
                case STRING_ESC -> state = c == '\\' ? State.TEXT : State.STRING;
            }
        }
    }
}
