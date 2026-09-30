package dev.egateza.termul.ssh;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hak pakai satu {@link SshConnection}. Setiap pemakai (tab, panel SFTP, remote edit) memegang
 * lease sendiri; {@link #close()} idempotent dan mengurangi reference count.
 */
public final class SshLease implements AutoCloseable {

    private final SessionManager manager;
    private final SessionManager.Entry entry;
    private final SshConnection connection;
    private final AtomicBoolean released = new AtomicBoolean();

    SshLease(SessionManager manager, SessionManager.Entry entry, SshConnection connection) {
        this.manager = manager;
        this.entry = entry;
        this.connection = connection;
    }

    public SshConnection connection() {
        if (released.get()) {
            throw new IllegalStateException("Lease sudah dilepas");
        }
        return connection;
    }

    public boolean isReleased() {
        return released.get();
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            manager.release(entry);
        }
    }
}
