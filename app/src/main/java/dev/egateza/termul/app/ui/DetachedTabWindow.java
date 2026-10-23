package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.terminal.SplitPanes;
import dev.egateza.termul.app.terminal.TermPane;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Window terpisah berisi satu tab (terminal tunggal atau grup split) yang dilepas dari window utama. Sesi di dalamnya
 * tetap milik {@link MainFrame}: shortcut, pengaturan, konfirmasi tutup, dan keluar aplikasi tetap lewat sana. Semua
 * method dipanggil di EDT.
 */
final class DetachedTabWindow extends JFrame {

    /** Pemilik window (window utama). */
    interface Owner {
        /** Tombol tutup window: konfirmasi lalu tutup sesinya (seperti menutup tab). */
        void closeRequested(DetachedTabWindow window);

        /** Window ini menjadi window aktif: aksi menu/shortcut Terminal bekerja pada tab di sini. */
        void activated(DetachedTabWindow window);
    }

    private final SplitPanes<TermPane> group;
    private String customTitle; // nama tab dari user; null = nama host

    DetachedTabWindow(SplitPanes<TermPane> group, String customTitle, JMenuBar menu, Owner owner) {
        this.group = group;
        this.customTitle = customTitle;
        setIconImages(MainFrame.appIcons());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setJMenuBar(menu);
        getContentPane().add(group, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                owner.closeRequested(DetachedTabWindow.this);
            }

            @Override
            public void windowActivated(WindowEvent e) {
                owner.activated(DetachedTabWindow.this);
                SwingUtilities.invokeLater(() -> group.active().focusTerminal());
            }
        });
        refreshTitle();
    }

    SplitPanes<TermPane> group() {
        return group;
    }

    String customTitle() {
        return customTitle;
    }

    /** Lepas isi window untuk dikembalikan ke window utama (sesi tetap berjalan); window lalu di-dispose pemanggil. */
    SplitPanes<TermPane> takeGroup() {
        getContentPane().remove(group);
        return group;
    }

    /** Judul window: nama tab (atau nama host panel-panelnya) dan alamatnya. */
    void refreshTitle() {
        setTitle(title(customTitle, group) + " - TermUL");
    }

    static String title(String customTitle, SplitPanes<TermPane> group) {
        if (customTitle != null) {
            return customTitle;
        }
        return group.panes().stream().map(p -> p.displayName() + " (" + p.displayAddress() + ")")
                .collect(Collectors.joining(" | "));
    }
}
