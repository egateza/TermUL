package dev.egateza.termul.app.sftp;

import dev.egateza.termul.sftp.RemoteEntry;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.List;

/**
 * File remote yang di-drag dari tabel SFTP (hanya di dalam aplikasi). Panel lokal yang menerima drop meminta
 * {@code source} mengunduhnya, jadi drop tetap memakai koneksi host asal file tersebut.
 */
record RemoteSelection(SftpPanel source, List<RemoteEntry> entries) implements Transferable {

    /** Objek lokal JVM: tidak diserialisasi dan tidak bisa di-drop ke aplikasi lain. */
    static final DataFlavor FLAVOR = flavor();

    private static DataFlavor flavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + RemoteSelection.class.getName(),
                    "TermUL remote files", RemoteSelection.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    RemoteSelection {
        entries = List.copyOf(entries);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {FLAVOR};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return this;
    }
}
