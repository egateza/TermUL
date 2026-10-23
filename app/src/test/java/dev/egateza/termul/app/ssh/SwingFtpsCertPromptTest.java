package dev.egateza.termul.app.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SwingFtpsCertPromptTest {

    @Test
    void fingerprintJugaDitampilkanDalamFormatOpenssl() {
        // SHA256 dari 32 byte 0x00..0x1F, base64 tanpa padding
        assertThat(SwingFtpsCertPrompt.opensslHex("SHA256:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"))
                .isEqualTo("00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:"
                        + "10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F");
        assertThat(SwingFtpsCertPrompt.opensslHex("SHA256:bukan base64!")).isEmpty();
    }
}
