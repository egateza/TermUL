package dev.egateza.termul.app.snippet;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.snippet.DangerousCommands;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetPayload;
import dev.egateza.termul.core.snippet.SnippetStore;
import dev.egateza.termul.core.snippet.SnippetVariables;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menempel atau menyalin snippet: isi variabel, konfirmasi snippet berbahaya dan baris yang akan langsung berjalan,
 * lalu kirim ke terminal / clipboard dan ingat nilai variabelnya per host. EDT; simpan nilai variabel di executor I/O.
 */
public final class SnippetSender {

    private static final Logger log = LoggerFactory.getLogger(SnippetSender.class);

    /** Hasil yang ditampilkan ke user (toast). */
    public record Result(String message) {
    }

    private final SnippetStore store;
    private final Executor io;

    /** @param codeFont Font terminal dari pengaturan (family + ukuran), untuk menampilkan perintah */
    public SnippetSender(SnippetStore store, Executor io, java.util.function.Supplier<java.awt.Font> codeFont) {
        this.store = store;
        this.io = io;
        SnippetUi.useCodeFont(codeFont);
    }

    public SnippetStore store() {
        return store;
    }

    /** Nilai variabel awal: yang diingat untuk host ini, kalau belum ada default snippet. */
    public Map<String, String> initialValues(Snippet s, SnippetTarget target) {
        var saved = target == null ? Map.<String, String>of() : store.remembered(target.hostKey(), s.id());
        var out = new java.util.LinkedHashMap<String, String>();
        for (String name : s.variableNames()) {
            String v = saved.get(name);
            out.put(name, v != null ? v : s.variables().getOrDefault(name, ""));
        }
        return out;
    }

    /**
     * Tempel ke {@code target}. Snippet berbahaya dan snippet beberapa baris di shell tanpa bracketed paste (baris
     * awal langsung berjalan) dikonfirmasi dulu.
     *
     * @return pesan hasil, atau null kalau dibatalkan / tidak bisa dikirim
     */
    public Result paste(Component parent, Snippet s, Map<String, String> values, SnippetTarget target) {
        if (target == null || !target.canPaste() || !SnippetVariables.missing(s.command(), values).isEmpty()) {
            return null;
        }
        String resolved = SnippetVariables.resolve(s.command(), values);
        if (s.isDangerous() && !confirmDanger(parent, s, resolved, s.enter() ? Action.RUN : Action.PASTE, target)) {
            return null;
        }
        boolean bracketed = target.bracketedPaste();
        if (SnippetPayload.runsEarly(resolved, s.enter(), bracketed) && !SnippetConfirm.show(parent, true,
                I18n.t("snippet.confirm.early.title"), I18n.t("snippet.confirm.early.body", target.name()),
                resolved, I18n.t("snippet.confirm.early.ok"))) {
            return null;
        }
        target.paste(SnippetPayload.build(resolved, s.enter(), bracketed));
        remember(s, values, target);
        int lines = s.lines().size();
        return new Result(I18n.t(s.enter() ? "snippet.result.ran" : "snippet.result.pasted", s.title(), lines,
                target.name()));
    }

    /** Salin perintah (variabel sudah diisi) ke clipboard; snippet berbahaya dikonfirmasi dulu. */
    public Result copy(Component parent, Snippet s, Map<String, String> values, SnippetTarget target) {
        if (!SnippetVariables.missing(s.command(), values).isEmpty()) {
            return null;
        }
        String resolved = SnippetVariables.resolve(s.command(), values);
        if (s.isDangerous() && !confirmDanger(parent, s, resolved, Action.COPY, target)) {
            return null;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(resolved), null);
        remember(s, values, target);
        return new Result(I18n.t("snippet.result.copied", s.title(), s.lines().size()));
    }

    private void remember(Snippet s, Map<String, String> values, SnippetTarget target) {
        if (target == null || values.isEmpty()) {
            return;
        }
        var copy = Map.copyOf(values);
        io.execute(() -> {
            try {
                store.remember(target.hostKey(), s.id(), copy);
            } catch (RuntimeException e) {
                log.warn("Nilai variabel snippet tidak bisa disimpan: {}", e.getMessage());
            }
        });
    }

    private enum Action { PASTE, RUN, COPY }

    private static boolean confirmDanger(Component parent, Snippet s, String resolved, Action action,
                                         SnippetTarget target) {
        String why = DangerousCommands.detect(s.command())
                .map(what -> I18n.t("snippet.danger.why", what)).orElse(I18n.t("snippet.danger.marked"));
        String host = target == null ? "" : target.name();
        String body = switch (action) {
            case COPY -> I18n.t("snippet.confirm.danger.copy", s.title(), why);
            case RUN -> I18n.t("snippet.confirm.danger.run", s.title(), why, host);
            case PASTE -> I18n.t("snippet.confirm.danger.paste", s.title(), why, host);
        };
        String ok = I18n.t(switch (action) {
            case COPY -> "snippet.confirm.danger.okCopy";
            case RUN -> "snippet.confirm.danger.okRun";
            case PASTE -> "snippet.confirm.danger.okPaste";
        });
        return SnippetConfirm.show(parent, true, I18n.t("snippet.confirm.danger.title"), body, resolved, ok);
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
