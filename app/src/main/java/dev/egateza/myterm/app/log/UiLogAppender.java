package dev.egateza.myterm.app.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Appender Logback yang menyalin log ke {@link LogBuffer} untuk ditampilkan di panel log. Isinya sama dengan
 * file log (tanpa secret, lihat {@code docs/SECURITY.md}); tidak menyimpan apa pun ke disk.
 */
public final class UiLogAppender extends AppenderBase<ILoggingEvent> {

    private final LogBuffer buffer;
    private String pattern = "%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n";
    private PatternLayout layout;

    public UiLogAppender() {
        this(LogBuffer.global());
    }

    UiLogAppender(LogBuffer buffer) {
        this.buffer = buffer;
    }

    /** Dipanggil Logback dari elemen {@code <pattern>} di {@code logback.xml}. */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public void start() {
        var l = new PatternLayout();
        l.setContext(getContext());
        l.setPattern(pattern);
        l.start();
        this.layout = l;
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (layout != null) {
            layout.stop();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        String text = layout.doLayout(event);
        buffer.append(event.getLevel().isGreaterOrEqual(Level.WARN), text.stripTrailing());
    }
}
