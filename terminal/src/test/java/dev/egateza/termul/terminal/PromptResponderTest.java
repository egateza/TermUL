package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.terminal.PromptResponder.Kind;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PromptResponderTest {

    private static final String PROMPT = "user@server01:~$ ";

    private final AtomicLong clock = new AtomicLong();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final PromptResponder responder = new PromptResponder("user", new PromptResponder.Listener() {
        @Override
        public void respond(Kind kind) {
            events.add("respond " + kind);
        }

        @Override
        public void rejected(Kind kind) {
            events.add("rejected " + kind);
        }
    }, clock::get);

    private void output(String text) {
        char[] chars = text.toCharArray();
        responder.onOutput(chars, 0, chars.length);
    }

    private void advance(Duration d) {
        clock.addAndGet(d.toNanos());
    }

    @Test
    void sudoSetelahEnterMengirimPasswordSekali() {
        responder.onEnter(PROMPT + "sudo systemctl restart nginx");
        output("\r\n[sudo] password for user: ");

        assertThat(events).containsExactly("respond SUDO");
    }

    @Test
    void suMemakaiPasswordRoot() {
        responder.onEnter("[user@host ~]$ su -");
        output("\r\nPassword: ");

        assertThat(events).containsExactly("respond SU");
    }

    @Test
    void promptBahasaIndonesiaDikenali() {
        responder.onEnter(PROMPT + "sudo -i");
        output("\r\n[sudo] kata sandi untuk user: ");

        assertThat(events).containsExactly("respond SUDO");
    }

    @Test
    void promptBerwarnaDanTerpotongDiBeberapaChunkTetapDikenali() {
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n\u001b[1;31m[sudo] pass");
        output("word for \u001b[0m\u001b]0;judul\u0007u");
        output("ser: ");

        assertThat(events).containsExactly("respond SUDO");
    }

    // --- spoofing: prompt palsu tidak boleh memicu ---

    @Test
    void promptPalsuTanpaArmTidakMemicu() {
        output("isi log: [sudo] password for user: ");
        output("\r\n[sudo] password for user: ");

        assertThat(events).isEmpty();
    }

    @Test
    void perintahSelainSudoAtauSuTidakMeng_arm() {
        responder.onEnter(PROMPT + "cat /var/log/auth.log");
        output("\r\n[sudo] password for user: ");
        responder.onEnter(PROMPT + "echo sudo ls");
        output("\r\n[sudo] password for user: ");
        responder.onEnter(PROMPT + "sudoku");
        output("\r\nPassword: ");
        responder.onEnter(PROMPT + "super");
        output("\r\nPassword: ");

        assertThat(events).isEmpty();
    }

    @Test
    void outputSebelumEnterTidakIkutDicocokkan() {
        output("[sudo] password for user: ");
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n");

        assertThat(events).isEmpty();
    }

    @Test
    void promptSetelahWindowLewatTidakMemicu() {
        responder.onEnter(PROMPT + "sudo apt update");
        advance(PromptResponder.ARM_WINDOW.plusMillis(1));
        output("\r\n[sudo] password for user: ");

        assertThat(events).isEmpty();
        assertThat(responder.isArmed()).isFalse();
    }

    @Test
    void promptUntukUserLainTidakMemicu() {
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n[sudo] password for deploy: ");

        assertThat(events).isEmpty();
    }

    @Test
    void promptYangBukanBarisTerakhirTidakMemicu() {
        responder.onEnter(PROMPT + "sudo ./script.sh");
        output("\r\n[sudo] password for user: lalu output lain");
        output("\r\n[sudo] password for user: \r\nbaris berikutnya");

        assertThat(events).isEmpty();
    }

    @Test
    void polaSuTidakDipakaiUntukSudo() {
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\nPassword: ");

        assertThat(events).isEmpty();
    }

    @Test
    void polaSudoTidakDipakaiUntukSu() {
        responder.onEnter(PROMPT + "su -");
        output("\r\n[sudo] password for user: ");

        assertThat(events).isEmpty();
    }

    @Test
    void barisBukanPromptShellTidakMeng_arm() {
        responder.onEnter("sudo ls"); // tidak ada prompt shell di depan (mis. di dalam editor/REPL)
        output("\r\n[sudo] password for user: ");

        assertThat(events).isEmpty();
    }

    // --- one-shot & berhenti saat gagal ---

    @Test
    void passwordDitolakTidakDikirimUlang() {
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n[sudo] password for user: ");
        output("\r\nSorry, try again.\r\n[sudo] password for user: ");
        output("[sudo] password for user: ");

        assertThat(events).containsExactly("respond SUDO", "rejected SUDO");
        assertThat(responder.isArmed()).isFalse();
    }

    @Test
    void suGagalDilaporkan() {
        responder.onEnter(PROMPT + "su");
        output("\r\nPassword: ");
        output("\r\nsu: Authentication failure\r\n" + PROMPT);

        assertThat(events).containsExactly("respond SU", "rejected SU");
    }

    @Test
    void promptKeduaTanpaPesanGagalTetapTidakDikirim() {
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n[sudo] password for user: ");
        output("\r\n[sudo] password for user: ");

        assertThat(events).containsExactly("respond SUDO");
    }

    @Test
    void enterBerikutnyaMeng_armUlangUntukPerintahBaru() {
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n[sudo] password for user: ");
        output("\r\nfile.txt\r\n" + PROMPT);
        responder.onEnter(PROMPT + "sudo -k");
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n[sudo] password for user: ");

        assertThat(events).containsExactly("respond SUDO", "respond SUDO");
    }

    @Test
    void enterManualSaatPromptMen_disarm() {
        responder.onEnter(PROMPT + "sudo ls");
        responder.onEnter("[sudo] password for user: "); // user mengetik password sendiri lalu Enter
        output("\r\n[sudo] password for user: ");

        assertThat(events).isEmpty();
    }

    @Test
    void pengamatanGagalBerakhirSetelahWindow() {
        responder.onEnter(PROMPT + "sudo ls");
        output("\r\n[sudo] password for user: ");
        advance(PromptResponder.FAILURE_WINDOW.plusSeconds(1));
        output("\r\nSorry, try again.");

        assertThat(events).containsExactly("respond SUDO");
        assertThat(responder.isArmed()).isFalse();
    }

    @Test
    void jenisPerintah() {
        assertThat(PromptResponder.kindOf("sudo ls")).isEqualTo(Kind.SUDO);
        assertThat(PromptResponder.kindOf("sudo su -")).isEqualTo(Kind.SUDO);
        assertThat(PromptResponder.kindOf("su")).isEqualTo(Kind.SU);
        assertThat(PromptResponder.kindOf("su - postgres")).isEqualTo(Kind.SU);
        assertThat(PromptResponder.kindOf("sudo")).isNull();
        assertThat(PromptResponder.kindOf("sudoedit /etc/x")).isNull();
        assertThat(PromptResponder.kindOf("sum file")).isNull();
    }
}
