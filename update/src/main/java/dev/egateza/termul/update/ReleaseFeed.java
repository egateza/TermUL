package dev.egateza.termul.update;

import java.security.PublicKey;

/** Sumber rilis terbaru beserta jar-nya ({@link UpdateClient} di produksi). */
public interface ReleaseFeed extends AssetSource {

    /** Manifest rilis terbaru yang tanda tangannya sudah diverifikasi dengan {@code key}. */
    SignedRelease fetchLatest(PublicKey key) throws UpdateException, InterruptedException;
}
