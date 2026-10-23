package dev.egateza.termul.terminal;

import dev.egateza.termul.ssh.RemoteExec;
import dev.egateza.termul.ssh.ShellQuote;
import dev.egateza.termul.ssh.SshConnection;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Folder kerja shell sebuah tab terminal, dibaca dari server lewat exec channel (koneksi yang sama), untuk "Ikuti folder
 * terminal" di panel SFTP. Tidak mengetik apa pun ke shell user.
 *
 * <p>Caranya: saat shell dibuka, client mengirim variabel {@value #ENV_NAME} berisi token acak (request {@code env};
 * server Debian/Ubuntu menerimanya lewat {@code AcceptEnv LC_*}). Variabel itu diwarisi shell dan semua turunannya. Probe
 * mencari proses milik user dengan token itu di {@code /proc/<pid>/environ}, membaca foreground process group terminal
 * proses tersebut ({@code tpgid} di {@code /proc/<pid>/stat}), lalu {@code readlink /proc/<tpgid>/cwd}. Jadi yang dibaca
 * selalu folder program yang sedang di depan: shell itu sendiri, subshell, atau editor yang dibuka dari situ.
 *
 * <p>Hanya Linux (butuh {@code /proc}). Token bukan secret: hanya penanda untuk membedakan tab.
 */
public final class ShellCwd {

    /** Nama variabel penanda. Awalan {@code LC_} supaya lolos {@code AcceptEnv LC_*} bawaan Debian/Ubuntu. */
    public static final String ENV_NAME = "LC_TERMUL_TTY";

    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");

    /** Exit code probe: penanda tidak ditemukan (server menolak env, bukan Linux, atau shell sudah selesai). */
    static final int EXIT_NO_MARKER = 2;
    /** Exit code probe: program di depan tidak bisa dibaca (mis. shell root lewat {@code sudo -i}). */
    static final int EXIT_UNREADABLE = 3;

    private ShellCwd() {
    }

    /** Hasil probe. */
    public sealed interface Result {
        /** Folder kerja program yang sedang di depan terminal. */
        record Found(String path) implements Result {
        }

        /** Penanda tidak ada di server: fitur tidak bisa dipakai lewat cara ini (lihat {@link ShellCwd}). */
        record Unsupported() implements Result {
        }

        /** Penanda ada, tapi folder program di depan tidak bisa dibaca (milik user lain, mis. root). */
        record Unreadable() implements Result {
        }
    }

    /** Token acak baru (32 digit hex) untuk satu shell. */
    public static String newToken() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /**
     * Command exec untuk token ini. Dibungkus {@code sh -c}, supaya tetap jalan kalau login shell user bukan
     * POSIX (mis. fish).
     */
    static String probeCommand(String token) {
        if (!TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("Token tidak valid");
        }
        String script = String.join("\n",
                "m=" + ENV_NAME + "=" + token,
                "for f in $(grep -lsF \"$m\" /proc/[0-9]*/environ); do",
                "  p=${f#/proc/}; p=${p%/environ}",
                "  s=$(cat \"/proc/$p/stat\" 2>/dev/null) || continue",
                "  s=${s##*\")\"}", // buang "pid (comm)": comm boleh berisi spasi/kurung
                "  set -f; set -- $s; set +f", // $1 state, $2 ppid, $3 pgrp, $4 session, $5 tty_nr, $6 tpgid
                "  [ \"${6:-0}\" -gt 0 ] 2>/dev/null || continue", // proses tanpa terminal (daemon)
                "  readlink \"/proc/$6/cwd\" && exit 0",
                "  exit " + EXIT_UNREADABLE,
                "done",
                "exit " + EXIT_NO_MARKER);
        return "sh -c " + ShellQuote.quote(script);
    }

    /** Menafsirkan hasil exec probe. */
    static Result parse(RemoteExec.Result result) {
        if (result.exitStatus() == 0) {
            String out = result.stdout();
            String path = out.endsWith("\n") ? out.substring(0, out.length() - 1) : out; // spasi di nama folder tetap
            if (path.startsWith("/") && path.indexOf('\n') < 0) {
                return new Result.Found(path);
            }
            return new Result.Unreadable(); // path dengan baris baru / keluaran aneh: jangan dipakai
        }
        if (result.exitStatus() == EXIT_UNREADABLE) {
            return new Result.Unreadable();
        }
        return new Result.Unsupported();
    }

    /** Blocking (exec channel); jangan dipanggil di EDT. */
    public static Result probe(SshConnection connection, String token) throws IOException {
        return parse(RemoteExec.run(connection, probeCommand(token), null, TIMEOUT));
    }
}
