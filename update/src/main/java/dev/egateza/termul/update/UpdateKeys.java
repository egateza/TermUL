package dev.egateza.termul.update;

import java.security.PublicKey;

/**
 * Public key Ed25519 untuk memverifikasi rilis. Pasangan private key-nya hanya ada di mesin rilis
 * ({@code ~/.termul-release/update-signing.key}), dibuat dengan {@code ReleaseTool keygen}. Mengganti key =
 * instalasi lama tidak bisa update lewat menu lagi: naikkan {@link UpdateProtocol#GENERATION}.
 */
public final class UpdateKeys {

    /** X.509 SubjectPublicKeyInfo, Base64. */
    static final String PUBLIC_KEY = "MCowBQYDK2VwAyEABidr6rPPA12tc95iO5A+6moPoTd3R5mNRL6x6LJJGuc=";

    private UpdateKeys() {
    }

    public static PublicKey publicKey() {
        return ManifestSignature.publicKey(PUBLIC_KEY);
    }
}
