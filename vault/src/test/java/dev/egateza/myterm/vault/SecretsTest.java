package dev.egateza.myterm.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecretsTest {

    @Test
    void roundTripUtf8() {
        char[] pw = "pässwörd-€".toCharArray();
        byte[] bytes = Secrets.toUtf8(pw);

        assertThat(bytes).isEqualTo("pässwörd-€".getBytes(StandardCharsets.UTF_8));
        assertThat(Secrets.fromUtf8(bytes)).containsExactly(pw);
    }

    @Test
    void suffixCarriageReturn() {
        char[] pw = "abc".toCharArray();

        assertThat(Secrets.toUtf8WithSuffix(pw, (byte) '\r')).containsExactly('a', 'b', 'c', '\r');
        assertThat(pw).containsExactly('a', 'b', 'c'); // input tidak diubah
    }

    @Test
    void surrogateTidakLengkapDitolak() {
        assertThatThrownBy(() -> Secrets.toUtf8(new char[] {'\uD800'})).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("\uD800");
    }

    @Test
    void zeroAmanUntukNull() {
        Secrets.zero((char[]) null);
        Secrets.zero((byte[]) null);
        char[] c = {'x'};
        Secrets.zero(c);
        assertThat(c).containsOnly('\0');
    }
}
