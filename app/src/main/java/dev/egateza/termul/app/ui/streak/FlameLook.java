package dev.egateza.termul.app.ui.streak;

import dev.egateza.termul.core.streak.Streak;
import dev.egateza.termul.core.streak.StreakStatus;

/**
 * Apa yang digambar untuk satu keadaan streak.
 *
 * @param count  jumlah hari (di bawah {@value Streak#ACTIVE_FROM} = bara)
 * @param missed hari absen (padam/beku)
 * @param cracks retakan es yang sudah dibuat (beku)
 */
record FlameLook(Mode mode, int count, int missed, int cracks) {

    enum Mode { ACTIVE, GRAY, FROZEN }

    static FlameLook active(int count) {
        return new FlameLook(Mode.ACTIVE, count, 0, 0);
    }

    static FlameLook of(StreakStatus status, int cracks) {
        return switch (status) {
            case StreakStatus.None _ -> active(0);
            case StreakStatus.Kindling k -> active(k.day());
            case StreakStatus.Burning b -> active(b.count());
            case StreakStatus.Gray g -> new FlameLook(Mode.GRAY, g.count(), g.missed(), 0);
            case StreakStatus.Frozen f -> new FlameLook(Mode.FROZEN, f.count(), f.missed(), cracks);
            case StreakStatus.Expired e -> new FlameLook(Mode.GRAY, e.count(), (int) Math.min(e.missed(), 999), 0);
        };
    }

    StreakTier tier() {
        return StreakTier.of(count);
    }

    boolean kindling() {
        return mode == Mode.ACTIVE && count < Streak.ACTIVE_FROM;
    }

    int clicks() {
        return Streak.clicksToThaw(missed);
    }
}
