package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Map;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class KeyBindingsTest {

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int CMD = InputEvent.META_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    private static KeyStroke ks(int key, int mods) {
        return KeyStroke.getKeyStroke(key, mods);
    }

    @Test
    void notasiModMengikutiOs() {
        assertThat(KeyBindings.parse("mod+shift+T", false)).isEqualTo(ks(KeyEvent.VK_T, CTRL | SHIFT));
        assertThat(KeyBindings.parse("mod+shift+T", true)).isEqualTo(ks(KeyEvent.VK_T, CMD | SHIFT));
        assertThat(KeyBindings.parse("ctrl+TAB", true)).isEqualTo(ks(KeyEvent.VK_TAB, CTRL));
        assertThat(KeyBindings.parse(" Mod + f5 ", false)).isEqualTo(ks(KeyEvent.VK_F5, CTRL));
        assertThat(KeyBindings.parse("mod+EQUALS", true)).isEqualTo(ks(KeyEvent.VK_EQUALS, CMD));
    }

    @Test
    void notasiRusakDitolak() {
        assertThat(KeyBindings.parse("", false)).isNull();
        assertThat(KeyBindings.parse("mod+shift+", false)).isNull();
        assertThat(KeyBindings.parse("hyper+T", false)).isNull();
        assertThat(KeyBindings.parse("mod+BUKAN_TOMBOL", false)).isNull();
    }

    @Test
    void formatBolakBalik() {
        for (boolean mac : new boolean[] {false, true}) {
            var b = KeyBindings.defaults(mac);
            for (var a : ShortcutAction.values()) {
                var key = b.get(a);
                assertThat(KeyBindings.parse(KeyBindings.format(key, mac), mac)).as(a.id()).isEqualTo(key);
            }
        }
        assertThat(KeyBindings.format(ks(KeyEvent.VK_TAB, CTRL | SHIFT), true)).isEqualTo("ctrl+shift+TAB");
        assertThat(KeyBindings.format(ks(KeyEvent.VK_P, CTRL | SHIFT), false)).isEqualTo("mod+shift+P");
    }

    @Test
    void defaultTidakSalingBentrokDanIdUnik() {
        for (boolean mac : new boolean[] {false, true}) {
            var b = KeyBindings.defaults(mac);
            var keys = new HashSet<KeyStroke>();
            for (var a : ShortcutAction.values()) {
                assertThat(keys.add(b.get(a))).as("%s di %s", a.id(), mac ? "Mac" : "Windows").isTrue();
                assertThat(ShortcutRules.check(a, b.get(a), mac).allowed()).as(a.id()).isTrue();
            }
        }
        var ids = new HashSet<String>();
        for (var a : ShortcutAction.values()) {
            assertThat(ids.add(a.id())).isTrue();
            assertThat(ShortcutAction.byId(a.id())).isEqualTo(a);
        }
    }

    @Test
    void hanyaOverrideYangDisimpan() {
        var b = KeyBindings.defaults(false)
                .with(ShortcutAction.NEW_HOST, ks(KeyEvent.VK_H, CTRL | SHIFT))
                .with(ShortcutAction.INJECT_ROOT, null)
                .with(ShortcutAction.CLOSE_TAB, ks(KeyEvent.VK_W, CTRL | SHIFT)); // sama dengan default

        assertThat(b.overrides()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "host.new", "mod+shift+H", "inject.root", ""));
        assertThat(b.isOverridden(ShortcutAction.CLOSE_TAB)).isFalse();
        assertThat(KeyBindings.of(b.overrides(), false)).isEqualTo(b);
        assertThat(b.reset(ShortcutAction.NEW_HOST).isOverridden(ShortcutAction.NEW_HOST)).isFalse();
    }

    @Test
    void overrideTidakDikenalAtauRusakDiabaikan() {
        var b = KeyBindings.of(Map.of("aksi.lama", "mod+K", "tab.new", "mod+???"), true);
        assertThat(b).isEqualTo(KeyBindings.defaults(true));
        assertThat(b.overriddenActions()).isEmpty();
    }

    @Test
    void aksiYangDigantiMenangSaatBentrokDenganDefault() {
        // file diedit manual: host baru memakai default "cari host"
        var b = KeyBindings.of(Map.of("host.new", "mod+F"), false);
        assertThat(b.actionFor(ks(KeyEvent.VK_F, CTRL))).contains(ShortcutAction.NEW_HOST);
        assertThat(b.actionFor(ks(KeyEvent.VK_Y, CTRL))).isEmpty();
        assertThat(b.actionFor(null)).isEmpty();
    }

    @Test
    void pindahkanShortcutMengosongkanPemilikLama() {
        var b = KeyBindings.defaults(true);
        var sudo = b.get(ShortcutAction.INJECT_SUDO);

        assertThat(ShortcutsDialog.conflicts(b, ShortcutAction.NEW_HOST, sudo)).containsExactly(ShortcutAction.INJECT_SUDO);
        assertThat(ShortcutsDialog.conflicts(b, ShortcutAction.INJECT_SUDO, sudo)).isEmpty();
        b = ShortcutsDialog.reassign(b, ShortcutAction.NEW_HOST, sudo);
        assertThat(b.get(ShortcutAction.NEW_HOST)).isEqualTo(sudo);
        assertThat(b.get(ShortcutAction.INJECT_SUDO)).isNull();
        assertThat(b.overrides()).containsEntry("inject.sudo", "").containsEntry("host.new", "mod+shift+P");
    }
}
