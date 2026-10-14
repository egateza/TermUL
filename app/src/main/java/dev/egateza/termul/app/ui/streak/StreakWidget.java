package dev.egateza.termul.app.ui.streak;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.Os;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Widget streak mengambang: window kecil tanpa bingkai yang selalu di depan, berisi api streak beranimasi dan jumlah
 * hari, plus status internet. Selama tidak di-hover hanya api yang terlihat; kartu, teks, dan status internet muncul
 * (fade) saat pointer di atas widget. Klik status internet = periksa sekarang.
 * Tetap tampil walau window utama disembunyikan ke tray. Seret = pindah, klik = sama dengan klik ikon pojok
 * (triple klik / pecahkan es), dobel klik = buka TermUL, klik kanan = menu. Tidak muncul di taskbar. EDT.
 */
final class StreakWidget {

    /** Aksi ke luar widget; dipanggil di EDT. */
    interface Host {
        void flameClicked();

        void openApp();

        void hideRequested();

        /** Selesai diseret ke posisi baru (kiri atas, koordinat desktop). */
        void moved(Point location);

        /** Klik status internet: periksa sekarang. */
        void checkNetwork();
    }

    static final double FLAME = 40; // >= 40: FlameActor menggambar percikan dan bintang
    private static final int FLAME_BOX = 52;
    private static final int PAD = 10;
    private static final int NET_DOT = 7;
    private static final int NET_GAP = 12; // jarak angka streak ke status internet
    private static final int CARD_HEIGHT = 64; // bukan HEIGHT: tertutup ImageObserver.HEIGHT di dalam Face
    private static final int ARC = 18;
    /** Jarak seret minimum (px) sebelum dianggap memindahkan, bukan klik. */
    static final int DRAG_SLOP = 4;
    /** Lama fade kartu muncul/hilang (detik). */
    static final double FADE_SECONDS = .18;
    /**
     * Alpha kartu saat tersembunyi: 1/255, tidak terlihat mata, tapi Windows meneruskan mouse di piksel yang 100%
     * transparan ke window di belakangnya. Dengan ini seluruh widget tetap bisa di-hover, diklik, dan diseret.
     */
    static final int HIDDEN_ALPHA = 1;
    /** Jarak widget dari tepi layar saat belum pernah digeser. */
    static final int MARGIN = 24;
    /** Warna titik offline (sama dengan indikator panel bawah); teksnya ikut merah. */
    static final Color NET_OFFLINE = new Color(0xE5484D);

    private final Host host;
    private JWindow window; // EDT; dibuat ulang saat pindah ke monitor lain (lihat moveTo)
    private final Face face = new Face();
    private final FlameActor actor = new FlameActor();
    private final Timer timer = new Timer(40, e -> tick());
    private int ticks; // EDT
    private FlameLook look = FlameLook.active(0);
    private JPopupMenu openMenu; // EDT; menu klik kanan yang sedang tampil (kartu tetap terlihat)
    private Color netColor; // EDT; warna titik status internet, null = belum ada data (tidak digambar)
    private String netText = ""; // EDT; mis. "23 ms" / "Offline"

    StreakWidget(Host host) {
        this.host = Objects.requireNonNull(host);
        window = createWindow(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration());
    }

    /**
     * Window untuk satu monitor. Java tidak menyesuaikan ukuran fisik window transparan per piksel yang dipindah ke
     * monitor dengan scaling lain (gambar skala baru masuk ke buffer ukuran lama: bergaris, atau ukurannya dibesarkan
     * berulang). Karena itu window selalu dibuat dengan konfigurasi monitor tempat ia tampil, dan dibuat ulang saat
     * pindah monitor.
     */
    private JWindow createWindow(GraphicsConfiguration gc) {
        var created = new JWindow(gc);
        created.setType(Window.Type.UTILITY); // tidak muncul di taskbar / Alt+Tab
        created.setAlwaysOnTop(true);
        created.setFocusableWindowState(false); // tidak merebut fokus dari terminal saat diklik
        if (gc.isTranslucencyCapable() && translucentSupported(gc.getDevice())) {
            created.setBackground(new Color(0, 0, 0, 0));
        }
        created.setContentPane(face);
        created.setSize(face.getPreferredSize());
        return created;
    }

    /**
     * Pindahkan widget (kiri atas, koordinat desktop). Di Windows window dibuat ulang kalau monitornya berbeda; di
     * macOS Java menangani perpindahan layar dengan benar.
     */
    private void moveTo(Point location) {
        var gc = Os.current().isWindows() ? configurationAt(location) : null;
        if (gc != null && gc.getDevice() != window.getGraphicsConfiguration().getDevice()) {
            boolean visible = window.isVisible();
            var old = window;
            window = createWindow(gc);
            window.setLocation(location);
            if (visible) {
                window.setVisible(true);
            }
            old.dispose();
            return;
        }
        window.setLocation(location);
    }

    /** Konfigurasi monitor yang memuat titik itu (koordinat desktop), atau null kalau di luar semua monitor. */
    private static GraphicsConfiguration configurationAt(Point p) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            var gc = device.getDefaultConfiguration();
            if (gc.getBounds().contains(p)) {
                return gc;
            }
        }
        return null;
    }

    /** Interval menegaskan ulang posisi paling atas (tiap N frame, ±0,5 detik). */
    static final int RAISE_EVERY_TICKS = 12;

    private void tick() {
        // Windows: taskbar/tray yang diklik naik menutupi widget; naikkan lagi secara berkala (tanpa fokus)
        if (Os.current().isWindows() && ++ticks % RAISE_EVERY_TICKS == 0 && window.isVisible()) {
            WinTopmost.raise(window);
        }
        face.repaint();
    }

    /** Ukuran window = ukuran isi (tidak ikut dibesarkan OS saat pindah monitor). */
    private void fitSize() {
        var size = face.getPreferredSize();
        if (!size.equals(window.getSize())) {
            window.setSize(size);
        }
    }

    FlameActor actor() {
        return actor;
    }

    FlameLook look() {
        return look;
    }

    void setLook(FlameLook look, String tooltip) {
        this.look = look;
        face.setToolTipText(tooltip);
        fitSize(); // kiri atas tetap; lebar mengikuti teks
        face.repaint();
    }

    /** Status internet terbaru (dari indikator panel bawah). @param dot null = belum ada data */
    void setNetwork(Color dot, String text) {
        netColor = dot;
        netText = text == null ? "" : text;
        fitSize();
        face.repaint();
    }

    /** @param saved posisi tersimpan, atau null untuk pojok kanan bawah layar utama */
    void show(Point saved) {
        window.setSize(face.getPreferredSize());
        moveTo(place(saved, window.getSize(), screens(), usableArea()));
        window.setVisible(true);
        timer.start();
    }

    void hide() {
        timer.stop();
        window.setVisible(false);
    }

    boolean showing() {
        return window.isVisible();
    }

    void dispose() {
        timer.stop();
        window.dispose();
    }

    /**
     * Posisi widget: posisi tersimpan kalau sudut kiri atasnya masih di salah satu layar (monitor bisa dicabut),
     * selain itu pojok kanan bawah area kerja layar utama.
     */
    static Point place(Point saved, Dimension size, List<Rectangle> screens, Rectangle usable) {
        if (saved != null) {
            var corner = new Rectangle(saved.x, saved.y, Math.min(size.width, 40), Math.min(size.height, 20));
            for (var screen : screens) {
                if (screen.contains(corner)) {
                    return new Point(saved);
                }
            }
        }
        return new Point(usable.x + usable.width - size.width - MARGIN, usable.y + usable.height - size.height - MARGIN);
    }

    /** Geser {@code location} seperlunya supaya window seukuran {@code size} berada utuh di dalam {@code screen}. */
    static Point clampInto(Point location, Dimension size, Rectangle screen) {
        int x = Math.max(screen.x, Math.min(location.x, screen.x + screen.width - size.width));
        int y = Math.max(screen.y, Math.min(location.y, screen.y + screen.height - size.height));
        return new Point(x, y);
    }

    private static List<Rectangle> screens() {
        var list = new ArrayList<Rectangle>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            list.add(device.getDefaultConfiguration().getBounds());
        }
        return list;
    }

    private static Rectangle usableArea() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); // tanpa taskbar
    }

    private static boolean translucentSupported(GraphicsDevice device) {
        return device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
    }

    private void showMenu(java.awt.Component invoker, int x, int y) {
        openMenu = menu();
        openMenu.show(invoker, x, y);
    }

    private JPopupMenu menu() {
        var menu = new JPopupMenu();
        var open = new JMenuItem(I18n.t("streak.widget.open"));
        open.addActionListener(e -> host.openApp());
        var hide = new JMenuItem(I18n.t("streak.widget.hide"));
        hide.addActionListener(e -> host.hideRequested());
        menu.add(open);
        menu.add(hide);
        return menu;
    }

    /** Isi widget: kartu membulat semi transparan, api, angka, dan keterangan. */
    private final class Face extends JComponent {

        private long lastPaint = System.nanoTime();
        private double reveal; // 0 = hanya api, 1 = kartu + teks penuh
        private Rectangle netArea; // area klik status internet (digambar terakhir), null kalau tidak tampil
        private Point pressScreen; // null = tidak sedang menekan tombol kiri
        private Point grab; // titik yang dipegang, dalam koordinat widget
        private boolean dragging;
        /**
         * Windows: selama diseret, posisi mengikuti pointer lewat polling dan selesai saat tombol dilepas (dibaca dari
         * OS). Window dibuat ulang saat pindah monitor sehingga event mouse window lama berhenti datang.
         */
        private final Timer dragTimer = new Timer(15, e -> {
            if (WinMouse.primaryDown()) {
                followPointer();
            } else {
                endDrag();
            }
        });

        Face() {
            setOpaque(false);
            getAccessibleContext().setAccessibleName(I18n.t("streak.name"));
            var mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showMenu(Face.this, e.getX(), e.getY());
                        return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        pressScreen = e.getLocationOnScreen();
                        grab = e.getPoint();
                        dragging = false;
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (pressScreen == null) {
                        return;
                    }
                    var now = e.getLocationOnScreen();
                    if (!dragging && Math.abs(now.x - pressScreen.x) < DRAG_SLOP
                            && Math.abs(now.y - pressScreen.y) < DRAG_SLOP) {
                        return;
                    }
                    if (!dragging) {
                        dragging = true;
                        if (Os.current().isWindows()) {
                            dragTimer.start();
                        }
                    }
                    followPointer();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) { // macOS/Windows berbeda: trigger bisa di pressed atau released
                        showMenu(Face.this, e.getX(), e.getY());
                        return;
                    }
                    if (dragging) {
                        endDrag();
                        return;
                    }
                    if (pressScreen == null || !SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    pressScreen = null;
                    if (overNetwork(e.getPoint())) {
                        if (e.getClickCount() == 1) { // dobel klik di sini tidak membuka aplikasi
                            netText = I18n.t("net.checking");
                            fitSize();
                            host.checkNetwork();
                        }
                    } else if (e.getClickCount() >= 2) {
                        host.openApp();
                    } else {
                        host.flameClicked();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    setCursor(overNetwork(e.getPoint()) ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                            : null);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        /** Titik di atas status internet yang sedang terlihat (kartu sudah muncul). */
        private boolean overNetwork(Point p) {
            return netArea != null && reveal > .5 && netArea.contains(p);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            return overNetwork(e.getPoint()) ? I18n.t("net.tooltip.hint") : super.getToolTipText(e);
        }

        /**
         * Posisi dari pointer absolut, bukan selisih dari titik awal: monitor dengan scaling berbeda punya ruang
         * koordinat sendiri. Widget juga dijaga utuh di monitor tempat pointer berada, supaya tidak pernah menggantung
         * di dua monitor dengan DPI berbeda; pindah monitor = satu lompatan bersih ke window baru.
         */
        private void followPointer() {
            var pointer = java.awt.MouseInfo.getPointerInfo();
            if (pointer == null) {
                return;
            }
            var at = pointer.getLocation();
            var screen = pointer.getDevice().getDefaultConfiguration().getBounds();
            moveTo(clampInto(new Point(at.x - grab.x, at.y - grab.y), window.getSize(), screen));
        }

        private void endDrag() {
            if (!dragging) {
                return;
            }
            dragging = false;
            pressScreen = null;
            dragTimer.stop();
            host.moved(window.getLocation());
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) { // JComponent polos tidak punya AccessibleContext
                accessibleContext = new AccessibleJComponent() {
                    @Override
                    public javax.accessibility.AccessibleRole getAccessibleRole() {
                        return javax.accessibility.AccessibleRole.PUSH_BUTTON;
                    }
                };
            }
            return accessibleContext;
        }

        private Font countFont() {
            return UIManager.getFont("Label.font").deriveFont(Font.BOLD, 22f);
        }

        private Font captionFont() {
            return UIManager.getFont("Label.font").deriveFont(11f);
        }

        String countText() {
            return look.kindling() ? look.count() + "/3" : String.valueOf(look.count());
        }

        String captionText() {
            return switch (look.mode()) {
                case GRAY -> I18n.t("streak.widget.gray");
                case FROZEN -> I18n.t("streak.widget.frozen", look.clicks() - look.cracks());
                case ACTIVE -> look.kindling() ? I18n.t("streak.widget.kindling")
                        : I18n.t("streak.widget.days", look.tier().displayName());
            };
        }

        /** Lebar status internet di samping angka (0 kalau belum ada data). */
        private int netWidth(FontMetrics caption) {
            return netColor == null ? 0 : NET_GAP + NET_DOT + 4 + caption.stringWidth(netText);
        }

        /** Pointer di atas widget, sedang diseret, atau menu klik kanan terbuka. */
        private boolean hovered() {
            if (dragging || openMenu != null && openMenu.isVisible()) {
                return true;
            }
            var pointer = java.awt.MouseInfo.getPointerInfo();
            return pointer != null && window.getBounds().contains(pointer.getLocation());
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics count = getFontMetrics(countFont());
            FontMetrics caption = getFontMetrics(captionFont());
            int firstLine = count.stringWidth(countText()) + netWidth(caption);
            int text = Math.max(firstLine, caption.stringWidth(captionText()));
            return new Dimension(PAD + FLAME_BOX + 4 + text + PAD + 4, CARD_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            long now = System.nanoTime();
            double dt = Math.min(.1, (now - lastPaint) / 1e9);
            lastPaint = now;
            double t = now / 1e9;
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color bg = UIManager.getColor("Panel.background");
                if (bg == null) {
                    bg = new Color(0x2B2D30);
                }
                boolean dark = (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 1000 < 128;
                double target = hovered() ? 1 : 0;
                double step = dt / FADE_SECONDS;
                reveal = target > reveal ? Math.min(target, reveal + step) : Math.max(target, reveal - step);
                double shown = Easing.inOutCubic(reveal);
                var card = new RoundRectangle2D.Double(.5, .5, getWidth() - 1, getHeight() - 1, ARC, ARC);
                g.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
                        Math.max(HIDDEN_ALPHA, (int) Math.round(235 * shown))));
                g.fill(card);
                if (shown > 0) {
                    g.setStroke(new BasicStroke(1f));
                    int edge = (int) Math.round(40 * shown);
                    g.setColor(dark ? new Color(255, 255, 255, edge) : new Color(0, 0, 0, edge));
                    g.draw(card);
                }

                var flame = (Graphics2D) g.create();
                flame.clip(card); // percikan tidak keluar dari kartu
                actor.paint(flame, PAD + FLAME_BOX / 2.0, getHeight() - 9, FLAME, t, dt, look);
                flame.dispose();

                if (shown <= 0) {
                    netArea = null;
                    return;
                }
                g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) shown));
                int x = PAD + FLAME_BOX + 4;
                g.setFont(countFont());
                FontMetrics count = g.getFontMetrics();
                g.setColor(StreakChip.textColor(look, dark));
                int countBase = 8 + count.getAscent();
                g.drawString(countText(), x, countBase);
                if (netColor != null) {
                    g.setFont(captionFont());
                    FontMetrics caption = g.getFontMetrics();
                    int nx = x + count.stringWidth(countText()) + NET_GAP;
                    int mid = countBase - count.getAscent() / 2 + 1; // tengah tinggi angka
                    g.setColor(netColor);
                    g.fillOval(nx, mid - NET_DOT / 2, NET_DOT, NET_DOT);
                    g.setColor(netColor == NET_OFFLINE ? netColor
                            : FlamePalette.fade(dark ? Color.WHITE : Color.BLACK, .75));
                    g.drawString(netText, nx + NET_DOT + 4, mid + caption.getAscent() / 2 - 1);
                    // area klik sedikit lebih besar dari titik + teks supaya mudah dikenai
                    netArea = new Rectangle(nx - 4, mid - 10, NET_DOT + 4 + caption.stringWidth(netText) + 8, 20);
                } else {
                    netArea = null;
                }
                g.setFont(captionFont());
                g.setColor(FlamePalette.fade(dark ? Color.WHITE : Color.BLACK, .65));
                g.drawString(captionText(), x, countBase + g.getFontMetrics().getAscent() + 2);
            } finally {
                g.dispose();
            }
        }
    }
}
