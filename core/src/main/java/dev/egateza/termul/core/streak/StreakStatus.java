package dev.egateza.termul.core.streak;

/** Keadaan streak di satu hari (lihat {@link Streak#status}). */
public sealed interface StreakStatus {

    /** Belum pernah dibuka, atau bara yang bolong sehari (mulai lagi saat dibuka). */
    record None() implements StreakStatus {
    }

    /** Hari ke-1 atau ke-2: belum aktif, tampil sebagai bara {@code day}/3. */
    record Kindling(int day) implements StreakStatus {
    }

    /** Streak aktif dan sudah dihitung hari ini. */
    record Burning(int count) implements StreakStatus {
    }

    /** Absen 1–2 hari: api padam (abu-abu), dinyalakan lagi dengan triple klik. */
    record Gray(int count, int missed) implements StreakStatus {
    }

    /** Absen 3–10 hari: api beku, butuh {@code clicks} klik untuk memecahkan es. */
    record Frozen(int count, int missed, int clicks) implements StreakStatus {
    }

    /** Absen lebih dari 10 hari: streak {@code count} hari hangus. */
    record Expired(int count, long missed) implements StreakStatus {
    }
}
