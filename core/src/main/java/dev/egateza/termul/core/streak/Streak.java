package dev.egateza.termul.core.streak;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Streak harian: berapa hari berturut-turut TermUL dibuka. Satu hari = tanggal kalender lokal; membuka berkali-kali di
 * hari yang sama dihitung satu. Immutable; semua aturan ada di sini supaya mudah diuji.
 *
 * <ul>
 *   <li>Hari ke-1 dan ke-2 masih "bara"; streak aktif mulai hari ke-{@value #ACTIVE_FROM}.</li>
 *   <li>Streak aktif lalu absen 1–2 hari: padam, dinyalakan lagi dengan triple klik.</li>
 *   <li>Absen 3–{@value #MAX_FROZEN_MISSED} hari: beku, dipecahkan dengan {@link #clicksToThaw(int)} klik.</li>
 *   <li>Absen lebih dari {@value #MAX_FROZEN_MISSED} hari: hangus, mulai lagi dari hari ke-1.</li>
 * </ul>
 * Memulihkan (padam/beku) tidak menghapus angka streak, dan hari ini ikut dihitung; hari yang bolong tidak.
 *
 * @param count   jumlah hari yang sudah dihitung; 0 = belum pernah dibuka
 * @param lastDay hari terakhir yang dihitung; null kalau {@code count} 0
 */
public record Streak(int count, LocalDate lastDay) {

    public static final int ACTIVE_FROM = 3;
    public static final int MAX_GRAY_MISSED = 2;
    public static final int MAX_FROZEN_MISSED = 10;
    public static final Streak NONE = new Streak(0, null);

    public Streak {
        if (count < 0) {
            throw new IllegalArgumentException("count negatif: " + count);
        }
        if (count == 0 || lastDay == null) {
            count = 0;
            lastDay = null;
        }
    }

    /** Jumlah klik untuk memecahkan es: 5 untuk absen 3 hari, naik satu per hari, maksimal 10. */
    public static int clicksToThaw(int missed) {
        return Math.clamp(5 + (missed - 3), 5, 10);
    }

    public boolean active() {
        return count >= ACTIVE_FROM;
    }

    /** Keadaan streak di hari {@code today}, dengan anggapan {@link #open} hari itu sudah dijalankan. */
    public StreakStatus status(LocalDate today) {
        if (lastDay == null) {
            return new StreakStatus.None();
        }
        long missed = ChronoUnit.DAYS.between(lastDay, today) - 1;
        if (missed < 0 || !active() && missed == 0) {
            return active() ? new StreakStatus.Burning(count) : new StreakStatus.Kindling(count);
        }
        if (!active()) {
            return new StreakStatus.None(); // bara yang bolong tidak padam/beku, langsung mulai lagi saat dibuka
        }
        if (missed == 0) {
            return new StreakStatus.Burning(count);
        }
        if (missed <= MAX_GRAY_MISSED) {
            return new StreakStatus.Gray(count, (int) missed);
        }
        if (missed <= MAX_FROZEN_MISSED) {
            return new StreakStatus.Frozen(count, (int) missed, clicksToThaw((int) missed));
        }
        return new StreakStatus.Expired(count, missed);
    }

    /**
     * Aplikasi dibuka (atau tetap terbuka saat tanggal berganti) di hari {@code today}. Jam yang mundur ke hari
     * sebelumnya diperlakukan seperti hari yang sama, jadi tidak mengubah apa pun.
     */
    public Opening open(LocalDate today) {
        if (lastDay == null) {
            return new Opening(new Streak(1, today), new StreakEvent.Counted(0, 1));
        }
        long gap = ChronoUnit.DAYS.between(lastDay, today);
        if (gap <= 0) {
            return new Opening(this, new StreakEvent.SameDay());
        }
        if (gap == 1) {
            return new Opening(new Streak(count + 1, today), new StreakEvent.Counted(count, count + 1));
        }
        return switch (status(today)) {
            case StreakStatus.Gray g -> new Opening(this, new StreakEvent.NeedsRevival(g));
            case StreakStatus.Frozen f -> new Opening(this, new StreakEvent.NeedsRevival(f));
            case StreakStatus.Expired e -> new Opening(new Streak(1, today), new StreakEvent.Expired(e.count(), e.missed()));
            default -> new Opening(new Streak(1, today), new StreakEvent.Counted(0, 1)); // bara yang bolong
        };
    }

    /**
     * Streak yang padam atau beku dinyalakan lagi (triple klik / es pecah): angka lanjut dan hari ini dihitung.
     *
     * @throws IllegalStateException kalau streak tidak sedang padam atau beku
     */
    public Streak revive(LocalDate today) {
        var status = status(today);
        if (!(status instanceof StreakStatus.Gray) && !(status instanceof StreakStatus.Frozen)) {
            throw new IllegalStateException("Streak tidak sedang padam atau beku: " + status);
        }
        return new Streak(count + 1, today);
    }

    /** Hasil {@link #open}: streak baru (simpan ke disk kalau berubah) dan apa yang terjadi (untuk animasi). */
    public record Opening(Streak streak, StreakEvent event) {
    }
}
