package dev.egateza.termul.app.terminal;

/**
 * Pilihan reaksi terhadap BEL dari server (suara sistem dan/atau layar bergetar). Satu instance dipakai bersama
 * semua tab, jadi perubahan langsung berlaku di tab yang sedang terbuka. Dibaca dari thread emulator, diubah di EDT.
 */
public final class BellSettings {

    private volatile boolean sound = true;
    private volatile boolean shake = true;

    public boolean sound() {
        return sound;
    }

    public boolean shake() {
        return shake;
    }

    public void setSound(boolean on) {
        this.sound = on;
    }

    public void setShake(boolean on) {
        this.shake = on;
    }
}
