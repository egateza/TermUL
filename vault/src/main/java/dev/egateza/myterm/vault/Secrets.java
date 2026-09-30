package dev.egateza.myterm.vault;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Konversi secret char[] ↔ byte[] UTF-8 tanpa melewati String. Buffer sementara di-zero. */
public final class Secrets {

    private Secrets() {
    }

    public static byte[] toUtf8(char[] chars) {
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer buf = null;
        try {
            buf = encoder.encode(CharBuffer.wrap(chars));
            byte[] out = new byte[buf.remaining()];
            buf.get(out);
            return out;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Secret berisi karakter tidak valid");
        } finally {
            if (buf != null && buf.hasArray()) {
                Arrays.fill(buf.array(), (byte) 0);
            }
        }
    }

    public static char[] fromUtf8(byte[] bytes) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer buf = null;
        try {
            buf = decoder.decode(ByteBuffer.wrap(bytes));
            char[] out = new char[buf.remaining()];
            buf.get(out);
            return out;
        } catch (CharacterCodingException e) {
            throw new VaultException("Data secret rusak");
        } finally {
            if (buf != null && buf.hasArray()) {
                Arrays.fill(buf.array(), '\0');
            }
        }
    }

    public static void zero(byte[] b) {
        if (b != null) {
            Arrays.fill(b, (byte) 0);
        }
    }

    public static void zero(char[] c) {
        if (c != null) {
            Arrays.fill(c, '\0');
        }
    }
}
