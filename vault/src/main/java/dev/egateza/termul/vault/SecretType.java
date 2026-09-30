package dev.egateza.termul.vault;

/** Jenis secret per profil. Kode byte dipakai di format file: jangan diubah urutannya. */
public enum SecretType {
    LOGIN_PASSWORD(1),
    KEY_PASSPHRASE(2),
    SUDO_PASSWORD(3),
    ROOT_PASSWORD(4);

    private final int code;

    SecretType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static SecretType fromCode(int code) {
        for (SecretType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("SecretType tidak dikenal: " + code);
    }
}
