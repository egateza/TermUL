package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TerminalMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StoredCursor;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JediTerminal} dengan kursor tersimpan (DECSC) terpisah untuk layar utama dan layar alternate, seperti xterm.
 * JediTerm 3.x hanya punya satu slot: program layar penuh (nano/ncurses memakai {@code ESC 7} saat mengatur scroll
 * region) menimpa posisi yang disimpan {@code ESC[?1049h}, sehingga setelah keluar ({@code ESC[?1049l}) kursor kembali
 * ke posisi di dalam nano (baris atas), bukan ke prompt.
 *
 * <p>Slot milik JediTerm ({@code myStoredCursor}, private) ditukar saat masuk/keluar layar alternate. Kalau field itu
 * tidak ada lagi (JediTerm di-upgrade), perilaku kembali ke bawaan JediTerm.
 *
 * <p>Semua method dipanggil dari thread emulator, jadi state di sini tidak butuh sinkronisasi.
 */
final class XtermCursorTerminal extends JediTerminal {

    private static final Logger log = LoggerFactory.getLogger(XtermCursorTerminal.class);
    private static final VarHandle STORED_CURSOR = storedCursorHandle();

    // dipakai juga oleh reset() di constructor JediTerminal, jadi tanpa initializer
    private StoredCursor mainSaved;     // slot layar utama selama layar alternate aktif
    private boolean restoreMainOnExit;  // StoreCursor-off (bagian pertama ESC[?1049l) baru diproses di layar alternate

    XtermCursorTerminal(TerminalDisplay display, TerminalTextBuffer buffer, StyleState styleState) {
        super(display, buffer, styleState);
    }

    static boolean supported() {
        return STORED_CURSOR != null;
    }

    /**
     * JediTerm memproses {@code ESC[?1049l} sebagai StoreCursor-off (restore kursor, masih di layar alternate) lalu
     * AlternateBuffer-off. xterm justru kembali ke layar utama dulu, baru me-restore kursor milik layar utama.
     */
    @Override
    public void setModeEnabled(TerminalMode mode, boolean enabled) {
        boolean exit1049 = restoreMainOnExit && mode == TerminalMode.AlternateBuffer && !enabled;
        restoreMainOnExit = false;
        super.setModeEnabled(mode, enabled);
        if (STORED_CURSOR == null) {
            return;
        }
        boolean alternate = getTerminalTextBuffer().isUsingAlternateBuffer();
        if (exit1049 && !alternate && STORED_CURSOR.get(this) != null) {
            restoreCursor();
        }
        if (mode == TerminalMode.StoreCursor && !enabled && alternate) {
            restoreMainOnExit = true;
        }
    }

    @Override
    public void useAlternateBuffer(boolean enabled) {
        boolean was = getTerminalTextBuffer().isUsingAlternateBuffer();
        if (STORED_CURSOR != null && enabled && !was) {
            mainSaved = (StoredCursor) STORED_CURSOR.get(this);
            STORED_CURSOR.set(this, (StoredCursor) null); // layar alternate mulai tanpa kursor tersimpan
        }
        super.useAlternateBuffer(enabled);
        if (STORED_CURSOR != null && !enabled && was) {
            STORED_CURSOR.set(this, mainSaved);
            mainSaved = null;
        }
    }

    private static VarHandle storedCursorHandle() {
        try {
            return MethodHandles.privateLookupIn(JediTerminal.class, MethodHandles.lookup())
                    .findVarHandle(JediTerminal.class, "myStoredCursor", StoredCursor.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("Kursor tersimpan per layar tidak aktif; JediTerm tidak cocok: {}", e.toString());
            return null;
        }
    }
}
