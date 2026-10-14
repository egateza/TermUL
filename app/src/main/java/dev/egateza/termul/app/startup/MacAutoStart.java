package dev.egateza.termul.app.startup;

import dev.egateza.termul.core.io.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run at startup macOS lewat LaunchAgent milik user ({@code ~/Library/LaunchAgents}); launchd membacanya saat login
 * berikutnya. Tidak memanggil {@code launchctl}, jadi TermUL tidak ikut dijalankan ulang saat opsi dinyalakan.
 */
final class MacAutoStart implements AutoStart {

    private static final Logger log = LoggerFactory.getLogger(MacAutoStart.class);

    static final String LABEL = "dev.egateza.termul";

    private final Path plist;
    private final String appPath;

    MacAutoStart(Path userHome, String appPath) {
        this.plist = userHome.resolve("Library").resolve("LaunchAgents").resolve(LABEL + ".plist");
        this.appPath = Objects.requireNonNull(appPath);
    }

    Path plist() {
        return plist;
    }

    String content() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key>
                    <string>%s</string>
                    <key>ProgramArguments</key>
                    <array>
                        <string>%s</string>
                        <string>%s</string>
                    </array>
                    <key>RunAtLoad</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(LABEL, xml(appPath), ARG_TRAY);
    }

    @Override
    public Status status() {
        try {
            // plist dari lokasi instalasi lain dianggap belum aktif: mencentang lagi memperbaikinya
            return Files.readString(plist, StandardCharsets.UTF_8).equals(content()) ? Status.ENABLED : Status.DISABLED;
        } catch (NoSuchFileException e) {
            return Status.DISABLED;
        } catch (IOException e) {
            log.warn("Status run at startup tidak terbaca: {}", e.getMessage());
            return Status.DISABLED;
        }
    }

    @Override
    public void setEnabled(boolean on) throws IOException {
        if (on) {
            AtomicFiles.write(plist, content().getBytes(StandardCharsets.UTF_8));
        } else {
            Files.deleteIfExists(plist);
        }
    }

    static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
