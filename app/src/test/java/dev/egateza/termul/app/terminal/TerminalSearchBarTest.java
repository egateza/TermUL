package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.ui.JediTermSearchComponentListener;
import dev.egateza.termul.app.i18n.I18n;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerminalSearchBarTest {

    private final List<String> events = new ArrayList<>();
    private TerminalSearchBar bar;

    @BeforeEach
    void setUp() {
        I18n.use("id");
        bar = new TerminalSearchBar();
        bar.addListener(new JediTermSearchComponentListener() {
            @Override
            public void searchSettingsChanged(String textToFind, boolean ignoreCase) {
                events.add("cari:" + textToFind + ":" + (ignoreCase ? "i" : "c"));
            }

            @Override
            public void hideSearchComponent() {
                events.add("tutup");
            }

            @Override
            public void selectNextFindResult() {
                events.add("next");
            }

            @Override
            public void selectPrevFindResult() {
                events.add("prev");
            }
        });
        // seperti JediTermWidget: Enter = berikutnya, Esc = tutup
        bar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                events.add(e.getKeyCode() == KeyEvent.VK_ESCAPE ? "esc" : "enter");
            }
        });
    }

    @AfterEach
    void reset() {
        I18n.use(I18n.FALLBACK);
    }

    private static SubstringFinder.FindResult find(String text, String pattern) {
        var finder = new SubstringFinder(pattern, true);
        var chars = new CharBuffer(text);
        for (int i = 0; i < chars.length(); i++) {
            finder.nextChar(i, 0, chars, i);
        }
        return finder.getResult();
    }

    private void press(int key, int modifiers) {
        var field = bar.field();
        for (var l : field.getKeyListeners()) {
            l.keyPressed(new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED));
        }
    }

    @Test
    void ketikanDanAaMengubahPencarian() {
        bar.field().setText("err");
        for (var c : bar.getComponents()[0] instanceof javax.swing.JPanel p ? p.getComponents() : new java.awt.Component[0]) {
            if (c instanceof JToggleButton t) {
                t.doClick();
            }
        }

        assertThat(events).containsExactly("cari:err:i", "cari:err:c");
    }

    @Test
    void jumlahHasil() {
        bar.field().setText("ab");

        bar.onResultUpdated(find("ab xx ab", "ab"));
        assertThat(bar.countText()).isEqualTo("1 dari 2");

        bar.onResultUpdated(find("xx", "ab"));
        assertThat(bar.countText()).isEqualTo("Tidak ada hasil");

        bar.field().setText("");
        assertThat(bar.countText()).isBlank();
    }

    @Test
    void shiftEnterKeSebelumnyaEnterDanEscKeJediTerm() {
        press(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
        press(KeyEvent.VK_ENTER, 0);
        press(KeyEvent.VK_ESCAPE, 0);

        assertThat(events).containsExactly("prev", "enter", "esc");
    }

    @Test
    void tombolSebelumnyaBerikutnyaTutup() {
        var buttons = new ArrayList<JButton>();
        for (var c : ((javax.swing.JPanel) bar.getComponents()[0]).getComponents()) {
            if (c instanceof JButton b) {
                buttons.add(b);
            }
        }
        assertThat(buttons).hasSize(3);
        buttons.forEach(JButton::doClick);

        assertThat(events).containsExactly("prev", "next", "tutup");
    }
}
