package dev.egateza.termul.app.ui.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.app.ui.HostTerminalColors;
import dev.egateza.termul.app.ui.SecretChange;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileStore;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.ssh.ConnectionTester;
import dev.egateza.termul.vault.SecretType;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileManagerDialogTest {

    @TempDir
    Path dir;

    private ProfileManagerHost host(ProfileStore store) {
        Executor direct = Runnable::run;
        return new ProfileManagerHost() {
            @Override
            public ProfileStore store() {
                return store;
            }

            @Override
            public Executor io() {
                return direct;
            }

            @Override
            public Executor network() {
                return direct;
            }

            @Override
            public Set<SecretType> storedSecrets(UUID profileId) {
                return Set.of();
            }

            @Override
            public void saveSecrets(HostProfile profile, Map<SecretType, SecretChange> changes, boolean copy) {
            }

            @Override
            public void profilesDeleted(Collection<UUID> ids) {
            }

            @Override
            public void connect(List<HostProfile> profiles) {
            }

            @Override
            public ConnectionTester.Result test(HostProfile profile, char[] password, char[] passphrase) {
                return new ConnectionTester.Result(ConnectionTester.HostKey.KNOWN, true, false, 1, null);
            }

            @Override
            public List<Snippet> snippetsFor(HostProfile profile) {
                return List.of();
            }

            @Override
            public void openSnippets() {
            }

            @Override
            public byte[] exportSnippets() {
                return null;
            }

            @Override
            public int snippetCount() {
                return 0;
            }

            @Override
            public List<HostProfile> readSshConfig(Path file) {
                return List.of();
            }

            @Override
            public Path defaultSshConfig() {
                return dir.resolve("config");
            }

            @Override
            public List<HostTerminalColors.Choice> colorChoices() {
                return List.of(new HostTerminalColors.Choice(null, "Default"));
            }
        };
    }

    private static HostProfile p(String name, String group) {
        return new HostProfile(UUID.randomUUID(), name, group, "10.0.0." + name.length(), 22, "admin",
                AuthMethod.PASSWORD, null, null, EnvironmentTag.NONE, null, null, false);
    }

    @Test
    void terbukaDiSetiapModeDanFavoritDiAtasSemuaProfil() throws Exception {
        var store = new ProfileStore(dir.resolve("profiles.json"));
        var a = p("alpha", "Dev");
        var b = p("beta", "Prod");
        var c = p("gamma", "");
        store.saveAll(List.of(a, b, c));
        store.setFavorite(c.id(), true);
        var host = host(store);

        SwingUtilities.invokeAndWait(() -> {
            var view = new ProfileManagerDialog(null, host, null, null);
            assertThat(view.shown()).extracting(HostProfile::name).containsExactly("gamma", "alpha", "beta");
            view.dispose();
            new ProfileManagerDialog(null, host, b.id(), null).dispose();
            var created = new ProfileManagerDialog(null, host, null, "Prod");
            assertThat(created.folderChoices()).extracting(ProfileManagerDialog.FolderChoice::group)
                    .containsExactly("Dev", "Prod", "");
            created.dispose();
        });
    }

    /** Popup hapus: paragraf yang membungkus dan catatan ikut dihitung, tombol tidak terpotong di bawah. */
    @Test
    void tombolPopupHapusTidakTerpotong() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var notes : List.of(List.<String>of(), List.of("jump-01 terhubung lewat profil ini dan akan beralih ke "
                    + "koneksi langsung.", "3 snippet khusus host ini tidak akan muncul lagi di terminal."))) {
                var dialog = PmDialogs.dangerDialog(null, "Delete “gapura08”?", "This removes the profile and its "
                        + "Vault entries from this computer. Nothing changes on the server.", notes, "Delete", "Cancel",
                        new boolean[1]);
                var content = (javax.swing.JComponent) dialog.getContentPane();
                content.setSize(dialog.getSize());
                PmDialogs.layoutTree(content); // seperti saat dialog tampil dengan ukuran itu
                var cancel = find(content, "Cancel");
                var bounds = SwingUtilities.convertRectangle(cancel.getParent(), cancel.getBounds(), content);

                assertThat(bounds.getMaxY()).as("tombol di dalam dialog, notes=" + notes.size())
                        .isLessThanOrEqualTo(content.getHeight() - content.getInsets().bottom);
                assertThat(cancel.getHeight()).isEqualTo(cancel.getPreferredSize().height);
                dialog.dispose();
            }
        });
    }

    private static java.awt.Component find(java.awt.Container root, String text) {
        for (var c : root.getComponents()) {
            if (c instanceof javax.swing.AbstractButton b && text.equals(b.getText())) {
                return b;
            }
            if (c instanceof java.awt.Container child) {
                var found = find(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Edit, Duplicate, Delete: ikon di tengah tombol 30×30 (sebelumnya terdorong padding kiri 14 px). */
    @Test
    void ikonTombolAksiDiTengah() {
        for (String path : List.of(ProfileManagerDialog.EDIT, ProfileManagerDialog.COPY, SnippetUi.TRASH)) {
            var b = ProfileManagerDialog.iconButton(path, "x", SnippetUi.Variant.DEFAULT);
            b.setSize(b.getPreferredSize());
            var img = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = img.createGraphics();
            b.paint(g);
            g.dispose();
            int bg = img.getRGB(15, 4); // isi tombol, di luar ikon
            int left = Integer.MAX_VALUE;
            int right = -1;
            for (int y = 4; y < 26; y++) {
                for (int x = 4; x < 26; x++) {
                    if (img.getRGB(x, y) != bg) {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
                }
            }
            assertThat(Math.abs((left + right) / 2.0 - 14.5)).as(path).isLessThanOrEqualTo(1.0);
        }
    }
}
