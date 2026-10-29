package dev.egateza.termul.app.startup;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link ShortcutIcons.Links} lewat COM {@code IShellLinkW} + {@code IPersistFile}, sehingga format {@code .lnk}
 * (termasuk blok ikon berbasis variabel lingkungan) diurus Windows sendiri. Semua method wajib dipanggil di satu thread
 * platform (bukan virtual thread) di antara {@link #open()} dan {@link #close()}: COM terikat ke thread OS.
 */
final class WinShellLink implements ShortcutIcons.Links, AutoCloseable {

    private static final Guid.CLSID CLSID_SHELL_LINK = new Guid.CLSID("{00021401-0000-0000-C000-000000000046}");
    private static final Guid.IID IID_SHELL_LINK_W = new Guid.IID("{000214F9-0000-0000-C000-000000000046}");
    private static final Guid.IID IID_PERSIST_FILE = new Guid.IID("{0000010b-0000-0000-C000-000000000046}");

    // urutan vtable IShellLinkW / IPersistFile (setelah 3 method IUnknown)
    private static final int LINK_GET_PATH = 3;
    private static final int LINK_GET_ICON_LOCATION = 16;
    private static final int LINK_SET_ICON_LOCATION = 17;
    private static final int FILE_LOAD = 5;
    private static final int FILE_SAVE = 6;

    private static final int STGM_READ = 0;
    private static final int STGM_READWRITE = 2;
    private static final int MAX_PATH_CHARS = 1024;

    /** Thread sudah di apartemen COM lain mode; tetap bisa dipakai, tapi tidak boleh di-uninitialize oleh kita. */
    private static final int RPC_E_CHANGED_MODE = 0x80010106;

    private final boolean initialized;

    private WinShellLink(boolean initialized) {
        this.initialized = initialized;
    }

    /** Masuk apartemen COM di thread ini. */
    static WinShellLink open() throws IOException {
        WinNT.HRESULT hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
        if (hr.intValue() == RPC_E_CHANGED_MODE) {
            return new WinShellLink(false);
        }
        if (COMUtils.FAILED(hr)) {
            throw new IOException("COM tidak bisa diinisialisasi (0x" + Integer.toHexString(hr.intValue()) + ")");
        }
        return new WinShellLink(true); // S_OK atau S_FALSE: keduanya wajib diimbangi CoUninitialize
    }

    @Override
    public void close() {
        if (initialized) {
            Ole32.INSTANCE.CoUninitialize();
        }
    }

    @Override
    public String target(Path lnk) throws IOException {
        return withLink(lnk, false, link -> {
            char[] buf = new char[MAX_PATH_CHARS];
            check(link.call(LINK_GET_PATH, link.getPointer(), buf, buf.length, null, 0), "GetPath", lnk);
            return Native.toString(buf);
        });
    }

    @Override
    public String icon(Path lnk) throws IOException {
        return withLink(lnk, false, link -> {
            char[] buf = new char[MAX_PATH_CHARS];
            check(link.call(LINK_GET_ICON_LOCATION, link.getPointer(), buf, buf.length, new IntByReference()),
                    "GetIconLocation", lnk);
            return Native.toString(buf);
        });
    }

    @Override
    public void setIcon(Path lnk, String iconPath) throws IOException {
        withLink(lnk, true, link -> {
            check(link.call(LINK_SET_ICON_LOCATION, link.getPointer(), new WString(iconPath), 0),
                    "SetIconLocation", lnk);
            return null;
        });
    }

    private interface LinkWork<T> {
        T run(Com link) throws IOException;
    }

    private static <T> T withLink(Path lnk, boolean save, LinkWork<T> work) throws IOException {
        var pLink = new PointerByReference();
        check(Ole32.INSTANCE.CoCreateInstance(CLSID_SHELL_LINK, null, WTypes.CLSCTX_INPROC_SERVER, IID_SHELL_LINK_W,
                pLink).intValue(), "CoCreateInstance", lnk);
        var link = new Com(pLink.getValue());
        Com file = null;
        try {
            var pFile = new PointerByReference();
            check(link.QueryInterface(new Guid.REFIID(IID_PERSIST_FILE), pFile).intValue(), "QueryInterface", lnk);
            file = new Com(pFile.getValue());
            check(file.call(FILE_LOAD, file.getPointer(), new WString(lnk.toString()),
                    save ? STGM_READWRITE : STGM_READ), "Load", lnk);
            T result = work.run(link);
            if (save) {
                check(file.call(FILE_SAVE, file.getPointer(), null, 1), "Save", lnk); // null = file yang di-Load
            }
            return result;
        } finally {
            if (file != null) {
                file.Release();
            }
            link.Release();
        }
    }

    private static void check(int hr, String what, Path lnk) throws IOException {
        if (hr < 0) {
            throw new IOException(what + " gagal untuk " + lnk.getFileName() + " (0x" + Integer.toHexString(hr) + ")");
        }
    }

    /** Pointer antarmuka COM dengan pemanggil vtable (argumen pertama selalu pointer {@code this}). */
    private static final class Com extends Unknown {
        Com(Pointer p) {
            super(p);
        }

        int call(int vtableIndex, Object... args) {
            return _invokeNativeInt(vtableIndex, args);
        }
    }

    /** {@code SHChangeNotify}: minta Explorer / taskbar memuat ulang ikon. */
    interface Shell32Notify extends StdCallLibrary {
        Shell32Notify INSTANCE = Native.load("shell32", Shell32Notify.class, W32APIOptions.DEFAULT_OPTIONS);

        int SHCNE_UPDATEITEM = 0x00002000;
        int SHCNE_ASSOCCHANGED = 0x08000000;
        int SHCNF_IDLIST = 0x0000;
        int SHCNF_PATHW = 0x0005;

        void SHChangeNotify(int eventId, int flags, Pointer item1, Pointer item2);

        void SHChangeNotify(int eventId, int flags, WString item1, Pointer item2);
    }

    /** Beri tahu Explorer bahwa ikon {@code changed} (dan asosiasi ikon secara umum) berubah. */
    static void notifyChanged(java.util.List<Path> changed) {
        for (Path p : changed) {
            Shell32Notify.INSTANCE.SHChangeNotify(Shell32Notify.SHCNE_UPDATEITEM, Shell32Notify.SHCNF_PATHW,
                    new WString(p.toString()), null);
        }
        Shell32Notify.INSTANCE.SHChangeNotify(Shell32Notify.SHCNE_ASSOCCHANGED, Shell32Notify.SHCNF_IDLIST,
                (Pointer) null, null);
    }
}
