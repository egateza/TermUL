package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * JediTerm mencari hyperlink di bawah mouse lewat {@code getLine} tanpa lock; di terminal baru itu menambah baris ke
 * storage layar bersamaan dengan thread emulator yang menulis output pertama, sehingga storage rusak (NPE di setiap
 * paint). Gerakan mouse dan perubahan fokus harus diproses selagi lock buffer dipegang.
 */
class HoverLockTest {

    @Test
    void gerakanMouseDanFokusMemegangLockBuffer() throws Exception {
        List<String> free = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            var widget = new ZoomableTermWidget(new TerminalSettings(14f));
            var panel = widget.getTerminalPanel();
            var buffer = widget.getTerminalTextBuffer();
            panel.setSize(400, 200);
            Runnable check = () -> {
                var other = new Thread(() -> { // seperti thread emulator yang mau menulis
                    if (buffer.tryLock()) {
                        synchronized (free) {
                            free.add(Thread.currentThread().getName());
                        }
                        buffer.unlock();
                    }
                });
                other.start();
                try {
                    other.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            panel.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    check.run();
                }
            });
            panel.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    check.run();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    check.run();
                }
            });

            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 50, 40,
                    0, false));
            panel.dispatchEvent(new FocusEvent(panel, FocusEvent.FOCUS_LOST));

            assertThat(buffer.getScreenLinesStorage().getSize()).isLessThanOrEqualTo(buffer.getHeight());
        });

        assertThat(free).as("thread lain bisa mengambil lock selama event diproses").isEmpty();
    }
}
