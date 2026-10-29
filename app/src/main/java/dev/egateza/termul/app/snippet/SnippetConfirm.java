package dev.egateza.termul.app.snippet;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Konfirmasi bergaya prototipe (kartu 440 px di atas latar gelap): judul, isi, perintah (opsional), lalu tombol aksi
 * bergaris merah dan "Batal" sebagai tombol utama, jadi Enter / Esc membatalkan. EDT.
 */
final class SnippetConfirm {

    private SnippetConfirm() {
    }

    /**
     * @param danger  judul merah dan bingkai merah (snippet berbahaya); false = konfirmasi biasa (hapus)
     * @param command perintah yang ditampilkan, atau null
     * @return true kalau user memilih {@code ok}
     */
    static boolean show(Component parent, boolean danger, String title, String body, String command, String ok) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        var dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        boolean[] result = {false};

        Supplier<Color> edge = danger ? () -> SnippetUi.alpha(SnippetUi.danger(), 0.6f) : SnippetUi::frame;
        var card = new SnippetUi.RoundPanel(new BorderLayout(0, 12), SnippetUi::panel, edge, 16);
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        var head = SnippetUi.label(title, SnippetUi.font(2, Font.BOLD), danger ? SnippetUi.danger() : SnippetUi.fg());
        card.add(head, BorderLayout.NORTH);

        var middle = new JPanel(new BorderLayout(0, 12));
        middle.setOpaque(false);
        middle.add(SnippetUi.wrapped(SnippetUi.esc(body), 400, SnippetUi.font(0, Font.PLAIN), SnippetUi.fg()),
                BorderLayout.NORTH);
        if (command != null) {
            var code = new JTextArea(command);
            code.setEditable(false);
            code.setLineWrap(true);
            code.setFont(SnippetUi.mono(0));
            code.setForeground(SnippetUi.fg());
            code.setRows(Math.min(8, command.split("\n", -1).length));
            middle.add(SnippetUi.codeBox(code), BorderLayout.CENTER);
        }
        card.add(middle, BorderLayout.CENTER);

        var okButton = new SnippetUi.Button(ok, null, SnippetUi.Variant.DANGER);
        var cancel = new SnippetUi.Button(I18n.t("snippet.cancel"), null, SnippetUi.Variant.PRIMARY);
        okButton.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        buttons.add(okButton);
        buttons.add(cancel);
        card.add(buttons, BorderLayout.SOUTH);

        var root = dialog.getRootPane();
        root.setOpaque(false);
        root.registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.setDefaultButton(cancel);
        SnippetUi.roundWindow(dialog); // sudut kartu membulat
        dialog.setContentPane(card);
        dialog.setSize(440, card.getPreferredSize().height);
        SnippetUi.keepSizeAcrossScreens(dialog);
        dialog.setLocationRelativeTo(owner);
        Runnable undim = SnippetUi.dim(owner);
        SwingUtilities.invokeLater(cancel::requestFocusInWindow);
        try {
            dialog.setVisible(true);
        } finally {
            undim.run();
        }
        return result[0];
    }

    /** true kalau window tanpa dekorasi boleh berlatar transparan per piksel di layar ini. */
    static boolean translucent(Window w) {
        var gc = w.getGraphicsConfiguration();
        var device = gc != null ? gc.getDevice()
                : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        return device.isWindowTranslucencySupported(
                java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
    }
}
