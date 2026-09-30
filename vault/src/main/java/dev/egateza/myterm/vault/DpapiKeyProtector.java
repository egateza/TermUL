package dev.egateza.myterm.vault;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import java.nio.charset.StandardCharsets;

/**
 * Windows DPAPI ({@code CryptProtectData}, scope CurrentUser) via JNA.
 * Butuh {@code --enable-native-access=ALL-UNNAMED}.
 */
public final class DpapiKeyProtector implements KeyProtector {

    private static final byte[] ENTROPY = "MyTerm-vault-dek-v1".getBytes(StandardCharsets.US_ASCII);

    @Override
    public boolean isAvailable() {
        return Platform.isWindows();
    }

    @Override
    public byte[] protect(byte[] data) {
        return Crypt32Util.cryptProtectData(data, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "MyTerm", null);
    }

    @Override
    public byte[] unprotect(byte[] blob) {
        return Crypt32Util.cryptUnprotectData(blob, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null);
    }
}
