package dev.egateza.termul.app.snippet;

/** Terminal tempat snippet ditempel (tab SSH atau lokal). Semua method dipanggil di EDT. */
public interface SnippetTarget {

    /** Nama untuk teks UI, mis. nama host. */
    String name();

    /** Keterangan kedua, mis. {@code user@host:port}. */
    String address();

    /** Kunci nilai variabel yang diingat (per host / profil lokal). */
    String hostKey();

    /** Nama host untuk cakupan "hanya host ini"; {@code ""} kalau bukan host (tab lokal). */
    String hostName();

    /** Grup host untuk cakupan grup; {@code ""} kalau tanpa grup. */
    String hostGroup();

    /** true kalau terminal tersambung/berjalan, jadi teks bisa dikirim. */
    boolean canPaste();

    /** true kalau shell sedang meminta bracketed paste (baris-baris menunggu di prompt). */
    boolean bracketedPaste();

    /** Kirim teks apa adanya ke shell lalu fokus ke terminal. */
    void paste(String text);

    /** Fokus ke terminal (setelah menu snippet ditutup). */
    void focus();
}
