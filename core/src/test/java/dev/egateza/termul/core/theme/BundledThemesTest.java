package dev.egateza.termul.core.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BundledThemesTest {

    @Test
    void everyIndexedTemplateLoadsAndIsValid() {
        List<String> index = BundledThemes.index();
        List<CustomTheme> templates = BundledThemes.load();

        assertThat(index).isNotEmpty().doesNotHaveDuplicates();
        // template yang rusak dilewati diam-diam saat runtime, jadi di sini jumlahnya harus sama persis
        assertThat(templates).extracting(CustomTheme::id).containsExactlyElementsOf(index);
        assertThat(templates).extracting(CustomTheme::name).doesNotHaveDuplicates();
    }

    @Test
    void templatesHaveNoBackgroundImage() {
        // path gambar milik mesin pembuat template; tidak boleh ikut terbawa ke user lain
        assertThat(BundledThemes.load()).allSatisfy(t -> assertThat(t.terminal().hasBackdrop()).isFalse());
    }
}
