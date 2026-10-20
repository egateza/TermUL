package dev.egateza.termul.core.highlight;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * Profil highlight: daftar keyword berurutan (yang di atas menang kalau tumpang tindih). Formatnya sama dengan profil
 * plugin Tabby ({@code {id, name, keywords}}).
 *
 * @param id id teks (UUID dari Tabby atau TermUL)
 */
public record HighlightProfile(@JsonProperty("id") String id, @JsonProperty("name") String name,
                               @JsonProperty("keywords") List<HighlightKeyword> keywords) {

    public HighlightProfile {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        name = name == null || name.isBlank() ? "Profil" : name.strip();
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public static HighlightProfile create(String name, List<HighlightKeyword> keywords) {
        return new HighlightProfile(UUID.randomUUID().toString(), name, keywords);
    }

    public HighlightProfile withName(String value) {
        return new HighlightProfile(id, value, keywords);
    }

    public HighlightProfile withKeywords(List<HighlightKeyword> value) {
        return new HighlightProfile(id, name, value);
    }

    /** Profil bawaan (sama seperti default Tabby, tanpa keyword Mandarin). */
    public static HighlightProfile defaults() {
        return new HighlightProfile("60606be0-c0ff-42bc-bf77-de8a2435447f", "Default", List.of(
                HighlightKeyword.of("ERROR", "1"),
                HighlightKeyword.of("WARN", "3"),
                HighlightKeyword.of("INFO", "6")));
    }
}
