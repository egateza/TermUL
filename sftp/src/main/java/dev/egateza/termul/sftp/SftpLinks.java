package dev.egateza.termul.sftp;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.SessionManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Satu {@link SftpConnection} bersama per profil, dipakai panel SFTP dan sesi edit remote sekaligus, plus daftar
 * sesi terminal per profil. Terminal melaporkan statusnya lewat {@link #registerTerminal}; SFTP mengikutinya
 * (menunggu saat terminal menyambung ulang, tersedia lagi begitu terminal tersambung).
 *
 * <p>Koneksi SFTP ditutup saat pemakai terakhir melepasnya (reference counting).
 */
public final class SftpLinks {

    private static final class Slot {
        final SftpConnection link;
        int refs; // guarded by SftpLinks.this

        Slot(SftpConnection link) {
            this.link = link;
        }
    }

    /** Sesi terminal (satu tab). Pemiliknya wajib memanggil {@link #close()} saat tab ditutup. */
    public final class TerminalHandle implements AutoCloseable {
        private final UUID profileId;
        private TerminalState state = TerminalState.CONNECTING; // guarded by gateLock

        private TerminalHandle(UUID profileId) {
            this.profileId = profileId;
        }

        public void set(TerminalState newState) {
            synchronized (gateLock) {
                if (state == newState) {
                    return;
                }
                state = newState;
                gateLock.notifyAll();
            }
            notifyLink(profileId);
        }

        @Override
        public void close() {
            synchronized (gateLock) {
                var list = terminals.get(profileId);
                if (list != null) {
                    list.remove(this);
                    if (list.isEmpty()) {
                        terminals.remove(profileId);
                    }
                }
                gateLock.notifyAll();
            }
            notifyLink(profileId);
        }
    }

    private final SessionManager sessions;
    private final Map<UUID, Slot> slots = new HashMap<>(); // guarded by this
    private final Object gateLock = new Object();
    private final Map<UUID, List<TerminalHandle>> terminals = new HashMap<>(); // guarded by gateLock

    public SftpLinks(SessionManager sessions) {
        this.sessions = sessions;
    }

    /** Daftarkan sebuah tab terminal untuk profil ini (status awal {@link TerminalState#CONNECTING}). */
    public TerminalHandle registerTerminal(HostProfile profile) {
        var handle = new TerminalHandle(profile.id());
        synchronized (gateLock) {
            terminals.computeIfAbsent(profile.id(), id -> new ArrayList<>()).add(handle);
            gateLock.notifyAll();
        }
        return handle;
    }

    /** Status gabungan semua tab terminal profil ini; null kalau tidak ada. Yang tersambung didahulukan. */
    private TerminalState terminalState(UUID profileId) {
        synchronized (gateLock) {
            var list = terminals.get(profileId);
            if (list == null || list.isEmpty()) {
                return null;
            }
            TerminalState best = TerminalState.DOWN;
            for (var t : list) {
                if (t.state == TerminalState.CONNECTED) {
                    return TerminalState.CONNECTED;
                }
                if (t.state.isPending() && !best.isPending()) {
                    best = t.state;
                }
            }
            return best;
        }
    }

    private void awaitTerminalChange(UUID profileId, TerminalState seen, Duration max) throws InterruptedException {
        synchronized (gateLock) {
            if (terminalState(profileId) == seen) {
                gateLock.wait(Math.max(1, max.toMillis()));
            }
        }
    }

    private void notifyLink(UUID profileId) {
        SftpConnection link;
        synchronized (this) {
            var slot = slots.get(profileId);
            link = slot == null ? null : slot.link;
        }
        if (link != null) {
            link.sessionChanged();
        }
    }

    /**
     * Bagian dari percobaan reconnect terminal: buka lagi kanal SFTP profil ini kalau sedang dipakai (panel SFTP atau
     * sesi edit) dan mati. Dengan begitu terminal dan SFTP tersambung bersama; kalau ini gagal, pemanggil menganggap
     * seluruh percobaan gagal.
     */
    public void restoreSftp(HostProfile profile) throws RemoteFileException {
        SftpConnection link;
        synchronized (this) {
            var slot = slots.get(profile.id());
            link = slot == null ? null : slot.link;
        }
        if (link != null) {
            link.restore();
        }
    }

    /** Ambil (atau buat) koneksi bersama untuk profil ini. Pasangkan dengan {@link #release}. */
    public synchronized SftpConnection acquire(HostProfile profile) {
        var slot = slots.computeIfAbsent(profile.id(), id -> new Slot(new SftpConnection(
                () -> RemoteFileService.open(sessions.acquire(profile)), new SftpConnection.SessionGate() {
                    @Override
                    public TerminalState state() {
                        return terminalState(id);
                    }

                    @Override
                    public void awaitChange(TerminalState seen, Duration max) throws InterruptedException {
                        awaitTerminalChange(id, seen, max);
                    }
                })));
        slot.refs++;
        return slot.link;
    }

    public void release(HostProfile profile) {
        SftpConnection toClose = null;
        synchronized (this) {
            var slot = slots.get(profile.id());
            if (slot != null && --slot.refs <= 0) {
                slots.remove(profile.id());
                toClose = slot.link;
            }
        }
        if (toClose != null) {
            toClose.close(); // di luar lock: menutup koneksi bisa blocking
        }
    }
}
