package dev.egateza.termul.update;

import java.security.PublicKey;

/**
 * Manifest rilis yang tanda tangannya <b>sudah</b> diverifikasi. Hanya bisa dibuat lewat {@link #verify}, jadi tipe
 * ini sendiri adalah bukti verifikasi.
 *
 * @param manifestJson byte asli (disimpan apa adanya supaya Bootstrap bisa memverifikasi ulang)
 */
public record SignedRelease(byte[] manifestJson, byte[] signature, UpdateManifest manifest) {

    public SignedRelease {
        manifestJson = manifestJson.clone();
        signature = signature.clone();
    }

    public static SignedRelease verify(byte[] manifestJson, byte[] signature, PublicKey key) throws UpdateException {
        return new SignedRelease(manifestJson, signature, ManifestSignature.verify(manifestJson, signature, key));
    }

    @Override
    public byte[] manifestJson() {
        return manifestJson.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }

    public ReleaseVersion version() {
        return manifest.version();
    }
}
