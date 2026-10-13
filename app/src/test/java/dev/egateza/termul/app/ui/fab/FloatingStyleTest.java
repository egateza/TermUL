package dev.egateza.termul.app.ui.fab;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.egateza.termul.app.themes.NeoBrutalismLaf;
import dev.egateza.termul.app.themes.NeoBrutalismRetroLaf;
import java.awt.Color;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FloatingStyleTest {

    @Test
    void temaTanpaKeyMemakaiAksenDanBayanganLembut() {
        Map<String, Object> theme = Map.of("Component.accentColor", new Color(0x2675BF),
                "Panel.background", Color.WHITE, "Label.foreground", Color.BLACK);

        var primary = FloatingStyle.resolve(FloatingStyle.Kind.PRIMARY, theme::get);
        var secondary = FloatingStyle.resolve(FloatingStyle.Kind.SECONDARY, theme::get);

        assertThat(primary.background()).isEqualTo(new Color(0x2675BF));
        assertThat(primary.foreground()).isEqualTo(Color.WHITE);
        assertThat(primary.hardShadow()).isFalse();
        assertThat(primary.borderWidth()).isZero();
        assertThat(primary.arc()).isEqualTo(999);
        assertThat(secondary.background()).isEqualTo(Color.WHITE);
        assertThat(secondary.borderWidth()).as("tombol bantu tetap terlihat di atas terminal").isEqualTo(1);
    }

    @Test
    void tanpaWarnaAksenTetapAdaWarna() {
        var style = FloatingStyle.resolve(FloatingStyle.Kind.PRIMARY, key -> null);

        assertThat(style.background()).isEqualTo(FloatingStyle.FALLBACK_ACCENT);
    }

    @Test
    void neobrutalismMemakaiBorderTebalDanBayanganTegas() {
        var defaults = new NeoBrutalismLaf().getDefaults();

        var style = FloatingStyle.resolve(FloatingStyle.Kind.PRIMARY, defaults::get);

        assertThat(style.background()).isEqualTo(new Color(0xFFDE00));
        assertThat(style.borderWidth()).isEqualTo(2);
        assertThat(style.hardShadow()).isTrue();
        assertThat(style.shadowOffset()).isEqualTo(4);
        assertThat(style.shadowColor()).isEqualTo(Color.BLACK);
    }

    @Test
    void retroDatarTanpaBayangan() {
        var defaults = new NeoBrutalismRetroLaf().getDefaults();

        var style = FloatingStyle.resolve(FloatingStyle.Kind.PRIMARY, defaults::get);

        assertThat(style.hardShadow()).isFalse();
        assertThat(style.shadowColor().getAlpha()).isZero();
    }

    @Test
    void temaGelapBawaanTetapTerbaca() {
        var defaults = new FlatDarkLaf().getDefaults();

        var style = FloatingStyle.resolve(FloatingStyle.Kind.PRIMARY, defaults::get);

        assertThat(style.foreground()).isEqualTo(FloatingStyle.readableOn(style.background()));
    }
}
