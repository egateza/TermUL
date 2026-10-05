package dev.egateza.termul.update;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Tanda tangan Ed25519 atas byte persis {@code manifest.json}. File {@code .sig} berisi signature dalam Base64
 * (satu baris). Ed25519 ada di {@code java.base} sejak JDK 22, jadi tidak butuh modul tambahan di runtime jlink.
 */
public final class ManifestSignature {

    public static final String ALGORITHM = "Ed25519";
    /** Signature Ed25519 = 64 byte; Base64 + whitespace tidak mungkin lebih panjang dari ini. */
    static final int MAX_SIGNATURE_TEXT = 256;

    private ManifestSignature() {
    }

    /**
     * Verifikasi lalu parse manifest.
     *
     * @throws UpdateException kalau tanda tangan tidak valid atau manifest rusak
     */
    public static UpdateManifest verify(byte[] manifestJson, byte[] signatureText, PublicKey key)
            throws UpdateException {
        if (!isValid(manifestJson, signatureText, key)) {
            throw new UpdateException("Tanda tangan update tidak valid. Update ditolak: file mungkin rusak atau "
                    + "bukan dari pembuat TermUL.");
        }
        return UpdateManifest.parse(manifestJson);
    }

    static boolean isValid(byte[] manifestJson, byte[] signatureText, PublicKey key) {
        if (signatureText == null || signatureText.length > MAX_SIGNATURE_TEXT) {
            return false;
        }
        try {
            byte[] sig = Base64.getDecoder().decode(new String(signatureText, StandardCharsets.US_ASCII).strip());
            var verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(manifestJson);
            return verifier.verify(sig);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** @return isi file {@code .sig} (Base64 + newline) */
    public static byte[] sign(byte[] manifestJson, PrivateKey key) throws GeneralSecurityException {
        var signer = Signature.getInstance(ALGORITHM);
        signer.initSign(key);
        signer.update(manifestJson);
        return (Base64.getEncoder().encodeToString(signer.sign()) + "\n").getBytes(StandardCharsets.US_ASCII);
    }

    /** @param base64 public key X.509 (SubjectPublicKeyInfo) dalam Base64 */
    public static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Public key update tidak valid", e);
        }
    }

    /** @param pkcs8 private key PKCS#8 (DER) */
    public static PrivateKey privateKey(byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }
}
