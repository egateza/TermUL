package dev.egateza.termul.app.update;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.update.ReleaseVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class UpdatePrompterTest {

    private static final ReleaseVersion V130 = ReleaseVersion.parse("0.1.130");
    private static final ReleaseVersion V131 = ReleaseVersion.parse("0.1.131");

    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicBoolean dialogOpen = new AtomicBoolean();
    private final List<ReleaseVersion> opened = new ArrayList<>();
    private UpdatePrompter prompter;

    @Test
    void versiBaruMembukaModalSekaliPerVersi() throws Exception {
        onEdt(() -> {
            prompter().onState(new UpdateNotifier.Available(V130));
            prompter.onState(new UpdateNotifier.Idle());
            prompter.onState(new UpdateNotifier.Available(V130));
            prompter.onState(new UpdateNotifier.Available(V131));
        });

        assertThat(opened).containsExactly(V130, V131);
    }

    @Test
    void ditundaSelamaAdaDialogModalLain() throws Exception {
        busy.set(true);
        onEdt(() -> prompter().onState(new UpdateNotifier.Available(V130)));
        assertThat(opened).isEmpty();
        assertThat(prompter.waiting()).isTrue();

        busy.set(false);
        onEdt(() -> prompter.tryOpen());

        assertThat(opened).containsExactly(V130);
        assertThat(prompter.waiting()).isFalse();
    }

    @Test
    void penundaanBatalKalauUpdateTidakLagiTersedia() throws Exception {
        busy.set(true);
        onEdt(() -> {
            prompter().onState(new UpdateNotifier.Available(V130));
            prompter.onState(new UpdateNotifier.Installed(V130));
        });
        busy.set(false);
        onEdt(() -> prompter.tryOpen());

        assertThat(opened).isEmpty();
    }

    @Test
    void sudahDilihatLewatDialogSendiriTidakDitawarkanLagi() throws Exception {
        dialogOpen.set(true);
        onEdt(() -> prompter().onState(new UpdateNotifier.Available(V130)));
        dialogOpen.set(false);
        onEdt(() -> prompter.onState(new UpdateNotifier.Available(V130)));

        assertThat(opened).isEmpty();
    }

    private UpdatePrompter prompter() {
        prompter = new UpdatePrompter(busy::get, dialogOpen::get, opened::add);
        return prompter;
    }

    /** Jalankan di EDT lalu tunggu {@code invokeLater} pembuka dialog selesai. */
    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
        SwingUtilities.invokeAndWait(() -> { });
    }
}
