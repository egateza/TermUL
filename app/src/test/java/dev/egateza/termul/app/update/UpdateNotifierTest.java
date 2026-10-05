package dev.egateza.termul.app.update;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.update.ReleaseVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class UpdateNotifierTest {

    private static final ReleaseVersion V130 = ReleaseVersion.parse("0.1.130");

    private final List<UpdateNotifier.State> changes = new ArrayList<>();
    private final AtomicReference<Optional<ReleaseVersion>> latest = new AtomicReference<>(Optional.of(V130));
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger calls = new AtomicInteger();
    private final UpdateNotifier notifier = new UpdateNotifier(() -> {
        calls.incrementAndGet();
        return latest.get();
    }, enabled::get, changes::add);

    @Test
    void rilisBaruMenampilkanBadge() throws Exception {
        check();

        assertThat(changes).containsExactly(new UpdateNotifier.Available(V130));
    }

    @Test
    void hasilSamaTidakMemicuUlang() throws Exception {
        check();
        check();

        assertThat(changes).hasSize(1);
    }

    @Test
    void sudahTerbaruMenyembunyikanBadge() throws Exception {
        check();
        latest.set(Optional.empty());
        check();

        assertThat(changes).containsExactly(new UpdateNotifier.Available(V130), new UpdateNotifier.Idle());
    }

    @Test
    void dimatikanTidakMenghubungiServer() throws Exception {
        enabled.set(false);
        check();

        assertThat(calls).hasValue(0);
        assertThat(changes).isEmpty();
    }

    @Test
    void setelahDipasangBadgeRestartBertahan() throws Exception {
        check();
        SwingUtilities.invokeAndWait(() -> notifier.installed(V130));
        latest.set(Optional.of(ReleaseVersion.parse("0.1.131")));
        check();
        SwingUtilities.invokeAndWait(notifier::disabled);

        assertThat(changes).containsExactly(new UpdateNotifier.Available(V130), new UpdateNotifier.Installed(V130));
    }

    @Test
    void gagalTidakMengubahBadge() throws Exception {
        check();
        var failing = new UpdateNotifier(() -> {
            throw new java.io.IOException("offline");
        }, () -> true, changes::add);
        failing.checkQuietly();
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(changes).containsExactly(new UpdateNotifier.Available(V130));
    }

    @Test
    void dimatikanUserMenyembunyikanBadgeTersedia() throws Exception {
        check();
        SwingUtilities.invokeAndWait(notifier::disabled);

        assertThat(changes).containsExactly(new UpdateNotifier.Available(V130), new UpdateNotifier.Idle());
    }

    /** Jalankan satu pemeriksaan lalu tunggu hasilnya diproses di EDT. */
    private void check() throws Exception {
        notifier.checkQuietly();
        SwingUtilities.invokeAndWait(() -> { });
    }
}
