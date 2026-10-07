package dev.egateza.termul.app.vault;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Edt;
import dev.egateza.termul.vault.CredentialVault;
import dev.egateza.termul.vault.Secrets;
import dev.egateza.termul.vault.VaultException;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pintu akses ke vault untuk UI: unlock on-demand (DPAPI dulu, lalu prompt master password),
 * membuat vault baru kalau belum ada, dan auto-lock setelah idle.
 *
 * <p>Kunci manual ({@link #lock()}) berlaku sampai user sendiri membuka vault lagi: selama itu key DPAPI ("ingat di
 * PC ini") tidak dipakai untuk unlock on-demand, jadi inject/connect meminta master password dan auto-inject sudo
 * dilewati. Hanya {@link #unlockExplicitly()} (menu "Buka vault") yang boleh memakai DPAPI lagi. Auto-lock idle
 * tidak lengket.
 *
 * <p>{@link #ensureUnlocked()} <b>tidak boleh</b> dipanggil di EDT (Argon2id ±0,5 detik + dialog blocking).
 */
public final class VaultGate implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VaultGate.class);
    private static final int MAX_ATTEMPTS = 3;

    private final CredentialVault vault;
    private final Supplier<Component> parent;
    private final Duration idleLock;
    private final ScheduledExecutorService timer;
    private final Object unlockLock = new Object(); // satu prompt unlock pada satu waktu
    private volatile long lastUse = System.nanoTime();
    private volatile boolean lockedByUser; // true sejak "Kunci vault" sampai vault dibuka lagi

    public VaultGate(CredentialVault vault, Supplier<Component> parent, Duration idleLock) {
        this.vault = vault;
        this.parent = parent;
        this.idleLock = idleLock;
        this.timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("vault-autolock").daemon().factory());
        long period = Math.max(1, Math.min(60, idleLock.toSeconds()));
        timer.scheduleAtFixedRate(this::autoLockIfIdle, period, period, TimeUnit.SECONDS);
    }

    public CredentialVault vault() {
        touch();
        return vault;
    }

    public void touch() {
        lastUse = System.nanoTime();
    }

    /**
     * Memastikan vault terbuka. Membuat vault baru kalau belum ada (dengan persetujuan user).
     *
     * @return false kalau user membatalkan
     */
    public boolean ensureUnlocked() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("ensureUnlocked() tidak boleh dipanggil di EDT");
        }
        synchronized (unlockLock) {
            touch();
            if (vault.isUnlocked()) {
                return true;
            }
            if (!vault.exists()) {
                return createInteractive();
            }
            if (tryOsKey()) {
                return true;
            }
            String message = I18n.t(lockedByUser ? "vault.unlock.locked" : "vault.unlock.prompt");
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                char[] pw = askPasswords(I18n.t("vault.unlock.title"), new String[] {message})[0];
                if (pw == null) {
                    return false;
                }
                try {
                    vault.unlock(pw);
                    lockedByUser = false;
                    log.info("Vault dibuka");
                    return true;
                } catch (VaultException.WrongPassword e) {
                    message = I18n.t("vault.unlock.wrong", String.valueOf(attempt), String.valueOf(MAX_ATTEMPTS));
                }
            }
            showError(I18n.t("vault.title"), I18n.t("vault.unlock.tooMany"));
            return false;
        }
    }

    /**
     * Buka vault atas permintaan user (menu "Buka vault"): key DPAPI boleh dipakai lagi walaupun vault sebelumnya
     * dikunci manual. Panggil di luar EDT.
     *
     * @return false kalau user membatalkan
     */
    public boolean unlockExplicitly() {
        synchronized (unlockLock) {
            if (lockedByUser && !vault.isUnlocked() && vault.exists() && vault.unlockWithOsKey()) {
                lockedByUser = false;
                touch();
                log.info("Vault dibuka dengan key DPAPI");
                return true;
            }
            return ensureUnlocked();
        }
    }

    /** true kalau vault dikunci user lewat menu dan belum dibuka lagi. */
    public boolean isLockedByUser() {
        return lockedByUser && !vault.isUnlocked();
    }

    /** Unlock dengan key DPAPI tanpa dialog, kecuali vault sedang dikunci manual. */
    boolean tryOsKey() {
        synchronized (unlockLock) {
            if (lockedByUser || !vault.unlockWithOsKey()) {
                return false;
            }
            log.info("Vault dibuka dengan key DPAPI");
            return true;
        }
    }

    private boolean createInteractive() {
        var confirm = new AtomicReference<Boolean>(false);
        Edt.runAndWait(() -> confirm.set(JOptionPane.showConfirmDialog(parent.get(),
                I18n.t("vault.create.confirm"),
                I18n.t("vault.create.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION));
        return confirm.get() && createNewVault();
    }

    /** Meminta master password baru (dua kali) lalu membuat vault. @return false kalau dibatalkan. */
    private boolean createNewVault() {
        while (true) {
            char[][] pws = askPasswords(I18n.t("vault.create.title"),
                    new String[] {I18n.t("vault.create.password"), I18n.t("vault.create.repeat")});
            char[] first = pws[0];
            char[] second = pws[1];
            if (first == null || second == null) {
                Secrets.zero(first);
                Secrets.zero(second);
                return false;
            }
            boolean same = java.util.Arrays.equals(first, second);
            Secrets.zero(second);
            if (!same) {
                Secrets.zero(first);
                showError(I18n.t("vault.create.title"), I18n.t("vault.create.mismatch"));
                continue;
            }
            try {
                vault.create(first);
                lockedByUser = false;
                return true;
            } catch (VaultException e) {
                showError(I18n.t("vault.create.title"), e.getMessage());
            }
        }
    }

    /**
     * "Lupa master password": setelah konfirmasi, vault yang sedang dipakai diganti vault baru. File lama di-rename
     * ke backup (ceklis, default aktif) atau dihapus. Profil tetap ada, tetapi password yang tersimpan harus diisi
     * ulang. Panggil di luar EDT.
     */
    public void forgetMasterPasswordInteractive() {
        synchronized (unlockLock) {
            touch();
            if (!vault.exists()) {
                showInfo(I18n.t("vault.forget.title"), I18n.t("vault.forget.none"));
                return;
            }
            String[] options = {I18n.t("vault.forget.confirmButton"), I18n.t("vault.forget.cancelButton")};
            var choice = new AtomicReference<Integer>(JOptionPane.CLOSED_OPTION);
            var keepBackup = new AtomicReference<Boolean>(true);
            Edt.runAndWait(() -> {
                var panel = new JPanel(new BorderLayout(0, 12));
                panel.add(new JLabel(html(I18n.t("vault.forget.confirm"))), BorderLayout.CENTER);
                var backupBox = new JCheckBox(I18n.t("vault.forget.backup"), true);
                panel.add(backupBox, BorderLayout.SOUTH);
                choice.set(JOptionPane.showOptionDialog(parent.get(), panel, I18n.t("vault.forget.title"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]));
                keepBackup.set(backupBox.isSelected());
            });
            if (choice.get() != 0) {
                return;
            }
            if (!keepBackup.get() && !confirmWarning(I18n.t("vault.forget.title"), I18n.t("vault.forget.noBackupConfirm"))) {
                return;
            }
            Path backup;
            try {
                backup = vault.forgetMasterPassword(keepBackup.get());
            } catch (VaultException e) {
                showError(I18n.t("vault.forget.title"), e.getMessage());
                return;
            }
            showInfo(I18n.t("vault.forget.title"), backup == null
                    ? I18n.t("vault.forget.doneDeleted")
                    : I18n.t("vault.forget.done", backup.getFileName().toString()));
            createNewVault();
        }
    }

    /** Mengganti master password secara interaktif. Panggil di luar EDT. */
    public void changeMasterPasswordInteractive() {
        char[][] pws = askPasswords(I18n.t("vault.change.title"),
                new String[] {I18n.t("vault.change.old"), I18n.t("vault.change.new"), I18n.t("vault.change.repeat")});
        if (pws[0] == null || pws[1] == null || pws[2] == null) {
            for (char[] p : pws) {
                Secrets.zero(p);
            }
            return;
        }
        boolean same = java.util.Arrays.equals(pws[1], pws[2]);
        Secrets.zero(pws[2]);
        if (!same) {
            Secrets.zero(pws[0]);
            Secrets.zero(pws[1]);
            showError(I18n.t("vault.change.title"), I18n.t("vault.change.mismatch"));
            return;
        }
        try {
            vault.changeMasterPassword(pws[0], pws[1]);
            showInfo(I18n.t("vault.change.title"), I18n.t("vault.change.success"));
        } catch (VaultException e) {
            showError(I18n.t("vault.change.title"), e.getMessage());
        }
    }

    /** Kunci manual: berlaku sampai user membuka vault lagi (lihat keterangan class). */
    public void lock() {
        synchronized (unlockLock) {
            lockedByUser = true;
            vault.lock();
        }
        log.info("Vault dikunci");
    }

    void autoLockIfIdle() {
        if (vault.isUnlocked() && System.nanoTime() - lastUse > idleLock.toNanos()) {
            vault.lock();
            log.info("Vault dikunci otomatis setelah idle {} menit", idleLock.toMinutes());
        }
    }

    /** Dialog berisi beberapa field password; elemen null kalau dibatalkan. */
    private char[][] askPasswords(String title, String[] labels) {
        var result = new char[labels.length][];
        Edt.runAndWait(() -> {
            var panel = new JPanel(new GridLayout(0, 1, 0, 4));
            var fields = new JPasswordField[labels.length];
            for (int i = 0; i < labels.length; i++) {
                panel.add(new JLabel(labels[i]));
                fields[i] = new JPasswordField(24);
                panel.add(fields[i]);
            }
            var pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            var dialog = pane.createDialog(parent.get(), title);
            dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e) {
                    fields[0].requestFocusInWindow();
                }
            });
            fields[fields.length - 1].addActionListener(e -> {
                pane.setValue(JOptionPane.OK_OPTION);
                dialog.dispose();
            });
            dialog.setVisible(true);
            boolean ok = Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue());
            for (int i = 0; i < fields.length; i++) {
                result[i] = ok ? fields[i].getPassword() : null;
                fields[i].setText("");
            }
            dialog.dispose();
        });
        return result;
    }

    /** Teks multi-baris untuk JLabel (baris baru dari i18n tetap terlihat). */
    private static String html(String text) {
        return "<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</html>";
    }

    private boolean confirmWarning(String title, String message) {
        var ok = new AtomicReference<Boolean>(false);
        Edt.runAndWait(() -> ok.set(JOptionPane.showConfirmDialog(parent.get(), message, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION));
        return ok.get();
    }

    private void showError(String title, String message) {
        Edt.runAndWait(() -> JOptionPane.showMessageDialog(parent.get(), message, title, JOptionPane.ERROR_MESSAGE));
    }

    private void showInfo(String title, String message) {
        Edt.runAndWait(() -> JOptionPane.showMessageDialog(parent.get(), message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    @Override
    public void close() {
        timer.shutdownNow();
        vault.lock();
    }
}
