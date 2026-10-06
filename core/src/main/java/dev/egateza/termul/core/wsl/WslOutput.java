package dev.egateza.termul.core.wsl;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decode output {@code wsl.exe}. Perintah seperti {@code --list} menulis UTF-16LE, kecuali WSL versi baru dengan
 * {@code WSL_UTF8=1}; perintah di dalam distro ({@code --exec}) menulis UTF-8. Jadi encoding ditebak dari isinya.
 */
public final class WslOutput {

    private WslOutput() {
    }

    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String text = looksUtf16Le(bytes)
                ? new String(bytes, StandardCharsets.UTF_16LE)
                : new String(bytes, StandardCharsets.UTF_8);
        return text.replace("﻿", "").replace("\0", "");
    }

    /** Baris tidak kosong, sudah di-trim. */
    public static List<String> lines(String output) {
        return output.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** BOM UTF-16LE, atau teks ASCII dalam UTF-16LE (byte ganjil kebanyakan 0). */
    static boolean looksUtf16Le(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return true;
        }
        int pairs = b.length / 2;
        if (pairs == 0) {
            return false;
        }
        int zeros = 0;
        for (int i = 1; i < b.length; i += 2) {
            if (b[i] == 0) {
                zeros++;
            }
        }
        return zeros * 2 > pairs;
    }
}
