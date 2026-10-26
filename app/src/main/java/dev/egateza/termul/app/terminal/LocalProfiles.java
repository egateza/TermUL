package dev.egateza.termul.app.terminal;

import dev.egateza.termul.core.localshell.CommandLine;
import dev.egateza.termul.core.localshell.LocalProfile;
import dev.egateza.termul.core.localshell.LocalProfileStore;
import dev.egateza.termul.localterm.DetectedShell;
import dev.egateza.termul.localterm.LocalShells;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pilihan terminal lokal (ADR 0005 tahap 2): shell yang terdeteksi otomatis + profil buatan user, dengan satu default.
 * Deteksi (I/O) dilakukan sekali di luar EDT lewat {@link #detect()}; {@link #choices} hanya membaca memori.
 */
public final class LocalProfiles {

    /**
     * Satu pilihan di tree, popup Tab baru, dan dialog.
     *
     * @param detected true = shell terdeteksi (tidak bisa diedit/dihapus; duplikat untuk mengubahnya)
     */
    public record Choice(UUID id, String name, List<String> command, String directory, Map<String, String> environment,
                         String terminalTheme, boolean detected, boolean isDefault) {

        /** Perintah lengkap sebagai satu baris (untuk keterangan abu-abu). */
        public String commandLine() {
            return CommandLine.join(command);
        }

        /** Salinan yang bisa diedit (profil baru dengan id baru). */
        public LocalProfile toNewProfile(String newName) {
            return new LocalProfile(UUID.randomUUID(), newName, command, directory, environment, terminalTheme);
        }
    }

    private final LocalProfileStore store;
    private final Function<List<String>, List<DetectedShell>> wslShells;
    private volatile List<DetectedShell> detected = List.of();

    public LocalProfiles(LocalProfileStore store) {
        this(store, LocalShells::wsl);
    }

    LocalProfiles(LocalProfileStore store, Function<List<String>, List<DetectedShell>> wslShells) {
        this.store = store;
        this.wslShells = wslShells;
    }

    public LocalProfileStore store() {
        return store;
    }

    /** Deteksi ulang shell terpasang. Blocking: jangan dipanggil di EDT. */
    public void detect() {
        setDetected(LocalShells.detectCurrent());
    }

    void setDetected(List<DetectedShell> shells) {
        detected = List.copyOf(shells);
    }

    /**
     * Pilihan yang aktif (untuk tree, Tab baru, Quick Open, menu Explorer): default dulu, lalu shell terdeteksi, lalu
     * profil buatan user (urut nama). Default = pilihan tersimpan kalau masih ada dan aktif; selain itu pilihan aktif
     * pertama. Kalau semuanya nonaktif (mis. file diedit manual), semua tetap ditawarkan supaya selalu ada terminal.
     *
     * @param wslDistros distro WSL terpasang (kosong kalau Manajemen WSL mati)
     */
    public List<Choice> choices(List<String> wslDistros) {
        var all = allChoices(wslDistros);
        var state = store.snapshot();
        var enabled = all.stream().filter(c -> !state.isDisabled(c.id())).toList();
        return enabled.isEmpty() ? all : enabled;
    }

    /** Semua pilihan termasuk yang nonaktif (dialog Profil terminal lokal), dengan urutan yang sama. */
    public List<Choice> allChoices(List<String> wslDistros) {
        var shells = new ArrayList<>(detected);
        shells.addAll(wslShells.apply(wslDistros));
        var state = store.snapshot();
        var all = new ArrayList<Choice>();
        for (DetectedShell s : shells) {
            all.add(new Choice(s.id(), s.name(), s.command(), "", Map.of(), null, true, false));
        }
        for (LocalProfile p : state.profiles()) {
            all.add(new Choice(p.id(), p.name(), p.command(), p.directory(), p.environment(), p.terminalTheme(),
                    false, false));
        }
        var enabled = all.stream().map(Choice::id).filter(id -> !state.isDisabled(id)).toList();
        var candidates = enabled.isEmpty() ? all.stream().map(Choice::id).toList() : enabled;
        UUID defaultId = candidates.stream().filter(id -> id.equals(state.defaultId())).findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.getFirst());
        var result = new ArrayList<Choice>(all.size());
        all.stream().filter(c -> c.id().equals(defaultId)).map(LocalProfiles::asDefault).forEach(result::add);
        all.stream().filter(c -> !c.id().equals(defaultId)).forEach(result::add);
        return result;
    }

    public Optional<Choice> find(UUID id, List<String> wslDistros) {
        return choices(wslDistros).stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** Pilihan default (selalu ada: deteksi minimal menghasilkan shell cadangan). */
    public Optional<Choice> defaultChoice(List<String> wslDistros) {
        return choices(wslDistros).stream().findFirst();
    }

    private static Choice asDefault(Choice c) {
        return new Choice(c.id(), c.name(), c.command(), c.directory(), c.environment(), c.terminalTheme(),
                c.detected(), true);
    }
}
