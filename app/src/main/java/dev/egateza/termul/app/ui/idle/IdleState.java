package dev.egateza.termul.app.ui.idle;

/**
 * Logika layar idle tanpa Swing: kapan layar idle tampil, dan event keyboard mana yang ditelan supaya tombol yang
 * membangunkan layar tidak ikut terkirim ke terminal (Enter atau Ctrl+C di server produksi). Waktu dalam milidetik
 * monotonic (mis. {@code System.nanoTime() / 1_000_000}). EDT.
 */
public final class IdleState {

    /** Jenis event keyboard (mengikuti {@code KeyEvent.KEY_PRESSED/TYPED/RELEASED}). */
    public enum Key {
        PRESSED, TYPED, RELEASED
    }

    /** Keputusan untuk satu event keyboard. */
    public enum Decision {
        /** Teruskan seperti biasa. */
        PASS,
        /** Telan (jangan sampai ke terminal/menu). */
        SWALLOW,
        /** Telan, dan tutup layar idle sekarang. */
        WAKE
    }

    private static final int NO_KEY = Integer.MIN_VALUE;

    private long lastActivity;
    private boolean active;
    private int wakeKey = NO_KEY; // tombol yang membangunkan layar; event berikutnya ditelan sampai tombol ini dilepas

    public IdleState(long now) {
        lastActivity = now;
    }

    /** Ada input dari user (keyboard/mouse) di aplikasi. */
    public void activity(long now) {
        lastActivity = now;
    }

    /** @return true kalau layar idle harus tampil sekarang ({@code minutes} 0 = fitur mati) */
    public boolean shouldActivate(long now, int minutes) {
        return !active && minutes > 0 && now - lastActivity >= minutes * 60_000L;
    }

    public boolean isActive() {
        return active;
    }

    /** Layar idle tampil (otomatis atau lewat shortcut). */
    public void activate() {
        active = true;
        wakeKey = NO_KEY;
    }

    /** Layar idle ditutup lewat mouse. */
    public void deactivate(long now) {
        active = false;
        lastActivity = now;
    }

    /**
     * Window kehilangan fokus: event lepas tombol pembangun tidak akan datang lagi, jadi berhenti menelan. Layar idle
     * sendiri tetap tampil.
     */
    public void focusLost() {
        wakeKey = NO_KEY;
    }

    /** Putuskan nasib satu event keyboard di window utama. */
    public Decision onKey(Key kind, int keyCode, long now) {
        lastActivity = now;
        if (active) {
            if (kind == Key.PRESSED) {
                active = false;
                wakeKey = keyCode;
                return Decision.WAKE;
            }
            return Decision.SWALLOW; // mis. tombol shortcut yang dilepas setelah layar idle tampil
        }
        if (wakeKey == NO_KEY) {
            return Decision.PASS;
        }
        // termasuk karakter (TYPED), auto-repeat (PRESSED berulang), dan tombol lain selama tombol pembangun ditahan
        if (kind == Key.RELEASED && keyCode == wakeKey) {
            wakeKey = NO_KEY;
        }
        return Decision.SWALLOW;
    }
}
