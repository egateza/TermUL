package dev.egateza.termul.core.profile;

/** Tag environment server; menentukan warna tab dan default guard keamanan. */
public enum EnvironmentTag {
    PROD,
    STAGING,
    DEV,
    NONE;

    /** Host produksi; default profil sensitif kalau flag-nya tidak diisi ({@link HostProfile#needsGuard()}). */
    public boolean isProduction() {
        return this == PROD;
    }
}
