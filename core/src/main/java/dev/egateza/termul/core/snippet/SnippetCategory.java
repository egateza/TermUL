package dev.egateza.termul.core.snippet;

/** Kategori snippet; {@code id} dipakai snippet dan file ekspor, {@code name} yang tampil. */
public record SnippetCategory(String id, String name) {

    public static final int MAX_NAME = 40;

    public SnippetCategory {
        id = id == null ? "" : id.strip();
        name = name == null ? "" : name.strip();
        if (id.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("Kategori snippet wajib punya id dan nama.");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Nama kategori maksimal " + MAX_NAME + " karakter.");
        }
        if (Snippet.hasControlCharacter(id) || Snippet.hasControlCharacter(name)) {
            throw new IllegalArgumentException("Kategori snippet tidak boleh berisi karakter kontrol.");
        }
    }
}
