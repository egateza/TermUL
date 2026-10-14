package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransferListTest {

    private final TransferList list = new TransferList();

    @Test
    void urutanAntreanDanStatusBerjalan() {
        list.add(1, "Upload backup.sql.gz");
        list.add(2, "Download nginx.conf");
        list.start(1);
        list.progress(1, 64, 100);

        assertThat(list.items()).extracting(TransferList.Item::title)
                .containsExactly("Upload backup.sql.gz", "Download nginx.conf");
        assertThat(list.items().get(0).running()).isTrue();
        assertThat(list.items().get(0).percent()).isEqualTo(64);
        assertThat(list.items().get(1).running()).isFalse();
    }

    @Test
    void selesaiDihapusDariDaftar() {
        list.add(1, "a");
        list.add(2, "b");
        list.remove(1);

        assertThat(list.items()).extracting(TransferList.Item::id).containsExactly(2L);
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void totalBelumDiketahuiTanpaPersen() {
        list.add(1, "stream");
        list.progress(1, 4096, 0);

        assertThat(list.items().getFirst().percent()).isEqualTo(-1);
    }

    @Test
    void labelTransfer() {
        assertThat(TransferBadge.statusText(new TransferList.Item(1, "x", false, 0, 0)))
                .isEqualTo(dev.egateza.termul.app.i18n.I18n.t("fab.transfers.queued"));
        assertThat(TransferBadge.statusText(new TransferList.Item(1, "x", true, 50, 200))).isEqualTo("25%");
        assertThat(TransferBadge.shorten("a".repeat(60))).hasSize(TransferBadge.MAX_TITLE).contains("…");
        assertThat(TransferBadge.shorten("Download " + "x".repeat(60) + ".jar.filepart")).endsWith(".jar.filepart");
        assertThat(TransferBadge.shorten("pendek.txt")).isEqualTo("pendek.txt");
    }
}
