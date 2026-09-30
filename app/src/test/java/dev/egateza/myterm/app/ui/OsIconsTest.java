package dev.egateza.myterm.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.myterm.core.profile.OsInfo;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class OsIconsTest {

    @Test
    void distroDikenalMemakaiWarnaKhas() {
        var debian = OsIcons.of(new OsInfo("debian", "12", "Debian GNU/Linux 12"));
        int center = paintCenter(debian);

        assertThat(center & 0xFFFFFF).isNotEqualTo(0); // badge terisi, bukan transparan
        assertThat(OsIcons.of(new OsInfo("debian", null, null))).isSameAs(debian); // cache per id
        assertThat(OsIcons.STYLES).containsKeys("ubuntu", "debian", "alpine", "centos", "rocky", "rhel");
    }

    @Test
    void ubuntuMemakaiLogoMultiResolusi() {
        var img = OsIcons.loadLogo(OsIcons.LOGOS.get("ubuntu"));
        assertThat(img).isInstanceOf(java.awt.image.MultiResolutionImage.class);
        var variants = ((java.awt.image.MultiResolutionImage) img).getResolutionVariants();
        assertThat(variants).extracting(v -> v.getWidth(null)).containsExactly(14, 28, 42, 56);

        var icon = OsIcons.of(new OsInfo("ubuntu", "24.04", null));
        assertThat(icon.getClass().getSimpleName()).isEqualTo("Logo");
        assertThat(icon.getIconWidth()).isEqualTo(14);
        assertThat(OsIcons.loadLogo("/tidak/ada.png")).isNull(); // fallback ke badge
    }

    @Test
    void distroTakDikenalDanBelumTerdeteksi() {
        var unknown = OsIcons.of(new OsInfo("gentoo", null, null));
        assertThat(unknown.getIconWidth()).isEqualTo(14);
        paintCenter(unknown);

        var undetected = OsIcons.of(null);
        assertThat(paintCenter(undetected) >>> 24).isZero(); // lingkaran outline: tengahnya transparan
        assertThat(OsIcons.label(null)).contains("belum terdeteksi");
        assertThat(OsIcons.label(new OsInfo("ubuntu", "22.04", "Ubuntu 22.04.4 LTS"))).isEqualTo("Ubuntu 22.04.4 LTS");
    }

    private static int paintCenter(javax.swing.Icon icon) {
        var img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2 + 3);
    }
}
