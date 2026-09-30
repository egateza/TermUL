package dev.egateza.termul.ssh;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;

/**
 * Menjalankan satu command lewat exec channel (koneksi yang sama dengan terminal, tanpa PTY) dan menunggu selesai.
 * Blocking: jangan dipanggil di EDT.
 *
 * <p>Stdin dikirim sekali lalu ditutup (EOF), sehingga dipakai untuk secret seperti {@code sudo -S}: secret tidak
 * pernah muncul di argumen command (terlihat di {@code ps}). Output dibatasi {@value #MAX_OUTPUT} byte per stream.
 */
public final class RemoteExec {

    static final int MAX_OUTPUT = 64 * 1024;

    /** @param exitStatus exit code command, atau -1 kalau server tidak mengirimkannya */
    public record Result(int exitStatus, String stdout, String stderr) {
        public boolean ok() {
            return exitStatus == 0;
        }
    }

    private RemoteExec() {
    }

    /**
     * @param stdin byte yang dikirim ke stdin sebelum EOF, boleh null. Tidak diubah; pemanggil yang me-zero.
     * @throws IOException kalau channel gagal dibuka atau command tidak selesai dalam {@code timeout}
     */
    public static Result run(SshConnection connection, String command, byte[] stdin, Duration timeout)
            throws IOException {
        try (ChannelExec exec = connection.createExec(command)) {
            var out = new LimitedOutput(MAX_OUTPUT);
            var err = new LimitedOutput(MAX_OUTPUT);
            exec.setOut(out);
            exec.setErr(err);
            exec.open().verify(connection.channelOpenTimeout());
            try (OutputStream in = exec.getInvertedIn()) {
                if (stdin != null) {
                    in.write(stdin);
                    in.flush();
                }
            }
            Set<ClientChannelEvent> events = exec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout);
            if (!events.contains(ClientChannelEvent.CLOSED)) {
                exec.close(true);
                throw new IOException("Command remote tidak selesai dalam " + timeout.toSeconds() + " detik");
            }
            Integer status = exec.getExitStatus();
            return new Result(status == null ? -1 : status,
                    out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        }
    }
}
