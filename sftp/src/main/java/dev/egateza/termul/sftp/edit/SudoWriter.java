package dev.egateza.termul.sftp.edit;

import dev.egateza.termul.core.config.ValidationHooks;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.RemoteFileService;
import dev.egateza.termul.sftp.RemotePaths;
import dev.egateza.termul.sftp.TransferListener;
import dev.egateza.termul.ssh.RemoteExec;
import dev.egateza.termul.ssh.ShellQuote;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upload untuk file yang tidak bisa ditulis user login (mis. milik root), lewat sudo:
 * <ol>
 *   <li>SFTP: file baru ditulis ke direktori privat {@code /tmp/termul-<acak>} (mode 700, dibuat sebelum isinya);</li>
 *   <li>exec: <b>satu</b> {@code sudo sh -c <script>} yang (sebagai root) me-resolve symlink target, menyalin backup ke
 *       {@value #BACKUP_DIR}, memasang file baru dengan owner/group/mode target lama ({@code install} ke temp di
 *       direktori yang sama lalu {@code mv}, jadi atomic), menjalankan hook validasi, dan mengembalikan backup kalau
 *       validasi gagal. Semua dalam satu sudo, jadi rollback tetap jalan walau yang rusak adalah {@code sudoers};</li>
 *   <li>direktori sementara dihapus (juga saat gagal).</li>
 * </ol>
 *
 * <p>Password sudo dikirim lewat stdin ({@code sudo -S -k -p ''}), tidak pernah di argumen command. {@code -k}
 * memaksa sudo selalu membaca password (tidak memakai cache), sehingga stdin tidak pernah sampai ke script. Tanpa
 * password tersimpan dicoba {@code sudo -n} dulu (NOPASSWD), baru user diminta password. Password salah tidak diulang
 * (satu percobaan, supaya tidak memicu lockout {@code pam_faillock}).
 *
 * <p>Butuh shell login POSIX (bash/dash/zsh) di server. Blocking: jangan dipanggil di EDT.
 */
public final class SudoWriter implements RemoteEditSession.Uploader {

    private static final Logger log = LoggerFactory.getLogger(SudoWriter.class);

    /** Folder backup di server (root saja, mode 700). */
    public static final String BACKUP_DIR = "/var/backups/termul";
    /** Jumlah backup yang disimpan per file. */
    static final int KEEP_BACKUPS = 10;
    static final String TMP_PARENT = "/tmp";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    static final int EXIT_VALIDATION_ROLLED_BACK = 10;
    static final int EXIT_VALIDATION_ROLLBACK_FAILED = 11;
    static final int EXIT_TARGET_INVALID = 20;
    static final int EXIT_BACKUP_FAILED = 21;
    static final int EXIT_INSTALL_FAILED = 22;
    /** Exit code sudo sendiri saat menolak (auth gagal, tidak diizinkan). */
    static final int SUDO_FAILED = 1;

    /**
     * Script root. Argumen: $1 target, $2 file baru, $3 command validasi (boleh kosong), $4 folder backup,
     * $5 jumlah backup yang disimpan. Stdout baris terakhir = path backup; pesan & output validasi ke stderr.
     */
    static final String SCRIPT = """
            target=$1; src=$2; hook=$3; bakdir=$4; keep=$5
            dst=$(readlink -f "$target") || { echo "Path tidak valid: $target" >&2; exit 20; }
            [ -f "$dst" ] || { echo "Bukan file biasa: $dst" >&2; exit 20; }
            mode=$(stat -c %a "$dst") && owner=$(stat -c %u "$dst") && group=$(stat -c %g "$dst") || exit 20
            dir=$(dirname "$dst"); base=$(basename "$dst")
            tmp="$dir/.$base.termul-$$.tmp"
            umask 077
            mkdir -p "$bakdir" && chmod 700 "$bakdir" || { echo "Gagal membuat folder backup $bakdir" >&2; exit 21; }
            key=$(printf '%s' "$dst" | tr '/' '%')
            bak="$bakdir/$key.$(date +%Y%m%d-%H%M%S).$$"
            cp -p "$dst" "$bak" || { echo "Gagal membuat backup $bak" >&2; exit 21; }
            install -m "$mode" -o "$owner" -g "$group" "$src" "$tmp" || { rm -f "$tmp"; echo "Gagal memasang file baru" >&2; exit 22; }
            mv -f "$tmp" "$dst" || { rm -f "$tmp"; echo "Gagal mengganti $dst" >&2; exit 22; }
            if [ -n "$hook" ]; then
              if ! TERMUL_FILE="$dst" sh -c "$hook" >&2 </dev/null; then
                rtmp="$dir/.$base.termul-$$.rollback"
                if cp -p "$bak" "$rtmp" && mv -f "$rtmp" "$dst"; then exit 10; fi
                rm -f "$rtmp"; exit 11
              fi
            fi
            ls -1t "$bakdir/$key".* 2>/dev/null | tail -n +$((keep + 1)) | while IFS= read -r old; do rm -f "$old"; done
            printf '%s\\n' "$bak"
            exit 0
            """;

    /** Sumber password sudo. Array yang dikembalikan menjadi milik {@link SudoWriter} (di-zero setelah dipakai). */
    public interface SudoPassword {
        /** Password tersimpan (vault), atau null kalau tidak ada. */
        char[] stored() throws RemoteFileException;

        /** Minta password ke user (tidak disimpan), atau null kalau dibatalkan. */
        char[] prompt() throws RemoteFileException;
    }

    /** Hasil pemasangan yang berhasil. */
    public record Installed(String backupPath) {
    }

    private final SudoPassword password;
    private final Supplier<ValidationHooks> hooks;
    private volatile Installed last;

    public SudoWriter(SudoPassword password, Supplier<ValidationHooks> hooks) {
        this.password = Objects.requireNonNull(password);
        this.hooks = Objects.requireNonNull(hooks);
    }

    /** Hasil upload terakhir yang berhasil (path backup), atau null. */
    public Installed lastInstalled() {
        return last;
    }

    @Override
    public void upload(RemoteFileService files, Path local, String remotePath) throws RemoteFileException {
        if (!remotePath.startsWith("/")) {
            throw new RemoteFileException("Path remote harus absolut: " + remotePath);
        }
        String hook = hooks.get().find(remotePath).map(ValidationHooks.Hook::command).orElse("");
        String dir = files.createPrivateDir(TMP_PARENT);
        try {
            String staged = RemotePaths.join(dir, "content");
            files.upload(local, staged, 0600, TransferListener.NONE);
            var result = runSudo(files, command(remotePath, staged, hook));
            last = interpret(result, remotePath, hook);
            log.info("File {} dipasang dengan sudo{}; backup {}", remotePath,
                    hook.isEmpty() ? "" : " (validasi '" + hook + "' lolos)", last.backupPath());
        } finally {
            try {
                files.delete(dir, true);
            } catch (RemoteFileException e) {
                log.warn("Direktori sementara {} tidak bisa dihapus: {}", dir, e.getMessage());
            }
        }
    }

    /** Menjalankan sudo dengan password tersimpan, atau {@code sudo -n} lalu prompt kalau server minta password. */
    private RemoteExec.Result runSudo(RemoteFileService files, List<String> script) throws RemoteFileException {
        char[] stored = password.stored();
        if (stored != null) {
            return runWithPassword(files, script, stored, true);
        }
        var result = exec(files, "sudo -n " + String.join(" ", script), null);
        if (!needsPassword(result)) {
            return result;
        }
        char[] typed = password.prompt();
        if (typed == null) {
            throw new RemoteFileException("Upload dengan sudo dibatalkan (password tidak diisi).");
        }
        return runWithPassword(files, script, typed, false);
    }

    private RemoteExec.Result runWithPassword(RemoteFileService files, List<String> script, char[] pw,
                                              boolean fromVault) throws RemoteFileException {
        byte[] stdin = null;
        try {
            stdin = utf8Line(pw);
            var result = exec(files, "sudo -S -k -p '' " + String.join(" ", script), stdin);
            if (wrongPassword(result)) {
                throw new SudoAuthException(fromVault
                        ? "Password sudo di vault ditolak server. Perbarui password sudo di profil host."
                        : "Password sudo salah.");
            }
            return result;
        } finally {
            Arrays.fill(pw, '\0');
            if (stdin != null) {
                Arrays.fill(stdin, (byte) 0);
            }
        }
    }

    private static RemoteExec.Result exec(RemoteFileService files, String sudoCommand, byte[] stdin)
            throws RemoteFileException {
        try {
            // env: bekerja di shell login apa pun; LC_ALL=C supaya pesan sudo bisa dikenali
            return RemoteExec.run(files.connection(), "env LC_ALL=C " + sudoCommand, stdin, TIMEOUT);
        } catch (IOException e) {
            throw new RemoteFileException("Gagal menjalankan sudo: " + e.getMessage(), e);
        }
    }

    /** {@code sh -c <script> termul <target> <staged> <hook> <bakdir> <keep>}, sudah di-quote. */
    static List<String> command(String target, String staged, String hook) {
        return List.of("sh", "-c", ShellQuote.quote(SCRIPT), "termul", ShellQuote.quote(target),
                ShellQuote.quote(staged), ShellQuote.quote(hook), ShellQuote.quote(BACKUP_DIR),
                String.valueOf(KEEP_BACKUPS));
    }

    static Installed interpret(RemoteExec.Result result, String target, String hook) throws RemoteFileException {
        String err = result.stderr().strip();
        switch (result.exitStatus()) {
            case 0 -> {
                String[] lines = result.stdout().strip().split("\\R");
                return new Installed(lines[lines.length - 1].strip());
            }
            case EXIT_VALIDATION_ROLLED_BACK -> throw new RemoteValidationException(target, hook, err, true);
            case EXIT_VALIDATION_ROLLBACK_FAILED -> throw new RemoteValidationException(target, hook, err, false);
            case EXIT_TARGET_INVALID, EXIT_BACKUP_FAILED, EXIT_INSTALL_FAILED ->
                    throw new RemoteFileException("Gagal memasang " + target + " dengan sudo: " + err);
            default -> {
                String lower = err.toLowerCase(Locale.ROOT);
                if (lower.contains("not in the sudoers") || lower.contains("is not allowed to")) {
                    throw new SudoAuthException("User tidak punya hak sudo untuk memasang " + target + ".");
                }
                if (lower.contains("a password is required")) {
                    throw new SudoAuthException("sudo meminta password, tapi password tidak tersedia.");
                }
                throw new RemoteFileException("sudo gagal (exit " + result.exitStatus() + ")"
                        + (err.isEmpty() ? "" : ": " + err));
            }
        }
    }

    static boolean needsPassword(RemoteExec.Result result) {
        return result.exitStatus() == SUDO_FAILED
                && result.stderr().toLowerCase(Locale.ROOT).contains("a password is required");
    }

    static boolean wrongPassword(RemoteExec.Result result) {
        if (result.exitStatus() != SUDO_FAILED) {
            return false;
        }
        String lower = result.stderr().toLowerCase(Locale.ROOT);
        return lower.contains("incorrect password") || lower.contains("sorry, try again")
                || lower.contains("no password was provided");
    }

    /** UTF-8 + {@code '\n'} tanpa melewati String; buffer perantara di-zero. */
    static byte[] utf8Line(char[] chars) throws RemoteFileException {
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer buf = null;
        try {
            buf = encoder.encode(CharBuffer.wrap(chars));
            byte[] out = new byte[buf.remaining() + 1];
            buf.get(out, 0, buf.remaining());
            out[out.length - 1] = '\n';
            return out;
        } catch (CharacterCodingException e) {
            throw new RemoteFileException("Password sudo berisi karakter tidak valid.");
        } finally {
            if (buf != null && buf.hasArray()) {
                Arrays.fill(buf.array(), (byte) 0);
            }
        }
    }
}
