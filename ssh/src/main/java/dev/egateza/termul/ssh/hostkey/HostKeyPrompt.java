package dev.egateza.termul.ssh.hostkey;

/**
 * Callback ke UI saat verifikasi host key. Dipanggil dari thread I/O MINA; implementasi UI
 * harus pindah ke EDT sendiri (mis. {@code invokeAndWait}) dan boleh blocking.
 */
public interface HostKeyPrompt {

    /** Host belum ada di known_hosts: tampilkan fingerprint, return true kalau user percaya (TOFU). */
    boolean confirmUnknownHost(HostKeyInfo info);

    /** Host key berubah. Koneksi tetap ditolak; ini hanya notifikasi keras ke user. */
    void hostKeyChanged(HostKeyInfo presented, java.util.List<String> knownFingerprints);

    /** Menolak semua host baru (dipakai kalau tidak ada UI). */
    HostKeyPrompt REJECT_UNKNOWN = new HostKeyPrompt() {
        @Override
        public boolean confirmUnknownHost(HostKeyInfo info) {
            return false;
        }

        @Override
        public void hostKeyChanged(HostKeyInfo presented, java.util.List<String> knownFingerprints) {
        }
    };
}
