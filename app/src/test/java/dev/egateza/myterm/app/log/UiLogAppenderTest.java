package dev.egateza.myterm.app.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UiLogAppenderTest {

    private final LoggerContext context = new LoggerContext();
    private final LogBuffer buffer = new LogBuffer(10);
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        var appender = new UiLogAppender(buffer);
        appender.setContext(context);
        appender.setPattern("%-5level %logger{0} - %msg%n");
        appender.start();
        logger = context.getLogger("dev.egateza.Sample");
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void formatsEventAndMarksWarnings() {
        logger.info("halo {}", "dunia");
        logger.warn("hati-hati");
        logger.error("gagal");

        var entries = buffer.since(0);
        assertThat(entries).extracting(LogBuffer.Entry::text)
                .containsExactly("INFO  Sample - halo dunia", "WARN  Sample - hati-hati", "ERROR Sample - gagal");
        assertThat(entries).extracting(LogBuffer.Entry::warning).containsExactly(false, true, true);
    }

    @Test
    void includesStackTrace() {
        logger.error("koneksi gagal", new IllegalStateException("boom"));

        assertThat(buffer.since(0).getFirst().text())
                .startsWith("ERROR Sample - koneksi gagal")
                .contains("java.lang.IllegalStateException: boom");
    }
}
