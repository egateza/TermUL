package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.app.ui.HostTerminalColors;
import dev.egateza.termul.app.ui.SecretChange;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.ssh.ConnectionTester;
import dev.egateza.termul.vault.SecretType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Yang dibutuhkan Profile manager dari aplikasi (diisi window utama). */
public interface ProfileManagerHost {

    ProfileStore store();

    /** Executor disk I/O (store, vault metadata). */
    Executor io();

    /** Executor untuk Test koneksi (bisa menunggu sampai timeout; jangan menahan disk I/O). */
    Executor network();

    /** Secret yang tersimpan untuk profil. Blocking (disk I/O): panggil di {@link #io()}. */
    Set<SecretType> storedSecrets(UUID profileId);

    /**
     * Simpan perubahan secret (asinkron, vault dibuka kalau perlu). Array di {@code changes} di-zero setelah dipakai.
     *
     * @param copyLoginToSudo salin password login yang tersimpan di vault menjadi password sudo
     */
    void saveSecrets(HostProfile profile, Map<SecretType, SecretChange> changes, boolean copyLoginToSudo);

    /** Hapus semua secret profil yang sudah dihapus (asinkron), dan data lain milik profil itu. */
    void profilesDeleted(Collection<UUID> ids);

    /** Buka tab terminal (atau SFTP untuk FTP) untuk tiap profil. EDT. */
    void connect(List<HostProfile> profiles);

    /**
     * Tes koneksi (blocking, panggil di luar EDT). Secret dari form diutamakan; tanpa itu dari vault (dibuka kalau
     * perlu). Array {@code password}/{@code passphrase} di-zero setelah dipakai.
     */
    ConnectionTester.Result test(HostProfile profile, char[] password, char[] passphrase);

    /** Snippet yang muncul di terminal untuk profil ini. */
    List<Snippet> snippetsFor(HostProfile profile);

    /** Buka Snippet manager. EDT. */
    void openSnippets();

    /** Isi file export semua snippet, null kalau tidak ada snippet. */
    byte[] exportSnippets();

    int snippetCount();

    /** Profil dari file OpenSSH config. Blocking. */
    List<HostProfile> readSshConfig(Path file) throws IOException;

    Path defaultSshConfig();

    List<HostTerminalColors.Choice> colorChoices();
}
