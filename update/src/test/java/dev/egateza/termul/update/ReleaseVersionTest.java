package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReleaseVersionTest {

    @Test
    void urutanNumerikBukanLeksikal() {
        assertThat(ReleaseVersion.parse("0.1.130").isNewerThan(ReleaseVersion.parse("0.1.99"))).isTrue();
        assertThat(ReleaseVersion.parse("0.2.1").isNewerThan(ReleaseVersion.parse("0.1.500"))).isTrue();
        assertThat(ReleaseVersion.parse("0.1.0")).isEqualByComparingTo(ReleaseVersion.parse("0.1"));
        assertThat(ReleaseVersion.parse("0.1.5").isNewerThan(ReleaseVersion.parse("0.1.5"))).isFalse();
    }

    @Test
    void tagDanTeks() {
        var v = ReleaseVersion.parse(" 0.1.124 ");
        assertThat(v).hasToString("0.1.124");
        assertThat(v.tag()).isEqualTo("v0.1.124");
    }

    @Test
    void buildDevBukanVersi() {
        assertThat(ReleaseVersion.parseOrNull("dev")).isNull();
        assertThat(ReleaseVersion.parseOrNull("${termul.versionBase}.${git.total.commit.count}")).isNull();
        assertThat(ReleaseVersion.parseOrNull(null)).isNull();
        assertThatThrownBy(() -> ReleaseVersion.parse("1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReleaseVersion.parse("1.2.3.4.5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReleaseVersion.parse("1.-2")).isInstanceOf(IllegalArgumentException.class);
    }
}
