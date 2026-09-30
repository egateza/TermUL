package dev.egateza.myterm.app.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Ring buffer baris log terakhir di memori, untuk panel log di UI. Thread-safe: ditulis oleh thread mana pun
 * (lewat {@link UiLogAppender}), dibaca oleh EDT dengan polling {@link #since(long)} supaya EDT tidak dibanjiri
 * {@code invokeLater} per event.
 */
public final class LogBuffer {

    /** Satu baris (atau beberapa baris, kalau ada stack trace) log yang sudah diformat. */
    public record Entry(long seq, boolean warning, String text) {
    }

    public static final int DEFAULT_CAPACITY = 2000;

    private static final LogBuffer GLOBAL = new LogBuffer(DEFAULT_CAPACITY);

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>(); // guarded by this
    private long nextSeq = 1; // guarded by this

    public LogBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity harus >= 1");
        }
        this.capacity = capacity;
    }

    /** Buffer yang diisi oleh appender di {@code logback.xml}. */
    public static LogBuffer global() {
        return GLOBAL;
    }

    public synchronized void append(boolean warning, String text) {
        if (entries.size() == capacity) {
            entries.removeFirst();
        }
        entries.addLast(new Entry(nextSeq++, warning, text));
    }

    /** @return entry dengan {@code seq > afterSeq}, urut dari yang paling lama */
    public synchronized List<Entry> since(long afterSeq) {
        var result = new ArrayList<Entry>();
        for (Entry e : entries) {
            if (e.seq() > afterSeq) {
                result.add(e);
            }
        }
        return result;
    }

    public int capacity() {
        return capacity;
    }
}
