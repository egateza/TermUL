package dev.egateza.termul.app.snippet;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.snippet.Snippet;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.Scrollable;
import javax.swing.UIManager;

/**
 * Tampilan snippet mengikuti prototipe "TermUL Desktop" (artboard Snippet manager dan Menu Snippet). Warna prototipe
 * (palet Tokyo Night) dipetakan ke peran yang sama di tema aktif: latar, bar, panel, chip, border, bingkai, seleksi,
 * aksen. Warna semantik (merah berbahaya, kuning variabel, hijau sukses) tetap, versi lebih gelap di tema terang. EDT.
 */
final class SnippetUi {

    private SnippetUi() {
    }

    // --- palet ---

    static Color panel() {
        return ui("Panel.background", new Color(0x1E1F2B));
    }

    static boolean dark() {
        Color p = panel();
        return (0.299 * p.getRed() + 0.587 * p.getGreen() + 0.114 * p.getBlue()) < 128;
    }

    /** Header, footer, dan navigasi kiri (prototipe {@code --bar}). */
    static Color bar() {
        return mix(panel(), Color.BLACK, dark() ? 0.25f : 0.04f);
    }

    /** Latar input dan kotak kode (prototipe {@code --bg}). */
    static Color field() {
        return dark() ? mix(panel(), Color.BLACK, 0.12f) : ui("TextField.background", Color.WHITE);
    }

    /** Tombol biasa dan chip (prototipe {@code --panel2} / {@code --chip}). */
    static Color chip() {
        return mix(panel(), accent(), dark() ? 0.08f : 0.06f);
    }

    static Color hover() {
        return mix(panel(), fg(), dark() ? 0.05f : 0.06f);
    }

    static Color border() {
        return ui("Component.borderColor", new Color(0x2F3549));
    }

    /** Bingkai dialog dan menu (prototipe {@code --frame}). */
    static Color frame() {
        return mix(panel(), accent(), 0.32f);
    }

    static Color selection() {
        return mix(panel(), accent(), dark() ? 0.2f : 0.16f);
    }

    static Color fg() {
        return ui("Label.foreground", new Color(0xC0CAF5));
    }

    /** Teks item navigasi terpilih (prototipe: putih di atas {@code --sel}). */
    static Color onSelection() {
        return dark() ? Color.WHITE : fg();
    }

    static Color muted() {
        return mix(fg(), panel(), 0.32f);
    }

    static Color accent() {
        return ui("Component.accentColor", ui("Component.focusColor", new Color(0x7AA2F7)));
    }

    /** Teks di atas tombol beraksen / merah. */
    static Color onAccent(Color background) {
        return luminance(background) > 140 ? new Color(0x16161E) : Color.WHITE;
    }

    static Color danger() {
        return dark() ? new Color(0xF7768E) : new Color(0xC8344F);
    }

    static Color variable() {
        return dark() ? new Color(0xE0AF68) : new Color(0xA86B12);
    }

    static Color favorite() {
        return dark() ? new Color(0xE0AF68) : new Color(0xD08C1A);
    }

    static Color prompt() {
        return dark() ? new Color(0x9ECE6A) : new Color(0x3F7F1A);
    }

    static Color success() {
        return dark() ? new Color(0x3DD68C) : new Color(0x168A52);
    }

    static Color starOff() {
        return mix(muted(), panel(), 0.3f);
    }

    static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * a));
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static double luminance(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }

    private static Color ui(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // --- font ---

    static Font base() {
        Font f = UIManager.getFont("Label.font");
        return f != null ? f : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    private static volatile Supplier<Font> codeFont = () -> new Font(Font.MONOSPACED, Font.PLAIN, 14);

    /** Font perintah = Font terminal dari Pengaturan font, termasuk ukurannya (pilihan otomatis juga). */
    static void useCodeFont(Supplier<Font> font) {
        codeFont = font;
    }

    /**
     * Font UI = Font aplikasi dari Pengaturan font ({@code Label.font}, diturunkan FlatLaf dari {@code defaultFont}),
     * seperti dialog TermUL lain: teks biasa ukuran default; Bold hanya untuk judul, badge sedikit lebih kecil.
     */
    static Font font(float delta, int style) {
        return base().deriveFont(style, Math.round(base().getSize2D() + delta));
    }

    /** Font perintah persis seperti di terminal: family dan ukuran Font terminal. */
    static Font mono(float delta) {
        Font f = codeFont.get();
        return f.deriveFont(Font.PLAIN, Math.round(f.getSize2D() + delta));
    }

    static JLabel label(String text, Font font, Color color) {
        var l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    static JLabel mutedLabel(String text) {
        return label(text, font(0, Font.PLAIN), muted());
    }

    /** Judul bagian (Perintah, Variabel, ...): teks biasa abu-abu, seperti label di dialog TermUL lain. */
    static JLabel section(String text) {
        return label(text, font(0, Font.PLAIN), muted());
    }

    /**
     * Paragraf yang dibungkus mengikuti lebar wadahnya (bukan lebar HTML tetap), dengan potongan teks berwarna /
     * tebal / mono. Lebar yang diminta sengaja kecil, supaya layout memberi lebar wadah lalu tinggi mengikuti.
     */
    static final class Paragraph extends javax.swing.JTextPane {
        Paragraph(Font font, Color color) {
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(null);
            setFont(font);
            setForeground(color);
        }

        Paragraph add(String text, Color color, boolean bold, boolean mono) {
            var a = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setForeground(a, color);
            javax.swing.text.StyleConstants.setBold(a, bold);
            javax.swing.text.StyleConstants.setFontFamily(a, mono ? mono(0).getFamily() : getFont().getFamily());
            javax.swing.text.StyleConstants.setFontSize(a, getFont().getSize());
            var doc = getStyledDocument();
            try {
                doc.insertString(doc.getLength(), text, a);
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
            return this;
        }

        /** Teks dengan warna dan ketebalan font paragraf ini. */
        Paragraph add(String text) {
            return add(text, getForeground(), getFont().isBold(), false);
        }

        @Override
        public Dimension getPreferredSize() {
            var d = super.getPreferredSize();
            return new Dimension(Math.min(d.width, 40), d.height);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    }

    static Paragraph text(String text, Font font, Color color) {
        return new Paragraph(font, color).add(text);
    }

    /** Teks HTML yang dibungkus sampai lebar {@code width}. */
    static JLabel wrapped(String html, int width, Font font, Color color) {
        return label("<html><div style='width:" + width + "px'>" + html + "</div></html>", font, color);
    }

    static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // --- ikon (path SVG dari prototipe, viewBox 24×24) ---

    static final String BOLT = "M13 2L4 14h7l-1 8 9-12h-7z";
    static final String STAR = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z";
    static final String CLOSE = "M6 6l12 12M18 6L6 18";
    static final String PLUS = "M12 5v14M5 12h14";
    static final String TRASH = "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3";
    static final String WARN = "M12 3l10 18H2z M12 10v5M12 18h.01";
    static final String CHECK = "M5 12l5 5 9-10";
    static final String CHEVRON = "M6 9l6 6 6-6";
    static final String CLIPBOARD = "M8 5H6v15h12V5h-2 M8 3h8v4H8z";
    static final String FOLDER16 = "M1 3h5l1 1h8v9H1z";

    static Icon stroke(String path, int size, Supplier<Color> color, float width) {
        return new PathIcon(List.of(parse(path)), size, 24, color, false, width);
    }

    static Icon fill(String path, int size, Supplier<Color> color) {
        return new PathIcon(List.of(parse(path)), size, 24, color, true, 0);
    }

    static Icon star(boolean on, int size) {
        return new PathIcon(List.of(parse(STAR)), size, 24, on ? SnippetUi::favorite : SnippetUi::starOff, on, 1.8f);
    }

    static Icon folder(int size, Supplier<Color> color) {
        return new PathIcon(List.of(parse(FOLDER16)), size, 16, color, true, 0);
    }

    static Icon search(int size) {
        var shapes = new ArrayList<Shape>();
        shapes.add(new Ellipse2D.Float(4, 4, 14, 14));
        shapes.add(parse("M20 20l-3.5-3.5"));
        return new PathIcon(shapes, size, 24, SnippetUi::muted, false, 2);
    }

    static Icon sliders(int size) {
        var shapes = new ArrayList<Shape>();
        shapes.add(parse("M4 7h10M18 7h2M4 17h2M10 17h10"));
        shapes.add(new Ellipse2D.Float(14, 5, 4, 4));
        shapes.add(new Ellipse2D.Float(6, 15, 4, 4));
        return new PathIcon(shapes, size, 24, SnippetUi::fg, false, 2);
    }

    /** Titik status bulat. */
    static Icon dot(int size, Supplier<Color> color) {
        return new PathIcon(List.of(new Ellipse2D.Float(0, 0, 24, 24)), size, 24, color, true, 0);
    }

    private static final Pattern TOKEN = Pattern.compile("[MmLlHhVvZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)");

    /** Path SVG sederhana: M, L, H, V, Z (huruf kecil = relatif), angka berulang mengulang perintah terakhir. */
    static Path2D parse(String d) {
        var path = new Path2D.Float();
        Matcher m = TOKEN.matcher(d);
        var tokens = new ArrayList<String>();
        while (m.find()) {
            tokens.add(m.group());
        }
        char cmd = 'M';
        float x = 0;
        float y = 0;
        float startX = 0;
        float startY = 0;
        for (int i = 0; i < tokens.size(); ) {
            String t = tokens.get(i);
            if (Character.isLetter(t.charAt(0))) {
                cmd = t.charAt(0);
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    path.closePath();
                    x = startX;
                    y = startY;
                }
                continue;
            }
            switch (cmd) {
                case 'M', 'm' -> {
                    float nx = Float.parseFloat(tokens.get(i++));
                    float ny = Float.parseFloat(tokens.get(i++));
                    x = cmd == 'm' ? x + nx : nx;
                    y = cmd == 'm' ? y + ny : ny;
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    cmd = cmd == 'm' ? 'l' : 'L';
                }
                case 'L', 'l' -> {
                    float nx = Float.parseFloat(tokens.get(i++));
                    float ny = Float.parseFloat(tokens.get(i++));
                    x = cmd == 'l' ? x + nx : nx;
                    y = cmd == 'l' ? y + ny : ny;
                    path.lineTo(x, y);
                }
                case 'H', 'h' -> {
                    float n = Float.parseFloat(tokens.get(i++));
                    x = cmd == 'h' ? x + n : n;
                    path.lineTo(x, y);
                }
                case 'V', 'v' -> {
                    float n = Float.parseFloat(tokens.get(i++));
                    y = cmd == 'v' ? y + n : n;
                    path.lineTo(x, y);
                }
                default -> i++;
            }
        }
        return path;
    }

    /** Ikon vektor dari bentuk di viewBox {@code view}×{@code view}; warna dibaca saat digambar. */
    static final class PathIcon implements Icon {
        private final List<Shape> shapes;
        private final int size;
        private final float view;
        private final Supplier<Color> color;
        private final boolean filled;
        private final float strokeWidth;

        PathIcon(List<Shape> shapes, int size, float view, Supplier<Color> color, boolean filled, float strokeWidth) {
            this.shapes = shapes;
            this.size = size;
            this.view = view;
            this.color = color;
            this.filled = filled;
            this.strokeWidth = strokeWidth;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            float scale = size / view;
            g2.scale(scale, scale);
            g2.setColor(c != null && !c.isEnabled() ? alpha(color.get(), 0.45f) : color.get());
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Shape s : shapes) {
                if (filled) {
                    g2.fill(s);
                }
                if (strokeWidth > 0) {
                    g2.draw(s);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    // --- komponen ---

    /** Panel bersudut membulat dengan latar dan garis tepi (kotak kode, peringatan, chip, kartu). */
    static class RoundPanel extends JPanel {
        private final Supplier<Color> background;
        private final Supplier<Color> line;
        private final int arc;

        RoundPanel(LayoutManager layout, Supplier<Color> background, Supplier<Color> line, int arc) {
            super(layout);
            this.background = background;
            this.line = line;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, arc, arc);
            if (background != null) {
                g2.setColor(background.get());
                g2.fill(shape);
            }
            if (line != null) {
                g2.setColor(line.get());
                g2.draw(shape);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Kotak kode: latar input, garis tepi, sudut 4 px, isi berjarak 8/10 px. */
    static RoundPanel codeBox(JComponent content) {
        var box = new RoundPanel(new BorderLayout(), SnippetUi::field, SnippetUi::border, 8);
        box.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        content.setOpaque(false);
        box.add(content, BorderLayout.CENTER);
        return box;
    }

    /** Kotak kode juga {@link Paragraph}: baris panjang dibungkus, bukan melebarkan panel. */
    private static javax.swing.JTextPane pane(float fontDelta) {
        return new Paragraph(mono(fontDelta), fg());
    }

    private static void append(javax.swing.text.StyledDocument doc, String text, Color color, Color background) {
        var a = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setForeground(a, color);
        if (background != null) {
            javax.swing.text.StyleConstants.setBackground(a, background);
        }
        try {
            doc.insertString(doc.getLength(), text, a);
        } catch (javax.swing.text.BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Perintah dengan {@code ${variabel}} kuning berlatar transparan (prototipe: kotak "Perintah"). */
    static javax.swing.JTextPane commandPane(String command, float fontDelta) {
        var pane = pane(fontDelta);
        var doc = pane.getStyledDocument();
        var m = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*}").matcher(command);
        int last = 0;
        while (m.find()) {
            append(doc, command.substring(last, m.start()), fg(), null);
            append(doc, m.group(), variable(), mix(field(), variable(), 0.14f));
            last = m.end();
        }
        append(doc, command.substring(last), fg(), null);
        return pane;
    }

    /** Kotak "Akan ditempel": setiap baris diawali {@code $ } hijau, ditambah baris "↵ Enter" kalau langsung jalan. */
    static javax.swing.JTextPane promptPane(float fontDelta) {
        return pane(fontDelta);
    }

    static void setPrompt(javax.swing.JTextPane pane, String resolved, boolean enter) {
        var doc = pane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (javax.swing.text.BadLocationException e) {
            throw new IllegalStateException(e);
        }
        var lines = resolved.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            append(doc, "$ ", prompt(), null);
            append(doc, lines[i] + (i < lines.length - 1 || enter ? "\n" : ""), fg(), null);
        }
        if (enter) {
            // tanda ↵ tidak ada di sebagian font mono (mis. JetBrains Mono): pakai Font aplikasi seperti badge "↵ jalan"
            var a = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setForeground(a, accent());
            javax.swing.text.StyleConstants.setFontFamily(a, base().getFamily());
            try {
                doc.insertString(doc.getLength(), "↵ Enter", a);
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Kotak peringatan merah transparan (snippet berbahaya). */
    static RoundPanel dangerBox(JComponent content, float borderAlpha) {
        var box = new RoundPanel(new BorderLayout(10, 0), () -> alpha(danger(), 0.10f),
                () -> alpha(danger(), borderAlpha), 8);
        box.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        box.add(content, BorderLayout.CENTER);
        return box;
    }

    enum Variant { DEFAULT, PRIMARY, DANGER, DANGER_FILL, CHIP, ICON }

    /** Tombol bergaya prototipe ({@code .btn}, {@code .btn.primary}, {@code .btn.danger}, {@code .icon-btn}). */
    static final class Button extends JButton {
        private final Variant variant;
        private int height = 30;

        Button(String text, Icon icon, Variant variant) {
            super(text, icon);
            this.variant = variant;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setOpaque(false);
            setIconTextGap(6);
            setFont(font(0, Font.PLAIN));
            int side = variant == Variant.ICON ? 0 : 14;
            setBorder(BorderFactory.createEmptyBorder(0, side, 0, side));
            if (variant == Variant.ICON) {
                height = 28;
            }
        }

        Button height(int h) {
            height = h;
            return this;
        }

        @Override
        public Color getForeground() {
            if (variant == null) {
                return super.getForeground();
            }
            return switch (variant) {
                case PRIMARY -> onAccent(accent());
                case DANGER_FILL -> onAccent(danger());
                case DANGER -> danger();
                case ICON -> getModel().isRollover() ? fg() : muted();
                default -> fg();
            };
        }

        @Override
        public Dimension getPreferredSize() {
            var d = super.getPreferredSize();
            return variant == Variant.ICON ? new Dimension(height, height) : new Dimension(d.width, height);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover() && isEnabled();
            var shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 8, 8);
            Color fill = switch (variant) {
                case PRIMARY -> hover ? mix(accent(), Color.WHITE, 0.25f) : accent();
                case DANGER_FILL -> hover ? mix(danger(), Color.WHITE, 0.15f) : danger();
                case ICON -> hover ? hover() : null;
                default -> chip();
            };
            Color line = switch (variant) {
                case PRIMARY -> accent();
                case DANGER_FILL -> danger();
                case ICON -> null;
                case DANGER -> hover ? danger() : border();
                default -> hover ? accent() : border();
            };
            if (!isEnabled()) {
                g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.45f));
            }
            if (fill != null) {
                g2.setColor(fill);
                g2.fill(shape);
            }
            if (line != null) {
                g2.setColor(line);
                g2.draw(shape);
            }
            if (isFocusOwner()) {
                g2.setColor(accent());
                g2.setStroke(new BasicStroke(2));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2f, getHeight() - 2f, 7, 7));
            }
            paintLabel(g2);
            g2.dispose();
        }

        /** Ikon + teks di tengah; tombol nonaktif tetap berwarna aktif tapi dipudarkan (seperti prototipe). */
        private void paintLabel(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Icon icon = getIcon();
            String text = getText() == null ? "" : getText();
            FontMetrics fm = g2.getFontMetrics(getFont());
            int iw = icon == null ? 0 : icon.getIconWidth();
            int gap = icon != null && !text.isEmpty() ? getIconTextGap() : 0;
            int w = iw + gap + fm.stringWidth(text);
            int x = (getWidth() - w) / 2;
            if (icon != null) {
                icon.paintIcon(null, g2, x, (getHeight() - icon.getIconHeight()) / 2);
            }
            if (!text.isEmpty()) {
                g2.setFont(getFont());
                g2.setColor(getForeground());
                g2.drawString(text, x + iw + gap, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            }
        }
    }

    /**
     * Field bergaya prototipe: latar input, garis tepi, sudut 4 px, tinggi {@code height}. Lebar yang diminta kecil
     * (field mengisi lebar kolomnya), supaya isi yang panjang tidak membuat panel lebih lebar dari dialog.
     */
    static <T extends JComponent> T styleField(T field, int height) {
        field.putClientProperty("FlatLaf.style", "background: " + hex(field()) + "; borderColor: " + hex(border())
                + "; focusedBorderColor: " + hex(accent()) + "; arc: 8");
        field.putClientProperty("JComponent.minimumHeight", height);
        field.setPreferredSize(new Dimension(40, height));
        return field;
    }

    /** Saklar + teks yang dibungkus; klik teks juga mengubah saklar. */
    record Toggle(JComponent row, JCheckBox box) {
    }

    /** Saklar geser (prototipe {@code .sw}): label tebal + keterangan abu-abu, warna saat aktif per saklar. */
    static Toggle toggle(String label, String hint, boolean on, Supplier<Color> color) {
        var box = new JCheckBox((String) null, on);
        box.setIcon(new SwitchIcon(false, color));
        box.setSelectedIcon(new SwitchIcon(true, color));
        box.setRolloverIcon(new SwitchIcon(false, color));
        box.setRolloverSelectedIcon(new SwitchIcon(true, color));
        box.setOpaque(false);
        box.setFocusPainted(true);
        box.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        box.getAccessibleContext().setAccessibleName(label);
        var text = new Paragraph(font(0, Font.PLAIN), fg()).add(label, fg(), false, false).add(" " + hint, muted(),
                false, false);
        text.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                box.doClick();
            }
        });
        var row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        var west = new JPanel(new BorderLayout());
        west.setOpaque(false);
        west.add(box, BorderLayout.NORTH);
        row.add(west, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return new Toggle(row, box);
    }

    private record SwitchIcon(boolean on, Supplier<Color> color) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(on ? color.get() : border());
            g2.fillRoundRect(x, y, 32, 18, 18, 18);
            g2.setColor(on ? onAccent(color.get()) : muted());
            g2.fillOval(on ? x + 16 : x + 2, y + 2, 14, 14);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 32;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }

    /** Badge "berbahaya", "2 var", "↵ jalan" untuk {@code s}. */
    static void addBadges(JPanel row, Snippet s) {
        if (s.isDangerous()) {
            row.add(new Badge(I18n.t("snippet.badge.dangerous"), SnippetUi::danger, 0.16f, false));
        }
        int vars = s.variableNames().size();
        if (vars > 0) {
            row.add(new Badge(I18n.t("snippet.badge.vars", vars), SnippetUi::variable, 0.14f, true));
        }
        if (s.enter()) {
            var runs = new Badge(I18n.t("snippet.badge.runs"), SnippetUi::accent, 0.16f, false);
            runs.setToolTipText(I18n.t("snippet.badge.runs.tip"));
            row.add(runs);
        }
    }

    /** Label pil kecil (tinggi 16, sudut 8) dengan latar warna transparan. */
    static final class Badge extends JLabel {
        private final Supplier<Color> color;
        private final float fillAlpha;

        Badge(String text, Supplier<Color> color, float fillAlpha, boolean mono) {
            super(text);
            this.color = color;
            this.fillAlpha = fillAlpha;
            setOpaque(false);
            setFont(font(-1, Font.PLAIN));
            setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        }

        @Override
        public Color getForeground() {
            return color == null ? super.getForeground() : color.get();
        }

        @Override
        public Dimension getPreferredSize() {
            var d = super.getPreferredSize();
            return new Dimension(d.width, 16);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(alpha(getForeground(), fillAlpha));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Chip pil dengan garis tepi (target, folder host). */
    static final class Chip extends JPanel {
        Chip(int height, int padding, JComponent... parts) {
            super(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder((height - 16) / 2, padding, (height - 16) / 2, padding));
            for (var p : parts) {
                add(p);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, getHeight(),
                    getHeight());
            g2.setColor(chip());
            g2.fill(shape);
            g2.setColor(border());
            g2.draw(shape);
            g2.dispose();
        }
    }

    /** Panel di dalam scroll pane yang lebarnya selalu mengikuti viewport (teks dibungkus, tanpa scroll horizontal). */
    static final class TrackingPanel extends JPanel implements Scrollable {
        TrackingPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height < getParent().getHeight();
        }
    }

    /**
     * Latar gelap transparan di atas window {@code owner} selama dialog snippet terbuka (seperti prototipe). Dipasang
     * di lapisan modal {@link JLayeredPane}, bukan glass pane (dipakai layar idle).
     *
     * @return pelepas latar
     */
    static Runnable dim(Window owner) {
        if (!(owner instanceof RootPaneContainer rpc)) {
            return () -> { };
        }
        JRootPane root = rpc.getRootPane();
        var layered = root.getLayeredPane();
        var shade = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(9, 10, 16, 158));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        shade.setBounds(0, 0, layered.getWidth(), layered.getHeight());
        layered.add(shade, JLayeredPane.MODAL_LAYER);
        layered.repaint();
        return () -> {
            layered.remove(shade);
            layered.repaint();
        };
    }

    /** Baris pertama perintah, ditambah "(+N baris)" kalau lebih dari satu baris. */
    static String preview(Snippet s) {
        var lines = s.lines();
        return lines.size() == 1 ? lines.getFirst() : I18n.t("snippet.preview.more", lines.getFirst(), lines.size() - 1);
    }

    /** Lebar teks, untuk memotong dengan elipsis. */
    static String ellipsize(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String dots = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width) {
            end--;
        }
        return text.substring(0, end) + dots;
    }

}
