package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import com.formdev.flatlaf.util.UIScale;
import dev.egateza.termul.core.config.AppConfig;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

/**
 * Tab bar terminal dengan gaya dari prototipe "Gaya tab" ({@link AppConfig#TAB_STYLES}). Card dan Underlined memakai
 * FlatLaf apa adanya; gaya lain digambar di sini dengan warna tema aktif: aksen = {@code TabbedPane.underlineColor},
 * latar tab aktif = {@code TabbedPane.selectedBackground}, hover = {@code TabbedPane.hoverColor}. Gaya dibaca dari
 * client property {@link #STYLE}. Hanya untuk tab di atas; penempatan lain jatuh ke FlatLaf. EDT.
 */
public final class TermTabbedPaneUI extends FlatTabbedPaneUI {

    /** Client property (String, salah satu {@link AppConfig#TAB_STYLES}). */
    public static final String STYLE = "TermUL.tabStyle";

    /** Lebar ujung panah / lekukan Powerline dan tumpang tindih antartab (px, sebelum skala). */
    static final int ARROW = 10;
    static final int OVERLAP = 7;
    /** Jari-jari lengkung kaki tab aktif gaya Browser. */
    static final int FOOT = 8;

    @SuppressWarnings("unused") // dipanggil Swing lewat refleksi
    public static ComponentUI createUI(JComponent c) {
        return new TermTabbedPaneUI();
    }

    private String style() {
        return tabPane.getClientProperty(STYLE) instanceof String s ? s : AppConfig.TAB_CARD;
    }

    /** Gaya yang digambar sendiri (bukan Card/Underlined bawaan FlatLaf), hanya untuk tab di atas. */
    private boolean custom() {
        String s = style();
        return tabPane.getTabPlacement() == TOP && !AppConfig.TAB_CARD.equals(s) && !AppConfig.TAB_UNDERLINED.equals(s);
    }

    private boolean rollover(int tabIndex) {
        return getRolloverTab() == tabIndex && tabPane.isEnabledAt(tabIndex);
    }

    private Color accent() {
        return underlineColor != null ? underlineColor : tabPane.getForeground();
    }

    private Color hover() {
        return hoverColor != null ? hoverColor : mix(tabPane.getForeground(), tabPane.getBackground(), 0.08f);
    }

    /**
     * Latar tab aktif (Top line, Browser): {@code TabbedPane.selectedBackground} tema; tema yang tidak mengisinya
     * (mis. FlatLaf Dark bawaan) dapat warna sedikit lebih terang dari tab bar, supaya tab aktif tetap terlihat.
     */
    private Color activeBackground() {
        return selectedBackground != null ? selectedBackground
                : mix(tabPane.getForeground(), tabPane.getBackground(), 0.12f);
    }

    private Color separator() {
        return tabSeparatorColor != null ? tabSeparatorColor : contentAreaColor;
    }

    @Override
    protected Insets getTabInsets(int tabPlacement, int tabIndex) {
        Insets insets = super.getTabInsets(tabPlacement, tabIndex);
        if (custom() && AppConfig.TAB_POWERLINE.equals(style())) {
            // teks tidak masuk ke lekukan kiri / ujung panah kanan
            int left = tabIndex > 0 ? UIScale.scale(ARROW) : 0;
            return new Insets(insets.top, insets.left + left, insets.bottom, insets.right + UIScale.scale(ARROW - OVERLAP));
        }
        return insets;
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                      boolean isSelected) {
        if (!custom()) {
            super.paintTabBackground(g, tabPlacement, tabIndex, x, y, w, h, isSelected);
            return;
        }
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = rollover(tabIndex);
            switch (style()) {
                case AppConfig.TAB_TOPLINE -> fill(g2, isSelected ? activeBackground()
                        : hover ? hover() : null, new java.awt.Rectangle(x, y, w, h));
                case AppConfig.TAB_PILL -> fill(g2, isSelected ? pillColor() : hover ? hover() : null,
                        pillShape(x, y, w, h));
                case AppConfig.TAB_INDICATOR -> fill(g2, hover && !isSelected ? hover() : null,
                        new java.awt.Rectangle(x, y, w, h));
                case AppConfig.TAB_SOLID -> fill(g2, isSelected ? accent() : hover ? hover() : null,
                        new java.awt.Rectangle(x, y, w, h));
                case AppConfig.TAB_BROWSER -> {
                    if (isSelected) {
                        fill(g2, activeBackground(), browserShape(x, y, w, h));
                    } else if (hover) {
                        fill(g2, hover(), browserIdleShape(x, y, w, h));
                    }
                }
                case AppConfig.TAB_POWERLINE -> fill(g2, isSelected ? accent() : hover ? hover() : chipColor(),
                        powerlineShape(x, y, w, h, tabIndex == 0, tabIndex == tabPane.getTabCount() - 1));
                default -> super.paintTabBackground(g, tabPlacement, tabIndex, x, y, w, h, isSelected);
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                  boolean isSelected) {
        if (!custom()) {
            super.paintTabBorder(g, tabPlacement, tabIndex, x, y, w, h, isSelected);
            return;
        }
        if (AppConfig.TAB_TOPLINE.equals(style())) { // sekat penuh di kanan setiap tab
            g.setColor(separator());
            g.fillRect(x + w - UIScale.scale(1), y, UIScale.scale(1), h);
        }
    }

    @Override
    protected void paintTabSelection(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h) {
        if (!custom()) {
            super.paintTabSelection(g, tabPlacement, tabIndex, x, y, w, h);
            return;
        }
        var g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(accent());
            switch (style()) {
                case AppConfig.TAB_TOPLINE -> g2.fillRect(x, y, w, UIScale.scale(2));
                case AppConfig.TAB_INDICATOR -> {
                    int inset = UIScale.scale(14);
                    int height = UIScale.scale(3);
                    g2.fill(new RoundRectangle2D.Float(x + inset, y + h - UIScale.scale(3) - height,
                            Math.max(height, w - 2 * inset), height, height, height));
                }
                default -> { } // Pill, Solid, Browser, Powerline: tab aktif ditandai latarnya
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected Color getTabForeground(int tabPlacement, int tabIndex, boolean isSelected) {
        String s = style();
        if (isSelected && custom() && (AppConfig.TAB_SOLID.equals(s) || AppConfig.TAB_POWERLINE.equals(s))) {
            return onColor(accent());
        }
        if (isSelected && tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)
                && tabPane.getForegroundAt(tabIndex) == tabPane.getForeground()) {
            return selectedForeground != null ? selectedForeground : tabPane.getForeground(); // tanpa warna hover
        }
        return super.getTabForeground(tabPlacement, tabIndex, isSelected);
    }

    /**
     * Tab aktif selalu memakai warna aktifnya. FlatLaf mendahulukan warna hover (dan warna fokus) di atas warna tab
     * aktif, sehingga tab yang baru diklik tetap tampak seperti hover sampai mouse digeser keluar: terasa seperti
     * highlight yang terlambat, padahal terminal sudah berganti.
     */
    @Override
    protected Color getTabBackground(int tabPlacement, int tabIndex, boolean isSelected) {
        Color background = tabPane.getBackgroundAt(tabIndex);
        if (isSelected && tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex) && background == tabPane.getBackground()) {
            return selectedBackground != null ? selectedBackground : background; // sama dengan latar = tidak diisi
        }
        return super.getTabBackground(tabPlacement, tabIndex, isSelected);
    }

    /** Bentuk Browser dan Powerline keluar dari kotak tab-nya: hover menggambar ulang seluruh tab bar. */
    @Override
    protected void setRolloverTab(int index) {
        int old = getRolloverTab();
        super.setRolloverTab(index);
        String s = style();
        if (index != old && custom() && (AppConfig.TAB_BROWSER.equals(s) || AppConfig.TAB_POWERLINE.equals(s))) {
            tabPane.repaint();
        }
    }

    // --- bentuk (murni geometri, bisa dites) ---

    /** Kapsul setinggi maks. 25 px di tengah tab. */
    static Shape pillShape(int x, int y, int w, int h) {
        float ph = Math.min(h - UIScale.scale(6f), UIScale.scale(25f));
        float inset = UIScale.scale(2f);
        return new RoundRectangle2D.Float(x + inset, y + (h - ph) / 2f, w - 2 * inset, ph, ph, ph);
    }

    /**
     * Tab aktif gaya Browser: sudut atas membulat, kaki bawah melengkung cekung keluar kotak tab sejauh {@link #FOOT}
     * sehingga menyatu dengan area terminal di bawahnya.
     */
    static Shape browserShape(int x, int y, int w, int h) {
        float r = UIScale.scale((float) FOOT);
        float top = y + UIScale.scale(3f);
        float bottom = y + h;
        var p = new Path2D.Float();
        p.moveTo(x - r, bottom);
        p.quadTo(x, bottom, x, bottom - r);
        p.lineTo(x, top + r);
        p.quadTo(x, top, x + r, top);
        p.lineTo(x + w - r, top);
        p.quadTo(x + w, top, x + w, top + r);
        p.lineTo(x + w, bottom - r);
        p.quadTo(x + w, bottom, x + w + r, bottom);
        p.closePath();
        return p;
    }

    /** Hover tab tidak aktif gaya Browser: kotak membulat lebih pendek, berjarak 4 px dari bawah. */
    static Shape browserIdleShape(int x, int y, int w, int h) {
        float gap = UIScale.scale(4f);
        float th = Math.min(UIScale.scale(25f), h - gap - UIScale.scale(2f));
        float inset = UIScale.scale(2f);
        float arc = UIScale.scale(12f);
        return new RoundRectangle2D.Float(x + inset, y + h - gap - th, w - 2 * inset, th, arc, arc);
    }

    /**
     * Chevron Powerline: lekukan di kiri (kecuali tab pertama) dan ujung panah di kanan yang masuk ke lekukan tab
     * berikutnya sejauh {@link #OVERLAP} (tab terakhir: tidak keluar dari kotaknya).
     */
    static Shape powerlineShape(int x, int y, int w, int h, boolean first, boolean last) {
        float a = UIScale.scale((float) ARROW);
        float right = x + w + (last ? 0 : UIScale.scale((float) OVERLAP));
        var p = new Path2D.Float();
        p.moveTo(x, y);
        p.lineTo(right - a, y);
        p.lineTo(right, y + h / 2f);
        p.lineTo(right - a, y + h);
        p.lineTo(x, y + h);
        if (!first) {
            p.lineTo(x + a, y + h / 2f);
        }
        p.closePath();
        return p;
    }

    // --- warna ---

    private Color pillColor() {
        return mix(accent(), tabPane.getBackground(), 0.25f);
    }

    private Color chipColor() {
        return mix(tabPane.getForeground(), tabPane.getBackground(), 0.1f);
    }

    /** Teks di atas warna {@code bg}: gelap di atas warna terang, putih di atas warna gelap. */
    static Color onColor(Color bg) {
        double lum = (0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue()) / 255;
        return lum > 0.5 ? new Color(0x16161E) : Color.WHITE;
    }

    /** {@code a × t + b × (1 − t)}, pekat. */
    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() * t + b.getRed() * (1 - t)),
                Math.round(a.getGreen() * t + b.getGreen() * (1 - t)),
                Math.round(a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    private static void fill(Graphics2D g2, Color color, Shape shape) {
        if (color != null) {
            g2.setColor(color);
            g2.fill(shape);
        }
    }
}
