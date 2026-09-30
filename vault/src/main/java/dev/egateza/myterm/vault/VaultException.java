package dev.egateza.myterm.vault;

/** Operasi vault gagal. Pesan untuk user (Bahasa Indonesia), tidak pernah berisi secret. */
public class VaultException extends RuntimeException {

    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Master password salah (atau header vault dimodifikasi). */
    public static final class WrongPassword extends VaultException {
        public WrongPassword() {
            super("Master password salah.");
        }
    }

    /** Vault terkunci: panggil unlock dulu. */
    public static final class Locked extends VaultException {
        public Locked() {
            super("Vault terkunci.");
        }
    }
}
