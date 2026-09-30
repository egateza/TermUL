package dev.egateza.termul.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReconnectorTest {

    /** Merekam semua callback sebagai teks supaya urutannya bisa diperiksa. */
    private static final class Recorder implements Reconnector.Listener<String> {
        final List<String> events = new ArrayList<>();

        @Override
        public void attempting(int attempt) {
            events.add("coba" + attempt);
        }

        @Override
        public void waiting(int secondsLeft, Exception lastError) {
            events.add("tunggu" + secondsLeft);
        }

        @Override
        public void connected(String value) {
            events.add("tersambung:" + value);
        }

        @Override
        public void gaveUp(Exception lastError) {
            events.add("menyerah:" + lastError.getMessage());
        }
    }

    private final AtomicInteger slept = new AtomicInteger();

    private Reconnector<String> reconnector() {
        return new Reconnector<>(Reconnector.DEFAULT_DELAYS, d -> slept.addAndGet((int) d.toSeconds()));
    }

    @Test
    void firstAttemptIsImmediate() {
        var rec = new Recorder();

        reconnector().run(() -> "ok", rec);

        assertThat(rec.events).containsExactly("coba1", "tersambung:ok");
        assertThat(slept).hasValue(0);
    }

    @Test
    void waits15ThenTriesAgainThenWaits30ThenSucceeds() {
        var rec = new Recorder();
        var calls = new AtomicInteger();

        reconnector().run(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IOException("timeout");
            }
            return "ok";
        }, rec);

        assertThat(rec.events.getFirst()).isEqualTo("coba1");
        assertThat(rec.events).containsSubsequence("coba1", "tunggu15", "tunggu1", "coba2", "tunggu30", "tunggu1",
                "coba3", "tersambung:ok");
        assertThat(slept).hasValue(15 + 30);
    }

    @Test
    void givesUpAfterThirdFailure() {
        var rec = new Recorder();
        var calls = new AtomicInteger();

        reconnector().run(() -> {
            calls.incrementAndGet();
            throw new IOException("server mati");
        }, rec);

        assertThat(calls).hasValue(3);
        assertThat(rec.events.getLast()).isEqualTo("menyerah:server mati");
        assertThat(slept).hasValue(45);
    }

    @Test
    void skipWaitTriesImmediately() {
        var rec = new Recorder();
        var calls = new AtomicInteger();
        var r = new Reconnector<String>(Reconnector.DEFAULT_DELAYS, d -> { });

        r.run(() -> {
            if (calls.incrementAndGet() == 1) {
                r.skipWait(); // user menekan Reconnect saat hitung mundur berikutnya
                throw new IOException("x");
            }
            return "ok";
        }, rec);

        assertThat(rec.events).containsExactly("coba1", "coba2", "tersambung:ok");
    }

    @Test
    void cancelStopsWithoutFurtherCallbacksAndClosesLateResult() {
        var rec = new Recorder();
        var r = new Reconnector<String>(Reconnector.DEFAULT_DELAYS, d -> { });
        var discarded = new java.util.ArrayList<String>();

        r.run(() -> {
            r.cancel(); // tab ditutup selagi menyambung
            return "shell-baru";
        }, new Reconnector.Listener<>() {
            @Override
            public void attempting(int attempt) {
                rec.events.add("coba" + attempt);
            }

            @Override
            public void waiting(int secondsLeft, Exception lastError) {
                rec.events.add("tunggu");
            }

            @Override
            public void connected(String value) {
                rec.events.add("tersambung");
            }

            @Override
            public void discarded(String value) {
                discarded.add(value);
            }

            @Override
            public void gaveUp(Exception lastError) {
                rec.events.add("menyerah");
            }
        });

        assertThat(rec.events).containsExactly("coba1");
        assertThat(discarded).containsExactly("shell-baru");
    }

    @Test
    void cancelDuringCountdownStopsQuietly() {
        var rec = new Recorder();
        var r = new Reconnector<String>(List.of(Duration.ofSeconds(15)), d -> { });

        r.run(() -> {
            r.cancel();
            throw new IOException("x");
        }, rec);

        assertThat(rec.events).containsExactly("coba1");
    }
}
