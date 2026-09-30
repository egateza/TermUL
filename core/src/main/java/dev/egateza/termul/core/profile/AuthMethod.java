package dev.egateza.termul.core.profile;

/** Metode autentikasi SSH yang dipakai sebuah profil. */
public enum AuthMethod {
    /** Private key dari file (opsional dengan passphrase). */
    KEY,
    /** SSH agent (Windows OpenSSH agent / Pageant). */
    AGENT,
    /** Password (dari vault atau prompt). */
    PASSWORD
}
