package dev.egateza.myterm.ssh.hostkey;

import java.util.List;

/** Hasil verifikasi host key. */
public sealed interface HostKeyVerdict {

    HostKeyInfo presented();

    /** Key cocok dengan known_hosts, atau host baru yang dikonfirmasi user (TOFU). */
    record Trusted(HostKeyInfo presented, boolean newlyAdded) implements HostKeyVerdict {
    }

    /** Host sudah dikenal tapi key berbeda: selalu ditolak. */
    record Changed(HostKeyInfo presented, List<String> knownFingerprints) implements HostKeyVerdict {
        public Changed {
            knownFingerprints = List.copyOf(knownFingerprints);
        }
    }

    /** Host baru yang ditolak user (atau tidak ada UI untuk konfirmasi). */
    record RejectedByUser(HostKeyInfo presented) implements HostKeyVerdict {
    }
}
