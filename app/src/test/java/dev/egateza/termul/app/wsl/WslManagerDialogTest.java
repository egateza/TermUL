package dev.egateza.termul.app.wsl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.anim.AnimationChoice;
import dev.egateza.termul.app.ui.anim.AnimationKind;
import dev.egateza.termul.app.ui.anim.LoadingPanel;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.wsl.WslDistro;
import java.awt.GraphicsEnvironment;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class WslManagerDialogTest {

    private static final WslManagerDialog.Actions NO_ACTIONS = new WslManagerDialog.Actions() {
        @Override
        public void refresh() {
        }

        @Override
        public void start(String distro) {
        }

        @Override
        public void stop(String distro) {
        }

        @Override
        public void shutdownAll() {
        }

        @Override
        public void newProfile(String distro) {
        }

        @Override
        public void guide(String distro) {
        }

        @Override
        public void check(String distro) {
        }

        @Override
        public void setPort(String distro, int port) {
        }
    };

    @AfterEach
    void resetAnimation() throws Exception {
        SwingUtilities.invokeAndWait(() -> LoadingPanel.use(new AnimationChoice.Fixed(AnimationKind.PACMAN)));
    }

    @Test
    void animasiProsesTampilSelamaSibukDanSaatMembacaDaftar() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            var dialog = new WslManagerDialog(null, NO_ACTIONS);
            try {
                assertThat(dialog.progressVisible()).isTrue(); // daftar distro belum terbaca

                dialog.setDistros(List.of(new WslDistro("Ubuntu", false)), new ProfileSnapshot(1, List.of(), List.of()),
                        d -> null);
                assertThat(dialog.progressVisible()).isFalse();

                dialog.setBusy("Menjalankan Ubuntu...");
                assertThat(dialog.progressVisible()).isTrue();

                dialog.setIdle("Ubuntu berjalan");
                assertThat(dialog.progressVisible()).isFalse();
            } finally {
                dialog.dispose();
            }
        });
    }

    @Test
    void tanpaAnimasiKalauPengaturannyaMati() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            LoadingPanel.use(AnimationChoice.OFF);
            var dialog = new WslManagerDialog(null, NO_ACTIONS);
            try {
                dialog.setBusy("Memeriksa sshd di Ubuntu...");

                assertThat(dialog.progressVisible()).isFalse();
            } finally {
                dialog.dispose();
            }
        });
    }
}
