package dev.egateza.myterm.core.profile;

/** Tag environment server; menentukan warna tab dan default guard keamanan. */
public enum EnvironmentTag {
    PROD,
    STAGING,
    DEV,
    NONE;

    /** Host produksi mendapat guard ekstra (konfirmasi paste, auto-sudo default OFF). */
    public boolean isProduction() {
        return this == PROD;
    }
}
