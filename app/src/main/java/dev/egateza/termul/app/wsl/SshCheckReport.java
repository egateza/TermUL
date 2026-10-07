package dev.egateza.termul.app.wsl;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.wsl.SshCheck;
import dev.egateza.termul.core.wsl.SshCheck.Problem;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Hasil "Periksa SSH" untuk satu distro dan port profil: daftar cek ✔/✘ dan perintah perbaikan yang bisa di-copy.
 * Perintah shell tidak diterjemahkan; komentar dan teks dari i18n.
 *
 * @param reachable port terjangkau dari Windows lewat {@code localhost}
 */
record SshCheckReport(String distro, int port, SshCheck check, boolean reachable) {

    /** true kalau SSH ke port ini seharusnya bisa. */
    boolean ok() {
        return check.problems(port).isEmpty() && reachable;
    }

    /** Daftar cek, satu baris per cek. */
    String checklist() {
        var lines = new ArrayList<String>();
        String p = String.valueOf(port);
        lines.add(mark(check.installed()) + I18n.t("wsl.check.installed"));
        if (check.installed()) {
            lines.add(mark(check.configValid()) + I18n.t("wsl.check.config"));
            if (check.configValid()) {
                lines.add(mark(check.configPorts().contains(port)) + I18n.t("wsl.check.port", p, ports(check.configPorts())));
            }
            if (check.socketActive()) {
                // socket dengan override ListenStream ke port profil juga sah: hanya masalah kalau port tidak listen
                lines.add(Boolean.TRUE.equals(check.isListening(port))
                        ? "\u2139  " + I18n.t("wsl.check.socket.ok", p)
                        : mark(false) + I18n.t("wsl.check.socket"));
            }
            Boolean listening = check.isListening(port);
            if (listening == null) {
                lines.add("?  " + I18n.t("wsl.check.listen.unknown", p));
            } else {
                lines.add(mark(listening) + I18n.t("wsl.check.listen", p, ports(check.listening())));
            }
        }
        lines.add(mark(reachable) + I18n.t("wsl.check.reachable", p));
        return String.join("\n", lines);
    }

    /** Penjelasan singkat masalah pertama, untuk pesan error saat connect; null kalau tidak ada masalah di distro. */
    String firstProblem() {
        var problems = check.problems(port);
        return problems.isEmpty() ? null : describe(problems.getFirst());
    }

    String describe(Problem problem) {
        String p = String.valueOf(port);
        return switch (problem) {
            case NOT_INSTALLED -> I18n.t("wsl.problem.notInstalled");
            case CONFIG_INVALID -> I18n.t("wsl.problem.configInvalid");
            case PORT_NOT_CONFIGURED -> I18n.t("wsl.problem.portNotConfigured", p, ports(check.configPorts()));
            case SOCKET_ACTIVATION -> I18n.t("wsl.problem.socket", p, ports(check.listening()));
            case NOT_LISTENING -> I18n.t("wsl.problem.notListening", p);
        };
    }

    /** Perintah perbaikan, urut dijalankan; kosong kalau semua cek di distro lolos. */
    String fixCommands() {
        var problems = check.problems(port);
        if (problems.isEmpty()) {
            return "";
        }
        if (problems.contains(Problem.NOT_INSTALLED)) {
            return WslManagerDialog.guideCommands(port);
        }
        var lines = new ArrayList<String>();
        if (problems.contains(Problem.CONFIG_INVALID)) {
            lines.add("# " + I18n.t("wsl.fix.configInvalid"));
            lines.add("sudo sshd -t");
            return String.join("\n", lines);
        }
        if (problems.contains(Problem.PORT_NOT_CONFIGURED)) {
            lines.add("# " + I18n.t("wsl.fix.port", String.valueOf(port)));
            lines.add("sudo sed -i -E 's/^#?Port .*/Port " + port + "/' /etc/ssh/sshd_config");
        }
        if (check.socketActive()) {
            lines.add("# " + I18n.t("wsl.fix.socket"));
            lines.add("sudo systemctl disable --now ssh.socket && sudo systemctl enable ssh.service");
        }
        lines.add("# " + I18n.t("wsl.fix.restart"));
        lines.add(check.systemd() ? "sudo systemctl restart ssh" : "sudo service ssh restart");
        lines.add("ss -ltn | grep :" + port);
        return String.join("\n", lines);
    }

    /**
     * Perintah opsional saat semuanya sudah jalan tapi sshd lewat {@code ssh.socket}: pindah ke {@code ssh.service}
     * supaya port dibaca dari {@code sshd_config}. Kosong kalau tidak relevan.
     */
    String optionalCommands() {
        if (!check.socketActive() || !check.problems(port).isEmpty()) {
            return "";
        }
        var lines = new ArrayList<String>();
        lines.add("# " + I18n.t("wsl.fix.socket.optional"));
        if (!check.configPorts().contains(port)) {
            lines.add("sudo sed -i -E 's/^#?Port .*/Port " + port + "/' /etc/ssh/sshd_config");
        }
        lines.add("sudo systemctl disable --now ssh.socket && sudo systemctl enable --now ssh.service");
        lines.add("ss -ltn | grep :" + port);
        return String.join("\n", lines);
    }

    /**
     * Port lain yang sudah dipakai sshd (dari sshd_config dan sedang listen), untuk ditawarkan sebagai pengganti port
     * profil; null kalau port profil sudah benar atau tidak ada pengganti yang jelas.
     */
    Integer suggestedPort() {
        if (!check.problems(port).contains(Problem.PORT_NOT_CONFIGURED)) {
            return null;
        }
        Integer other = WslController.pickPort(check.configPorts());
        return other != null && other != port && !Boolean.FALSE.equals(check.isListening(other)) ? other : null;
    }

    /** Port listen di distro tapi tidak terjangkau dari Windows. */
    boolean unreachableOnly() {
        return check.problems(port).isEmpty() && !reachable;
    }

    private static String mark(boolean ok) {
        return ok ? "✔  " : "✘  ";
    }

    private static String ports(java.util.Set<Integer> ports) {
        return ports == null || ports.isEmpty() ? "-"
                : ports.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
