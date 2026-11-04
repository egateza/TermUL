package dev.egateza.termul.ssh.tunnel;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Sisi server proxy SOCKS untuk tunnel dynamic, seperti {@code ssh -D}: SOCKS5 (RFC 1928, tanpa autentikasi) dan
 * SOCKS4/4a, hanya perintah CONNECT. Nama host tidak di-resolve di PC ini: diteruskan ke server (DNS dari server).
 */
final class Socks {

    /** Tujuan yang diminta aplikasi. */
    record Request(int version, String host, int port) {
    }

    /** Permintaan yang tidak didukung; balasan penolakan sudah dikirim. */
    static final class UnsupportedException extends IOException {
        UnsupportedException(String message) {
            super(message);
        }
    }

    static final int V4 = 4;
    static final int V5 = 5;
    private static final int CONNECT = 1;
    private static final int MAX_FIELD = 255;

    private Socks() {
    }

    /** Baca sapaan dan permintaan CONNECT. Balasan sukses/gagal dikirim lewat {@link #reply} setelah tujuan dibuka. */
    static Request accept(InputStream in, OutputStream out) throws IOException {
        int version = read(in);
        return switch (version) {
            case V5 -> accept5(in, out);
            case V4 -> accept4(in, out);
            default -> throw new UnsupportedException("Versi SOCKS tidak dikenal: " + version);
        };
    }

    private static Request accept5(InputStream in, OutputStream out) throws IOException {
        int methods = read(in);
        boolean noAuth = false;
        for (int i = 0; i < methods; i++) {
            noAuth |= read(in) == 0;
        }
        if (!noAuth) {
            out.write(new byte[] {V5, (byte) 0xFF});
            out.flush();
            throw new UnsupportedException("Aplikasi meminta autentikasi SOCKS; proxy TermUL tanpa autentikasi");
        }
        out.write(new byte[] {V5, 0});
        out.flush();
        if (read(in) != V5) {
            throw new UnsupportedException("Permintaan SOCKS5 tidak valid");
        }
        int command = read(in);
        read(in); // RSV
        int type = read(in);
        String host = switch (type) {
            case 1 -> InetAddress.getByAddress(readFully(in, 4)).getHostAddress();
            case 3 -> new String(readFully(in, read(in)), StandardCharsets.US_ASCII);
            case 4 -> InetAddress.getByAddress(readFully(in, 16)).getHostAddress();
            default -> {
                reply5(out, 8);
                throw new UnsupportedException("Jenis alamat SOCKS5 tidak didukung: " + type);
            }
        };
        int port = readPort(in);
        if (command != CONNECT) {
            reply5(out, 7);
            throw new UnsupportedException("Perintah SOCKS5 tidak didukung: " + command);
        }
        return new Request(V5, host, port);
    }

    private static Request accept4(InputStream in, OutputStream out) throws IOException {
        int command = read(in);
        int port = readPort(in);
        byte[] ip = readFully(in, 4);
        readString(in); // user id, diabaikan
        String host;
        if (ip[0] == 0 && ip[1] == 0 && ip[2] == 0 && ip[3] != 0) { // SOCKS4a: nama host menyusul
            host = readString(in);
        } else {
            host = InetAddress.getByAddress(ip).getHostAddress();
        }
        if (command != CONNECT) {
            reply4(out, false);
            throw new UnsupportedException("Perintah SOCKS4 tidak didukung: " + command);
        }
        return new Request(V4, host, port);
    }

    /** Balasan untuk permintaan CONNECT: berhasil, atau tujuan tidak bisa dibuka. */
    static void reply(OutputStream out, Request request, boolean ok) throws IOException {
        if (request.version() == V5) {
            reply5(out, ok ? 0 : 5);
        } else {
            reply4(out, ok);
        }
    }

    private static void reply5(OutputStream out, int code) throws IOException {
        out.write(new byte[] {V5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0});
        out.flush();
    }

    private static void reply4(OutputStream out, boolean ok) throws IOException {
        out.write(new byte[] {0, (byte) (ok ? 0x5A : 0x5B), 0, 0, 0, 0, 0, 0});
        out.flush();
    }

    private static int read(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("Koneksi SOCKS ditutup aplikasi");
        }
        return b;
    }

    private static int readPort(InputStream in) throws IOException {
        return (read(in) << 8) | read(in);
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] data = in.readNBytes(n);
        if (data.length < n) {
            throw new EOFException("Koneksi SOCKS ditutup aplikasi");
        }
        return data;
    }

    /** String berakhiran NUL (SOCKS4), paling panjang {@value #MAX_FIELD} byte. */
    private static String readString(InputStream in) throws IOException {
        var buf = new ByteArrayOutputStream();
        int b;
        while ((b = read(in)) != 0) {
            if (buf.size() >= MAX_FIELD) {
                throw new UnsupportedException("Field SOCKS4 terlalu panjang");
            }
            buf.write(b);
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }
}
