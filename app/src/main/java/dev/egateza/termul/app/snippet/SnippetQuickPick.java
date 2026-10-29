package dev.egateza.termul.app.snippet;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.ShortcutAction;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.core.snippet.Snippet;
import dev.egateza.termul.core.snippet.SnippetVariables;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Menu snippet di atas terminal (prototipe "Menu Snippet"): hanya snippet yang berlaku untuk host tab ini,
 * dikelompokkan per favorit dan kategori, dengan filter. Snippet tanpa variabel yang tidak berbahaya langsung ditempel
 * saat diklik; yang lain dibuka dulu untuk mengisi variabel / meninjau perintahnya. EDT.
 */
public final class SnippetQuickPick {

    static final int WIDTH = 400;
    /** Prototipe: menu maksimal 600 px; dikurangi header (filter) dan footer (Kelola snippet). */
    static final int MAX_LIST_HEIGHT = 500;

    private final SnippetTarget target;
    private final SnippetSender sender;
    private final Runnable manage;
    private final Consumer<String> toast;
    private final JPopupMenu popup = new JPopupMenu();
    private final JTextField filter = new JTextField();
    private final JPanel list = new ListPanel();
    private final JScrollPane scroll;
    private final JLabel count = SnippetUi.mutedLabel("");
    private final JLabel hidden = SnippetUi.mutedLabel("");
    private final Map<UUID, Map<String, String>> values = new HashMap<>();
    private UUID expanded;

    private SnippetQuickPick(SnippetTarget target, SnippetSender sender, Runnable manage, Consumer<String> toast) {
        this.target = target;
        this.sender = sender;
        this.manage = manage;
        this.toast = toast;
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(SnippetUi.panel());
        list.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        var panel = new JPanel(new BorderLayout());
        panel.setBackground(SnippetUi.panel());
        panel.add(header(), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(footer(), BorderLayout.SOUTH);
        popup.setLayout(new BorderLayout());
        popup.add(panel);
        popup.setBackground(SnippetUi.panel());
        popup.setBorder(BorderFactory.createLineBorder(SnippetUi.frame()));
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                SwingUtilities.invokeLater(target::focus); // kembali mengetik di terminal
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
        rebuild();
    }

    /**
     * Tampilkan menu di {@code invoker} (koordinat {@code x}, {@code y} relatif ke invoker); {@code alignRight}: tepi
     * kanan menu di {@code x}, {@code above}: tepi bawah menu di {@code y}.
     */
    public static void show(Component invoker, int x, int y, boolean alignRight, boolean above, SnippetTarget target,
                            SnippetSender sender, Runnable manage, Consumer<String> toast) {
        var pick = new SnippetQuickPick(target, sender, manage, toast);
        var size = pick.popup.getPreferredSize();
        pick.popup.show(invoker, alignRight ? x - size.width : x, above ? y - size.height : y);
        SwingUtilities.invokeLater(pick.filter::requestFocusInWindow);
    }

    // --- bagian tetap ---

    private JComponent header() {
        // "Snippet untuk" biasa + nama host Bold dari Font aplikasi (bukan <b> HTML, yang tidak ikut font pilihan)
        var title = SnippetUi.label(I18n.t("snippet.quick.for"), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
        title.setIcon(SnippetUi.dot(7, target.canPaste() ? SnippetUi::success : SnippetUi::muted));
        title.setIconTextGap(8);
        var host = SnippetUi.label(target.name(), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
        var row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.add(title);
        row.add(Box.createHorizontalStrut(4));
        row.add(host);
        row.add(Box.createHorizontalStrut(8));
        String folder = target.hostGroup().isEmpty() ? I18n.t("snippet.quick.noFolder") : target.hostGroup();
        row.add(new SnippetUi.Chip(18, 8, SnippetUi.label(folder, SnippetUi.font(0, Font.PLAIN), SnippetUi.muted())));
        row.add(Box.createHorizontalGlue());
        row.add(Box.createHorizontalStrut(8));
        row.add(count);

        SnippetUi.styleField(filter, 28);
        filter.setFont(SnippetUi.font(0, Font.PLAIN));
        filter.putClientProperty("JTextField.placeholderText", I18n.t("snippet.quick.filter"));
        filter.getAccessibleContext().setAccessibleName(I18n.t("snippet.quick.filter"));
        filter.getDocument().addDocumentListener(onChange(this::rebuild));
        filter.addActionListener(e -> firstItem().ifPresent(ItemRow::activate));
        filter.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "firstItem");
        filter.getActionMap().put("firstItem", action(() -> firstItem().ifPresent(JComponent::requestFocusInWindow)));

        var box = new JPanel(new BorderLayout(0, 8));
        box.setBackground(SnippetUi.panel());
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        box.add(row, BorderLayout.NORTH);
        box.add(filter, BorderLayout.CENTER);
        return box;
    }

    private JComponent footer() {
        hidden.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        hidden.setOpaque(true);
        hidden.setBackground(SnippetUi.panel());

        var manageRow = new HoverRow(SnippetUi.bar(), () -> {
            popup.setVisible(false);
            manage.run();
        });
        manageRow.setLayout(new BorderLayout(8, 0));
        manageRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        manageRow.setPreferredSize(new Dimension(WIDTH, 36));
        var label = SnippetUi.label(I18n.t("snippet.quick.manage"), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
        label.setIcon(SnippetUi.sliders(14));
        label.setIconTextGap(8);
        manageRow.add(label, BorderLayout.CENTER);
        manageRow.add(SnippetUi.mutedLabel(Shortcuts.text(ShortcutAction.SNIPPETS)), BorderLayout.EAST);
        manageRow.getAccessibleContext().setAccessibleName(I18n.t("snippet.quick.manage"));

        var wrap = new JPanel(new BorderLayout());
        wrap.add(hidden, BorderLayout.NORTH);
        wrap.add(manageRow, BorderLayout.SOUTH);
        return wrap;
    }

    private java.util.Optional<ItemRow> firstItem() {
        for (var c : list.getComponents()) {
            if (c instanceof ItemPanel item) {
                return java.util.Optional.of(item.row);
            }
        }
        return java.util.Optional.empty();
    }

    /** Snippet untuk host ini yang cocok dengan filter, per kelompok: Favorit, setiap kategori, Tanpa kategori. */
    static Map<String, List<Snippet>> groups(List<Snippet> available, List<String> categoryIds,
                                              Function<String, String> categoryName, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        var shown = available.stream().filter(s -> q.isEmpty()
                || (s.title() + " " + s.command()).toLowerCase(Locale.ROOT).contains(q)).toList();
        var out = new LinkedHashMap<String, List<Snippet>>();
        var favs = shown.stream().filter(Snippet::favorite).toList();
        if (!favs.isEmpty()) {
            out.put(I18n.t("snippet.nav.favorites"), favs);
        }
        for (String id : categoryIds) {
            var items = shown.stream().filter(s -> !s.favorite() && s.category().equals(id)).toList();
            if (!items.isEmpty()) {
                out.put(categoryName.apply(id), items);
            }
        }
        var none = shown.stream().filter(s -> !s.favorite() && !categoryIds.contains(s.category())).toList();
        if (!none.isEmpty()) {
            out.put(I18n.t("snippet.nav.uncategorized"), none);
        }
        return out;
    }

    private void rebuild() {
        var store = sender.store();
        var all = store.snapshot();
        var available = store.forHost(target.hostName(), target.hostGroup());
        var ids = store.categories().stream().map(c -> c.id()).toList();
        var groups = groups(available, ids, store::categoryName, filter.getText());
        list.removeAll();
        groups.forEach((label, items) -> {
            var head = SnippetUi.section(label);
            head.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
            head.setAlignmentX(Component.LEFT_ALIGNMENT);
            head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
            list.add(head);
            for (Snippet s : items) {
                var item = new ItemPanel(s);
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                list.add(item);
            }
        });
        if (groups.isEmpty()) {
            var empty = SnippetUi.wrapped(SnippetUi.esc(available.isEmpty()
                    ? I18n.t("snippet.quick.none", target.name()) : I18n.t("snippet.quick.noMatch")), 340,
                    SnippetUi.font(0, Font.PLAIN), SnippetUi.muted());
            empty.setHorizontalAlignment(JLabel.CENTER);
            empty.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(empty);
        }
        count.setText(I18n.t("snippet.quick.count", available.size()));
        int other = all.size() - available.size();
        hidden.setText(other == 1 ? I18n.t("snippet.quick.hidden1") : I18n.t("snippet.quick.hidden", other));
        hidden.setVisible(other > 0);
        list.revalidate();
        // tinggi teks yang dibungkus baru benar setelah teks itu mendapat lebar: layout sekali dengan lebar menu,
        // buang ukuran yang di-cache saat belum ada lebar, lalu ukur lagi. Kalau melebihi batas, scrollbar muncul dan
        // lebarnya berkurang, jadi diukur ulang dengan lebar itu. validate() tidak dipakai karena tidak berbuat
        // apa-apa sebelum popup tampil (belum ada peer).
        int h = measuredHeight(WIDTH);
        if (h > MAX_LIST_HEIGHT) {
            measuredHeight(WIDTH - scroll.getVerticalScrollBar().getPreferredSize().width);
            h = MAX_LIST_HEIGHT;
        }
        scroll.setPreferredSize(new Dimension(WIDTH, h));
        popup.pack();
        popup.revalidate();
        popup.repaint();
    }

    private int measuredHeight(int width) {
        list.setSize(width, Short.MAX_VALUE);
        layoutTree(list);
        return list.getPreferredSize().height;
    }

    private static void layoutTree(Container c) {
        c.doLayout();
        for (var child : c.getComponents()) {
            if (child instanceof Container sub) {
                layoutTree(sub);
            }
        }
        c.invalidate();
    }

    private Map<String, String> valuesOf(Snippet s) {
        return values.computeIfAbsent(s.id(), id -> sender.initialValues(s, target));
    }

    private void done(SnippetSender.Result result) {
        if (result != null) {
            popup.setVisible(false);
            toast.accept(result.message());
        }
    }

    /**
     * Isi daftar selalu selebar viewport. Tanpa ini pratinjau perintah yang panjang membuat daftar lebih lebar dari
     * menu, lalu viewport menggeser isinya ke samping (mis. saat snippet berbahaya dibuka), dan teks terpotong.
     */
    private static final class ListPanel extends JPanel implements javax.swing.Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // --- baris ---

    /** Baris yang bisa diklik / Enter, dengan latar saat disorot (prototipe {@code .snq-item:hover}). */
    private static class HoverRow extends JPanel {
        private final java.awt.Color base;
        private final Runnable onActivate;

        HoverRow(java.awt.Color base, Runnable onActivate) {
            this.base = base;
            this.onActivate = onActivate;
            setOpaque(true);
            setFocusable(true);
            setBackground(base);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!contains(e.getPoint())) {
                        setHover(false);
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        activate();
                    }
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
            for (var key : new int[] {KeyEvent.VK_ENTER, KeyEvent.VK_SPACE}) {
                getInputMap().put(KeyStroke.getKeyStroke(key, 0), "activate");
            }
            getActionMap().put("activate", SnippetQuickPick.action(this::activate));
        }

        void activate() {
            onActivate.run();
        }

        private void setHover(boolean on) {
            setBackground(on ? SnippetUi.hover() : base);
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (isFocusOwner()) {
                var g2 = (Graphics2D) g.create();
                g2.setColor(SnippetUi.accent());
                g2.setStroke(new BasicStroke(2));
                g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                g2.dispose();
            }
        }
    }

    private final class ItemRow extends HoverRow {
        ItemRow(java.awt.Color base, Runnable onActivate) {
            super(base, onActivate);
        }
    }

    /** Satu snippet: baris (judul, badge, aksi, pratinjau perintah) dan, kalau dibuka, isian variabel + tombol. */
    private final class ItemPanel extends JPanel {
        final ItemRow row;

        ItemPanel(Snippet s) {
            super(new BorderLayout());
            boolean direct = s.variableNames().isEmpty() && !s.isDangerous();
            boolean open = s.id().equals(expanded);
            var base = open ? SnippetUi.selection() : SnippetUi.panel();
            setBackground(base);
            row = new ItemRow(base, () -> {
                if (direct) {
                    done(sender.paste(popup.getInvoker(), s, Map.of(), target));
                } else {
                    expanded = open ? null : s.id();
                    rebuild();
                }
            });
            row.setLayout(new BorderLayout(0, 3));
            row.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
            var line = new JPanel(new BorderLayout(6, 0));
            line.setOpaque(false);
            // judul (menyusut dengan elipsis) lalu badge, rata kiri dengan pratinjau di bawahnya
            var titleRow = new JPanel();
            titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
            titleRow.setOpaque(false);
            var title = SnippetUi.label(s.title(), SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
            title.setMinimumSize(new Dimension(0, title.getPreferredSize().height));
            titleRow.add(title);
            var badges = new JPanel();
            SnippetUi.addBadges(badges, s);
            for (var b : badges.getComponents()) {
                titleRow.add(Box.createHorizontalStrut(6));
                titleRow.add(b);
            }
            titleRow.add(Box.createHorizontalGlue());
            line.add(titleRow, BorderLayout.CENTER);
            String action = direct ? I18n.t(s.enter() ? "snippet.quick.run" : "snippet.quick.paste")
                    : I18n.t(open ? "snippet.quick.hide" : "snippet.quick.review");
            line.add(SnippetUi.mutedLabel(action), BorderLayout.EAST);
            row.add(line, BorderLayout.NORTH);
            var preview = SnippetUi.label(SnippetUi.preview(s), SnippetUi.mono(0), SnippetUi.muted());
            row.add(preview, BorderLayout.CENTER);
            row.setToolTipText(direct
                    ? I18n.t(s.enter() ? "snippet.quick.tipRun" : "snippet.quick.tipPaste", target.name())
                    : I18n.t("snippet.quick.tipReview"));
            row.getAccessibleContext().setAccessibleName(s.title());
            add(row, BorderLayout.NORTH);
            if (open) {
                add(details(s), BorderLayout.CENTER);
            }
        }

        /**
         * Tinggi maksimum = tinggi yang dibutuhkan pada lebar saat ini. Jangan dikunci di constructor: saat itu belum
         * ada lebar, sehingga teks yang dibungkus (peringatan berbahaya) diukur terlalu pendek dan tombol terpotong,
         * apalagi dengan font yang lebih besar.
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private JComponent details(Snippet s) {
            var box = new JPanel(new GridBagLayout());
            box.setOpaque(false);
            box.setBorder(BorderFactory.createEmptyBorder(2, 12, 12, 12));
            var gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.gridwidth = 2;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;
            gc.insets = new Insets(0, 0, 8, 0);
            if (s.isDangerous()) {
                var text = SnippetUi.text(I18n.t(s.enter() ? "snippet.quick.dangerRun" : "snippet.quick.dangerPaste"),
                        SnippetUi.font(0, Font.PLAIN), SnippetUi.fg());
                var warn = SnippetUi.dangerBox(text, 0.45f);
                warn.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                box.add(warn, gc);
                gc.gridy++;
            }
            var vals = valuesOf(s);
            var resolved = SnippetUi.promptPane(0);
            boolean danger = s.isDangerous();
            var paste = new SnippetUi.Button(I18n.t(s.enter()
                    ? (danger ? "snippet.action.runAnyway" : "snippet.quick.run")
                    : (danger ? "snippet.action.pasteAnyway" : "snippet.quick.paste")), null,
                    danger ? SnippetUi.Variant.DANGER_FILL : SnippetUi.Variant.PRIMARY).height(28);
            var copy = new SnippetUi.Button(I18n.t("snippet.action.copy"), null, SnippetUi.Variant.CHIP).height(28);
            Runnable refresh = () -> {
                SnippetUi.setPrompt(resolved, SnippetVariables.resolve(s.command(), vals), false);
                boolean ready = SnippetVariables.missing(s.command(), vals).isEmpty();
                paste.setEnabled(ready && target.canPaste());
                copy.setEnabled(ready);
            };
            gc.gridwidth = 1;
            for (String name : s.variableNames()) {
                gc.gridx = 0;
                gc.weightx = 0;
                gc.insets = new Insets(0, 0, 8, 8);
                var label = SnippetUi.label(name, SnippetUi.mono(0), SnippetUi.variable());
                label.setPreferredSize(new Dimension(80, label.getPreferredSize().height));
                box.add(label, gc);
                gc.gridx = 1;
                gc.weightx = 1;
                gc.insets = new Insets(0, 0, 8, 0);
                var field = variableField(vals.getOrDefault(name, ""), 26, 0);
                field.putClientProperty("JTextField.placeholderText", I18n.t("snippet.quick.value"));
                label.setLabelFor(field);
                field.getDocument().addDocumentListener(onChange(() -> {
                    vals.put(name, field.getText());
                    markEmpty(field);
                    refresh.run();
                }));
                field.addActionListener(e -> paste.doClick());
                box.add(field, gc);
                gc.gridy++;
            }
            gc.gridx = 0;
            gc.gridwidth = 2;
            gc.weightx = 1;
            gc.insets = new Insets(0, 0, 8, 0);
            var code = SnippetUi.codeBox(resolved);
            code.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            box.add(code, gc);
            gc.gridy++;
            paste.addActionListener(e -> done(sender.paste(popup.getInvoker(), s, Map.copyOf(vals), target)));
            copy.addActionListener(e -> done(sender.copy(popup.getInvoker(), s, Map.copyOf(vals), target)));
            var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            buttons.setOpaque(false);
            buttons.add(paste);
            buttons.add(Box.createHorizontalStrut(6));
            buttons.add(copy);
            gc.insets = new Insets(0, 0, 0, 0);
            box.add(buttons, gc);
            refresh.run();
            return box;
        }
    }

    /** Input variabel: mono, garis tepi kuning selama nilainya kosong / tidak valid (prototipe). */
    static JTextField variableField(String value, int height, float fontDelta) {
        var field = SnippetUi.styleField(new JTextField(value), height);
        field.setFont(SnippetUi.mono(fontDelta));
        markEmpty(field);
        return field;
    }

    static void markEmpty(JTextField field) {
        boolean ok = SnippetVariables.isValidValue(field.getText());
        field.putClientProperty("FlatLaf.style", "background: " + SnippetUi.hex(SnippetUi.field())
                + "; borderColor: " + SnippetUi.hex(ok ? SnippetUi.border() : SnippetUi.variable())
                + "; focusedBorderColor: " + SnippetUi.hex(ok ? SnippetUi.accent() : SnippetUi.variable())
                + "; arc: 8");
    }

    /** Setiap baris diawali "$ " seperti di prompt. */
    static String prefixed(String command) {
        var out = new ArrayList<String>();
        for (String line : command.split("\n", -1)) {
            out.add("$ " + line);
        }
        return String.join("\n", out);
    }

    static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    static AbstractAction action(Runnable run) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                run.run();
            }
        };
    }
}
