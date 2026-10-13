package dev.egateza.termul.core.streak;

/** Apa yang terjadi saat aplikasi dibuka (lihat {@link Streak#open}); menentukan animasi yang diputar. */
public sealed interface StreakEvent {

    /** Sudah dihitung hari ini: tidak ada animasi. */
    record SameDay() implements StreakEvent {
    }

    /** Hari ini dihitung: streak naik dari {@code from} ke {@code to} ({@code from} 0 = mulai baru). */
    record Counted(int from, int to) implements StreakEvent {
    }

    /** Streak padam atau beku dan menunggu dinyalakan lagi; angka belum berubah. */
    record NeedsRevival(StreakStatus status) implements StreakEvent {
    }

    /** Streak {@code lost} hari hangus setelah absen {@code missed} hari; hari ini jadi hari ke-1. */
    record Expired(int lost, long missed) implements StreakEvent {
    }
}
