package dev.egateza.termul.app.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetScope;
import dev.egateza.termul.core.snippet.SnippetStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Bagian Snippet manager yang tidak butuh layar: urutan seret, ekspor, pesan kategori. */
class SnippetManageUiTest {

    @AfterEach
    void reset() {
        I18n.use(I18n.FALLBACK);
    }

    @Test
    void sisipanSetelahBarisAsalBergeserSatu() {
        // baris 1 diseret ke celah sebelum baris 3: setelah diambil, posisinya jadi 2
        assertThat(SnippetManagerDialog.dropIndex(1, 3)).isEqualTo(2);
        assertThat(SnippetManagerDialog.dropIndex(3, 0)).isZero();
        assertThat(SnippetManagerDialog.dropIndex(2, 2)).isEqualTo(2);
    }

    @Test
    void namaFileDanMetaEkspor() {
        I18n.use("en");
        assertThat(SnippetExportDialog.fileName(LocalDate.of(2026, 10, 27)))
                .isEqualTo("termul-snippets-2026-10-27.json");
        assertThat(SnippetExportDialog.meta(1, 512)).isEqualTo("version 2 · 1 snippet · 0.5 KB");
        assertThat(SnippetExportDialog.meta(3, 2048)).isEqualTo("version 2 · 3 snippets · 2.0 KB");
    }

    @Test
    void pilihanEksporMemakaiDaftarYangBenar() {
        var fav = new Snippet(UUID.randomUUID(), "a", "echo a", false, "", true, false, SnippetScope.ALL, Map.of());
        var plain = new Snippet(UUID.randomUUID(), "b", "echo b", false, "x", false, false, SnippetScope.ALL, Map.of());
        var sets = new SnippetExportDialog.Sets(List.of(fav, plain), List.of(plain), "X",
                SnippetExportDialog.Which.VIEW);
        assertThat(sets.of(SnippetExportDialog.Which.ALL)).containsExactly(fav, plain);
        assertThat(sets.of(SnippetExportDialog.Which.VIEW)).containsExactly(plain);
        assertThat(sets.of(SnippetExportDialog.Which.FAVORITES)).containsExactly(fav);
    }

    @Test
    void teksTombolYangTidakMuatDipotongDenganElipsis() {
        var fm = new javax.swing.JLabel().getFontMetrics(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 12));
        String text = "Paste into 2024-main-server-01-sewa";
        assertThat(SnippetUi.elide(text, fm, 10_000)).isEqualTo(text);
        String cut = SnippetUi.elide(text, fm, fm.stringWidth("Paste into 2024"));
        assertThat(cut).endsWith("…").startsWith("Paste into");
        assertThat(fm.stringWidth(cut)).isLessThanOrEqualTo(fm.stringWidth("Paste into 2024"));
        assertThat(SnippetUi.elide(text, fm, 0)).isEmpty();
    }

    @Test
    void bentukWindowMembulatSeukuranWindow() {
        var shape = SnippetUi.windowShape(400, 300);
        assertThat(shape.getBounds()).isEqualTo(new java.awt.Rectangle(0, 0, 400, 300));
        assertThat(shape.contains(0.5, 0.5)).as("sudut terpotong").isFalse();
        assertThat(shape.contains(200, 150)).isTrue();
    }

    @Test
    void pesanMasalahNamaKategori() {
        I18n.use("en");
        assertThat(SnippetCategoriesDialog.problemText(null)).isEmpty();
        assertThat(SnippetCategoriesDialog.problemText(SnippetStore.CategoryProblem.TOO_LONG))
                .isEqualTo("Category names can be at most 24 characters.");
        assertThat(SnippetCategoriesDialog.problemText(SnippetStore.CategoryProblem.DUPLICATE))
                .isEqualTo("That name is already used.");
    }
}
