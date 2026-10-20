package dev.egateza.termul.core.highlight;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Satu keyword highlight, dengan nama field sama seperti plugin Tabby (tabby-highlight) supaya JSON-nya bisa
 * diimpor/ekspor apa adanya. Warna ({@code foregroundColor}/{@code backgroundColor}) hanya dipakai kalau saklarnya
 * ({@code foreground}/{@code background}) aktif, sama seperti di Tabby.
 *
 * @param text          teks atau regex yang dicari
 * @param regExp        {@code text} adalah regex (Java), bukan teks biasa
 * @param caseSensitive huruf besar/kecil dibedakan
 * @param js            keyword JavaScript dari Tabby; tidak didukung, disimpan agar ekspor tetap utuh, tidak pernah aktif
 * @param matchGroup    hanya grup regex {@code matchGroupName} (nomor atau nama) yang diwarnai
 * @param remark        catatan bebas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HighlightKeyword(
        @JsonProperty("text") String text,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("isJS") boolean js,
        @JsonProperty("isRegExp") boolean regExp,
        @JsonProperty("isCaseSensitive") boolean caseSensitive,
        @JsonProperty("foreground") boolean foreground,
        @JsonProperty("foregroundColor") String foregroundColor,
        @JsonProperty("background") boolean background,
        @JsonProperty("backgroundColor") String backgroundColor,
        @JsonProperty("bold") boolean bold,
        @JsonProperty("italic") boolean italic,
        @JsonProperty("underline") boolean underline,
        @JsonProperty("underlineStyle") String underlineStyle,
        @JsonProperty("dim") boolean dim,
        @JsonProperty("inverse") boolean inverse,
        @JsonProperty("invisible") boolean invisible,
        @JsonProperty("isMatchGroup") boolean matchGroup,
        @JsonProperty("matchGroup") String matchGroupName,
        @JsonProperty("remark") String remark) {

    public HighlightKeyword {
        text = text == null ? "" : text;
    }

    /** Keyword baru: teks biasa, tidak case sensitive, latar merah (sama seperti default Tabby). */
    public static HighlightKeyword of(String text, String backgroundColor) {
        return new HighlightKeyword(text, true, false, false, false, false, null, true, backgroundColor, false, false,
                false, null, false, false, false, false, null, null);
    }

    /** true kalau keyword ini ikut dicocokkan (aktif, tidak kosong, dan bukan JavaScript). */
    @JsonIgnore
    public boolean usable() {
        return enabled && !js && !text.isEmpty();
    }

    public HighlightKeyword withEnabled(boolean value) {
        return new HighlightKeyword(text, value, js, regExp, caseSensitive, foreground, foregroundColor, background,
                backgroundColor, bold, italic, underline, underlineStyle, dim, inverse, invisible, matchGroup,
                matchGroupName, remark);
    }

    public HighlightKeyword withRegExp(boolean value) {
        return new HighlightKeyword(text, enabled, js, value, caseSensitive, foreground, foregroundColor, background,
                backgroundColor, bold, italic, underline, underlineStyle, dim, inverse, invisible, matchGroup,
                matchGroupName, remark);
    }

    public HighlightKeyword withCaseSensitive(boolean value) {
        return new HighlightKeyword(text, enabled, js, regExp, value, foreground, foregroundColor, background,
                backgroundColor, bold, italic, underline, underlineStyle, dim, inverse, invisible, matchGroup,
                matchGroupName, remark);
    }
}
