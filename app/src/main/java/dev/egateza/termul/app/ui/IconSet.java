package dev.egateza.termul.app.ui;

/** Set ikon yang bisa dipilih user (menu Pengaturan → Set ikon). Disimpan di {@code config.json} sebagai {@link #id}. */
public enum IconSet {
    FONT_AWESOME("fontawesome", "fa", "Font Awesome", "Font Awesome Free 7.3.1 (CC BY 4.0), fontawesome.com"),
    MATERIAL("material", "material", "Material Symbols (Google)",
            "Material Symbols Rounded (Apache 2.0), fonts.google.com/icons"),
    // set orisinal TermUL: digambar manual (tidak diunduh tools/AddIcon.java)
    GARIS("garis", "garis", "Garis", "Garis: ikon orisinal TermUL"),
    DUOTON("duoton", "duoton", "Duoton", "Duoton: ikon orisinal TermUL"),
    PIKSEL("piksel", "piksel", "Piksel", "Piksel: ikon orisinal TermUL");

    private final String id;
    private final String dir;
    private final String label;
    private final String attribution;

    IconSet(String id, String dir, String label, String attribution) {
        this.id = id;
        this.dir = dir;
        this.label = label;
        this.attribution = attribution;
    }

    public String id() {
        return id;
    }

    String dir() {
        return dir;
    }

    public String label() {
        return label;
    }

    public String attribution() {
        return attribution;
    }

    /** @return set dengan id tersebut, atau {@link #FONT_AWESOME} kalau kosong/tidak dikenal */
    public static IconSet fromId(String id) {
        for (IconSet s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return FONT_AWESOME;
    }
}
