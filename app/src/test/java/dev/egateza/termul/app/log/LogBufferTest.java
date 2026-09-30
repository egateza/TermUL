package dev.egateza.termul.app.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LogBufferTest {

    @Test
    void sinceReturnsOnlyNewerEntriesInOrder() {
        var buffer = new LogBuffer(10);
        buffer.append(false, "a");
        buffer.append(true, "b");
        buffer.append(false, "c");

        assertThat(buffer.since(0)).extracting(LogBuffer.Entry::text).containsExactly("a", "b", "c");
        assertThat(buffer.since(2)).extracting(LogBuffer.Entry::text).containsExactly("c");
        assertThat(buffer.since(3)).isEmpty();
        assertThat(buffer.since(0).get(1).warning()).isTrue();
    }

    @Test
    void dropsOldestWhenFullButKeepsSequence() {
        var buffer = new LogBuffer(2);
        buffer.append(false, "a");
        buffer.append(false, "b");
        buffer.append(false, "c");

        var all = buffer.since(0);
        assertThat(all).extracting(LogBuffer.Entry::text).containsExactly("b", "c");
        assertThat(all).extracting(LogBuffer.Entry::seq).containsExactly(2L, 3L);
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThatThrownBy(() -> new LogBuffer(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
