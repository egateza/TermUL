package dev.egateza.myterm.sftp.edit;

import java.io.ByteArrayOutputStream;

/** Deteksi dan konversi line ending (file server Linux biasanya LF). */
public final class LineEndings {

    public enum Style { NONE, LF, CRLF, MIXED }

    private LineEndings() {
    }

    public static Style detect(byte[] data) {
        boolean lf = false;
        boolean crlf = false;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                if (i > 0 && data[i - 1] == '\r') {
                    crlf = true;
                } else {
                    lf = true;
                }
            }
        }
        if (lf && crlf) {
            return Style.MIXED;
        }
        return crlf ? Style.CRLF : lf ? Style.LF : Style.NONE;
    }

    /** Mengganti setiap CRLF menjadi LF (CR tunggal dibiarkan). */
    public static byte[] toLf(byte[] data) {
        var out = new ByteArrayOutputStream(data.length);
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\r' && i + 1 < data.length && data[i + 1] == '\n') {
                continue;
            }
            out.write(data[i]);
        }
        return out.toByteArray();
    }

    /** true kalau file asli LF/tanpa newline tapi sekarang mengandung CRLF (editor Windows mengubahnya). */
    public static boolean crlfIntroduced(Style original, Style now) {
        return (original == Style.LF || original == Style.NONE) && (now == Style.CRLF || now == Style.MIXED);
    }
}
