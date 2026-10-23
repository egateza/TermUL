package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.sftp.SftpPanel;
import dev.egateza.termul.terminal.Osc7Parser;
import dev.egateza.termul.terminal.ShellCwd;
import dev.egateza.termul.terminal.SshTtyConnector;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Ikuti folder terminal" untuk satu tab SSH: panel SFTP di samping terminal pindah ke folder kerja shell.
 *
 * <p>Dua sumber folder, tanpa mengetik apa pun ke shell user:
 * <ol>
 *   <li>OSC 7 dari shell ({@link Osc7Parser}), kalau shell-nya mengirim (zsh/fish dengan integrasi terminal, atau
 *       bash dengan {@code PROMPT_COMMAND}). Begitu terlihat sekali, probe exec tidak dipakai lagi untuk shell itu.</li>
 *   <li>Probe {@code /proc} lewat exec channel ({@link ShellCwd}), sesaat setelah user menekan Enter di prompt.</li>
 * </ol>
 * Probe hanya berjalan selama fitur aktif, panel SFTP terbuka, dan terminal tidak di layar alternatif (vim, htop,
 * less), dan paling banyak satu sekaligus. Semua method dipanggil di EDT.
 */
final class CwdFollower {

    private static final Logger log = LoggerFactory.getLogger(CwdFollower.class);

    /** Jeda setelah Enter sebelum probe: cukup untuk {@code cd}, dan Enter beruntun digabung jadi satu probe. */
    static final int PROBE_DELAY_MS = 400;

    private final Executor sshOps;
    private final Supplier<SftpPanel> panel;         // null kalau panel SFTP tidak terbuka
    private final BooleanSupplier alternateScreen;   // true selama program layar penuh berjalan
    private final Timer timer = new Timer(PROBE_DELAY_MS, e -> probe());
    private boolean enabled;
    private SshTtyConnector tty;     // shell saat ini; null sebelum connect
    private Osc7Parser osc7;         // terpasang di tty
    private boolean osc7Seen;        // shell ini mengirim OSC 7
    private String lastOsc7;         // folder terakhir dari OSC 7 (dipakai saat fitur baru dinyalakan)
    private boolean probing;         // satu probe sedang berjalan
    private boolean again;           // Enter lagi selama probe berjalan: probe sekali lagi setelahnya
    private Class<?> lastNotice;     // jenis keterangan terakhir di panel, supaya tidak diulang setiap Enter

    CwdFollower(Executor sshOps, Supplier<SftpPanel> panel, BooleanSupplier alternateScreen) {
        this.sshOps = sshOps;
        this.panel = panel;
        this.alternateScreen = alternateScreen;
        timer.setRepeats(false);
    }

    /** Shell baru (connect / reconnect): pasang pembaca OSC 7 dan lupakan status shell lama. */
    void attach(SshTtyConnector connector) {
        detach();
        tty = connector;
        osc7Seen = false;
        lastOsc7 = null;
        lastNotice = null;
        var target = connector;
        osc7 = new Osc7Parser(path -> SwingUtilities.invokeLater(() -> onOsc7(target, path)));
        connector.addOutputListener(osc7);
        if (enabled) {
            timer.restart(); // shell baru di folder awal profil
        }
    }

    void detach() {
        timer.stop();
        if (tty != null && osc7 != null) {
            tty.removeOutputListener(osc7);
        }
        tty = null;
        osc7 = null;
    }

    boolean isEnabled() {
        return enabled;
    }

    /** Nyalakan/matikan. Saat dinyalakan, panel langsung menyusul folder terminal. */
    void setEnabled(boolean on) {
        enabled = on;
        lastNotice = null;
        if (!on) {
            timer.stop();
        } else {
            refresh();
        }
    }

    /** Panel SFTP baru dibuka, atau fitur baru dinyalakan: susul folder terminal sekarang. */
    void refresh() {
        if (!enabled) {
            return;
        }
        if (osc7Seen && lastOsc7 != null) {
            var p = panel.get();
            if (p != null) {
                p.followTo(lastOsc7);
            }
        } else {
            probe();
        }
    }

    /** User menekan Enter di terminal. */
    void commandEntered() {
        if (enabled && !osc7Seen && panel.get() != null) {
            timer.restart();
        }
    }

    private void onOsc7(SshTtyConnector source, String path) {
        if (source != tty) {
            return; // OSC 7 dari shell lama yang masih tertinggal di antrean
        }
        osc7Seen = true;
        lastOsc7 = path;
        timer.stop();
        var p = panel.get();
        if (enabled && p != null) {
            p.followTo(path);
        }
    }

    private void probe() {
        var target = tty;
        if (!enabled || target == null || target.shellToken() == null || !target.isConnected()
                || panel.get() == null || alternateScreen.getAsBoolean()) {
            return;
        }
        if (probing) {
            again = true;
            return;
        }
        probing = true;
        try {
            sshOps.execute(() -> {
                ShellCwd.Result result;
                try {
                    result = ShellCwd.probe(target.lease().connection(), target.shellToken());
                } catch (IOException | IllegalStateException e) { // exec gagal, atau lease sudah dilepas
                    log.debug("Probe folder terminal {} gagal: {}", target.getName(), e.toString());
                    result = null;
                }
                var r = result;
                SwingUtilities.invokeLater(() -> probed(target, r));
            });
        } catch (RejectedExecutionException e) {
            probing = false; // aplikasi sedang ditutup
        }
    }

    /** @param result null = probe gagal (koneksi), dicoba lagi pada Enter berikutnya */
    private void probed(SshTtyConnector target, ShellCwd.Result result) {
        probing = false;
        if (target == tty && enabled) {
            var p = panel.get();
            if (p != null && result != null) {
                switch (result) {
                    case ShellCwd.Result.Found f -> {
                        lastNotice = null;
                        p.followTo(f.path());
                    }
                    case ShellCwd.Result.Unsupported u -> notice(p, u, "sftp.follow.unsupported");
                    case ShellCwd.Result.Unreadable u -> notice(p, u, "sftp.follow.unreadable");
                }
            }
        }
        if (again) {
            again = false;
            probe();
        }
    }

    private void notice(SftpPanel p, ShellCwd.Result result, String key) {
        if (lastNotice != result.getClass()) {
            lastNotice = result.getClass();
            p.followNotice(I18n.t(key));
        }
    }
}
