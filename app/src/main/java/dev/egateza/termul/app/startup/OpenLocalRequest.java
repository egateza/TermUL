package dev.egateza.termul.app.startup;

import dev.egateza.termul.core.instance.SingleInstance;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Buka tab terminal lokal di folder tertentu, dari menu klik kanan Explorer ({@link ExplorerMenu}). Datang sebagai
 * argumen command line ({@code --open-local=<id> --cwd "<folder>"}), lalu diteruskan ke instance yang sedang berjalan
 * lewat {@link SingleInstance} kalau TermUL sudah terbuka.
 *
 * <p>Yang diterima hanya id shell dan path folder: perintah shell selalu diambil dari daftar terminal lokal milik
 * aplikasi, jadi argumen ini tidak bisa dipakai untuk menjalankan perintah sembarang.
 *
 * @param shell     id pilihan terminal lokal; null = shell default
 * @param directory folder absolut tempat shell dimulai
 */
public record OpenLocalRequest(UUID shell, Path directory) {

    static final String ARG_OPEN_LOCAL = "--open-local=";
    /** Selalu argumen terakhir: nilainya bisa terpecah atau berakhir {@code "} (lihat {@link #directoryOf}). */
    static final String ARG_CWD = "--cwd";
    static final String COMMAND = "open-local";
    private static final Pattern DRIVE_ONLY = Pattern.compile("[A-Za-z]:");

    /** @return kosong kalau argumen tidak meminta tab lokal atau folder-nya tidak sah */
    public static Optional<OpenLocalRequest> parse(String[] args) {
        UUID shell = null;
        boolean requested = false;
        String directory = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith(ARG_OPEN_LOCAL)) {
                requested = true;
                shell = uuid(arg.substring(ARG_OPEN_LOCAL.length()));
            } else if (arg.equals(ARG_CWD)) {
                // "C:\" dari Explorer sampai sebagai C:" (\" = kutip ter-escape) dan bisa menelan argumen berikutnya
                directory = String.join(" ", Arrays.copyOfRange(args, i + 1, args.length));
                break;
            }
        }
        if (!requested) {
            return Optional.empty();
        }
        UUID id = shell;
        return directoryOf(directory).map(dir -> new OpenLocalRequest(id, dir));
    }

    /** Diteruskan ke instance yang sedang berjalan. */
    public SingleInstance.Request toRequest() {
        return new SingleInstance.Request(COMMAND, (shell == null ? "" : shell.toString()) + "\n" + directory);
    }

    /** @return kosong kalau bukan permintaan tab lokal yang sah */
    public static Optional<OpenLocalRequest> from(SingleInstance.Request request) {
        if (!COMMAND.equals(request.command())) {
            return Optional.empty();
        }
        String[] parts = request.argument().split("\n", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        UUID shell = uuid(parts[0]);
        return directoryOf(parts[1]).map(dir -> new OpenLocalRequest(shell, dir));
    }

    /**
     * Rapikan path folder dari Explorer: kutip sisa escape di akhir dibuang, dan {@code C:} menjadi {@code C:\} (tanpa
     * backslash artinya "folder aktif di drive C", bukan root-nya).
     *
     * @return kosong kalau bukan path absolut
     */
    static Optional<Path> directoryOf(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String d = raw.strip();
        while (d.endsWith("\"")) {
            d = d.substring(0, d.length() - 1).strip();
        }
        if (d.startsWith("\"")) {
            d = d.substring(1).strip();
        }
        if (d.isEmpty() || d.indexOf('\n') >= 0 || d.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        if (DRIVE_ONLY.matcher(d).matches()) {
            d += "\\";
        }
        try {
            Path path = Path.of(d);
            return path.isAbsolute() ? Optional.of(path.normalize()) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static UUID uuid(String s) {
        try {
            return s.isBlank() ? null : UUID.fromString(s.strip());
        } catch (IllegalArgumentException e) {
            return null; // profil tak dikenal: pakai shell default
        }
    }
}
