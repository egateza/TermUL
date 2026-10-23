package dev.egateza.termul.app.ssh;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Edt;
import dev.egateza.termul.sftp.ftp.FtpsCertPrompt;
import java.awt.Component;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/** Dialog TOFU sertifikat FTPS. Dipanggil dari thread handshake TLS; dialog ditampilkan di EDT (blocking). */
public final class SwingFtpsCertPrompt implements FtpsCertPrompt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final Supplier<Component> parent;

    public SwingFtpsCertPrompt(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public boolean confirmUntrusted(CertInfo info) {
        var accepted = new AtomicBoolean();
        Edt.runAndWait(() -> {
            String message = I18n.t("ftps.cert.message", info.hostLabel(), info.reason(), info.subject(), info.issuer(),
                    DATE.format(info.notBefore()), DATE.format(info.notAfter()), info.fingerprint(),
                    opensslHex(info.fingerprint()));
            Object[] options = {I18n.t("ftps.cert.trust"), I18n.t("common.cancel")};
            int choice = JOptionPane.showOptionDialog(parent.get(), message, I18n.t("ftps.cert.title", info.hostLabel()),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            accepted.set(choice == 0);
        });
        return accepted.get();
    }

    /** {@code SHA256:<base64>} dalam format {@code openssl x509 -fingerprint -sha256} ({@code AB:CD:...}). */
    static String opensslHex(String fingerprint) {
        String b64 = fingerprint.startsWith("SHA256:") ? fingerprint.substring("SHA256:".length()) : fingerprint;
        try {
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(Base64.getDecoder().decode(b64));
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
