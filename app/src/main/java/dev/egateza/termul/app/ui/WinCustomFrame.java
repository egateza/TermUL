package dev.egateza.termul.app.ui;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bingkai Windows yang digambar sendiri (title bar FlatLaf di window tanpa dekorasi native), tetapi berperilaku
 * seperti window berbingkai native: Snap (seret ke tepi layar, Win+panah, Snap Layouts di tombol maximize), resize
 * dari tepi, maximize pas area kerja monitor, dan animasi docking.
 *
 * <p>Kenapa: Java hanya mengizinkan alpha per piksel (transparansi terminal / bagian window) di window tanpa dekorasi
 * native, dan window yang sejak awal tanpa dekorasi tidak perlu dibuat ulang saat transparansi diubah (tanpa kedip
 * dan tanpa ikon taskbar yang hilang sesaat). Window tanpa dekorasi biasa adalah popup bagi Windows, jadi tanpa kelas
 * ini Snap tidak berfungsi.
 *
 * <p>Caranya: window procedure AWT di-subclass lewat JNA; gaya window diberi {@code WS_CAPTION}/{@code WS_THICKFRAME}
 * dst.; area non-client dibuang ({@code WM_NCCALCSIZE}); {@code WM_NCHITTEST} melaporkan tepi (resize), title bar
 * (seret) dan tombol maximize ke Windows. Hanya dipanggil di Windows. Window procedure dipanggil di thread toolkit AWT,
 * jadi geometrinya disimpan di field volatile yang diperbarui di EDT.
 */
final class WinCustomFrame {

    private static final Logger log = LoggerFactory.getLogger(WinCustomFrame.class);

    private static final int GWL_STYLE = -16;
    private static final int GWLP_WNDPROC = -4;
    private static final long WS_POPUP = 0x8000_0000L;
    private static final long WS_CAPTION = 0x00C0_0000L;
    private static final long WS_SYSMENU = 0x0008_0000L;
    private static final long WS_THICKFRAME = 0x0004_0000L;
    private static final long WS_MINIMIZEBOX = 0x0002_0000L;
    private static final long WS_MAXIMIZEBOX = 0x0001_0000L;
    private static final int SWP_FRAME_ONLY = 0x0001 | 0x0002 | 0x0004 | 0x0010 | 0x0020; // NOSIZE..FRAMECHANGED

    private static final int WM_NCCALCSIZE = 0x0083;
    private static final int WM_NCHITTEST = 0x0084;
    private static final int WM_NCMOUSEMOVE = 0x00A0;
    private static final int WM_NCLBUTTONDOWN = 0x00A1;
    private static final int WM_NCLBUTTONUP = 0x00A2;
    private static final int WM_NCLBUTTONDBLCLK = 0x00A3;
    private static final int WM_NCMOUSELEAVE = 0x02A2;
    private static final int WM_GETMINMAXINFO = 0x0024;

    private static final int HTCLIENT = 1;
    private static final int HTCAPTION = 2;
    private static final int HTMAXBUTTON = 9;
    private static final int HTLEFT = 10;
    private static final int HTRIGHT = 11;
    private static final int HTTOP = 12;
    private static final int HTTOPLEFT = 13;
    private static final int HTTOPRIGHT = 14;
    private static final int HTBOTTOM = 15;
    private static final int HTBOTTOMLEFT = 16;
    private static final int HTBOTTOMRIGHT = 17;

    /** Tebal tepi resize dan panjang sudutnya (px logis); sama dengan tepi resize FlatLaf. */
    private static final int RESIZE_BORDER = 5;
    private static final int RESIZE_CORNER = 16;

    interface User32 extends Library {
        Pointer GetWindowLongPtrW(Pointer hwnd, int index);

        Pointer SetWindowLongPtrW(Pointer hwnd, int index, Pointer value);

        Pointer SetWindowLongPtrW(Pointer hwnd, int index, WndProc value);

        Pointer CallWindowProcW(Pointer prev, Pointer hwnd, int msg, Pointer wParam, Pointer lParam);

        boolean SetWindowPos(Pointer hwnd, Pointer after, int x, int y, int cx, int cy, int flags);

        boolean GetWindowRect(Pointer hwnd, int[] rect);

        boolean IsZoomed(Pointer hwnd);

        Pointer MonitorFromRect(Pointer rect, int flags);

        boolean GetMonitorInfoW(Pointer monitor, int[] info);

        boolean TrackMouseEvent(Pointer tme);
    }

    interface Dwm extends Library {
        int DwmSetWindowAttribute(Pointer hwnd, int attribute, int[] value, int size);

        int DwmExtendFrameIntoClientArea(Pointer hwnd, int[] margins);
    }

    interface WndProc extends Callback {
        Pointer callback(Pointer hwnd, int msg, Pointer wParam, Pointer lParam);
    }

    private static User32 user32; // EDT
    private static Dwm dwm; // EDT
    private static boolean failed; // EDT

    private final JFrame frame;
    private WndProc proc; // dipegang supaya callback tidak di-GC
    private volatile Pointer previous;
    private long installedHwnd; // EDT

    // geometri dalam piksel fisik, relatif ke pojok kiri atas window; ditulis EDT, dibaca thread toolkit
    private volatile int border = RESIZE_BORDER;
    private volatile int corner = RESIZE_CORNER;
    private volatile Rectangle caption = new Rectangle();
    private volatile List<Rectangle> controls = List.of();
    private volatile Rectangle maxButtonArea = new Rectangle();
    private JButton maxButton; // EDT; tombol maximize/restore FlatLaf yang sedang tampil
    private volatile boolean maxHover;

    private final ComponentAdapter relayout = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            updateGeometry();
        }

        @Override
        public void componentShown(ComponentEvent e) {
            updateGeometry();
        }
    };
    private JComponent titlePane; // EDT; title bar FlatLaf (dicari ulang kalau UI diganti)

    WinCustomFrame(JFrame frame) {
        this.frame = frame;
        frame.addComponentListener(relayout);
        frame.getRootPane().addComponentListener(relayout);
        // maximize/restore menukar tombol FlatLaf; geometri dihitung setelah layout-nya selesai
        frame.addWindowStateListener(e -> SwingUtilities.invokeLater(this::updateGeometry));
        // pindah ke monitor dengan scaling lain: ukuran logis tetap (tanpa componentResized), tapi skala piksel fisik
        // berubah. Event ini bisa datang dari thread toolkit.
        frame.addPropertyChangeListener("graphicsConfiguration", e -> SwingUtilities.invokeLater(this::updateGeometry));
    }

    /**
     * Pasang di HWND window (setelah {@code addNotify}, sebelum window tampil supaya bingkainya tidak "melompat").
     * Aman dipanggil berulang; HWND baru dipasang ulang.
     */
    void install() {
        if (failed || !frame.isDisplayable()) {
            return;
        }
        try {
            if (user32 == null) {
                user32 = Native.load("user32", User32.class);
                dwm = Native.load("dwmapi", Dwm.class);
            }
            Pointer hwnd = Native.getWindowPointer(frame);
            if (hwnd == null || Pointer.nativeValue(hwnd) == installedHwnd) {
                return;
            }
            installedHwnd = Pointer.nativeValue(hwnd);
            proc = this::windowProc;
            previous = user32.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, proc);
            long style = Pointer.nativeValue(user32.GetWindowLongPtrW(hwnd, GWL_STYLE));
            style = (style & ~WS_POPUP) | WS_CAPTION | WS_SYSMENU | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
            user32.SetWindowLongPtrW(hwnd, GWL_STYLE, new Pointer(style));
            user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_FRAME_ONLY);
            dwm.DwmSetWindowAttribute(hwnd, 33, new int[] {2}, 4); // DWMWA_WINDOW_CORNER_PREFERENCE = ROUND (Win11)
            dwm.DwmExtendFrameIntoClientArea(hwnd, new int[] {0, 0, 1, 0}); // bayangan window seperti bingkai native
            updateGeometry();
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            failed = true;
            log.warn("Bingkai window Windows gagal dipasang: {}", e.toString());
        }
    }

    private Pointer windowProc(Pointer hwnd, int msg, Pointer wParam, Pointer lParam) {
        switch (msg) {
            case WM_NCCALCSIZE -> {
                if (Pointer.nativeValue(wParam) != 0) {
                    if (user32.IsZoomed(hwnd)) {
                        // maximize: Windows menaruh window melewati tiap sisi layar setebal bingkai; area isi
                        // dibatasi ke area kerja monitor supaya title bar dan tombolnya tidak terpotong
                        clientToWorkArea(lParam);
                    }
                    return new Pointer(0); // tanpa area non-client: seluruh window adalah isi (title bar FlatLaf)
                }
            }
            case WM_NCHITTEST -> {
                int hit = hitTest(hwnd, Pointer.nativeValue(lParam));
                setMaxHover(hit == HTMAXBUTTON, hwnd);
                return new Pointer(hit);
            }
            case WM_NCMOUSEMOVE -> setMaxHover(Pointer.nativeValue(wParam) == HTMAXBUTTON, hwnd);
            case WM_NCMOUSELEAVE -> setMaxHover(false, hwnd);
            case WM_NCLBUTTONDOWN, WM_NCLBUTTONDBLCLK -> {
                if (Pointer.nativeValue(wParam) == HTMAXBUTTON) {
                    return new Pointer(0); // klik diproses saat dilepas, seperti tombol biasa
                }
            }
            case WM_NCLBUTTONUP -> {
                if (Pointer.nativeValue(wParam) == HTMAXBUTTON) {
                    SwingUtilities.invokeLater(this::clickMaxButton);
                    return new Pointer(0);
                }
            }
            case WM_GETMINMAXINFO -> {
                // Windows mengisi ukuran & posisi maximize untuk monitor tempat window berada; AWT menimpanya dengan
                // Frame.getMaximizedBounds(), yang diset FlatLaf saat tombol maximize-nya diklik dan tidak diperbarui
                // saat window pindah monitor. Akibatnya maximize lewat Windows (dobel klik title bar, Snap, Win+Up)
                // jatuh di monitor lama, dan ukuran restore menyusut setiap pindah monitor beda scaling. Ukuran
                // minimum dari AWT tetap dipakai; area isi saat maximize sudah diatur WM_NCCALCSIZE.
                int[] maxSizeAndPosition = lParam.getIntArray(8, 4); // ptMaxSize, ptMaxPosition
                int[] maxTrackSize = lParam.getIntArray(32, 2); // ptMaxTrackSize
                Pointer result = user32.CallWindowProcW(previous, hwnd, msg, wParam, lParam);
                lParam.write(8, maxSizeAndPosition, 0, 4);
                lParam.write(32, maxTrackSize, 0, 2);
                return result;
            }
            default -> {
            }
        }
        return user32.CallWindowProcW(previous, hwnd, msg, wParam, lParam);
    }

    private int hitTest(Pointer hwnd, long lParam) {
        int screenX = (short) (lParam & 0xFFFF);
        int screenY = (short) ((lParam >> 16) & 0xFFFF);
        int[] r = new int[4];
        if (!user32.GetWindowRect(hwnd, r)) {
            return HTCLIENT;
        }
        int x = screenX - r[0];
        int y = screenY - r[1];
        int w = r[2] - r[0];
        int h = r[3] - r[1];
        if (!user32.IsZoomed(hwnd)) {
            int edge = edgeHit(x, y, w, h, border, corner);
            if (edge != HTCLIENT) {
                return edge;
            }
        }
        if (maxButtonArea.contains(x, y)) {
            return HTMAXBUTTON;
        }
        if (caption.contains(x, y)) {
            for (Rectangle c : controls) {
                if (c.contains(x, y)) {
                    return HTCLIENT;
                }
            }
            return HTCAPTION;
        }
        return HTCLIENT;
    }

    /** Hit test tepi resize (piksel relatif window); {@code HTCLIENT} kalau bukan di tepi. */
    static int edgeHit(int x, int y, int w, int h, int border, int corner) {
        boolean left = x < border;
        boolean right = x >= w - border;
        boolean top = y < border;
        boolean bottom = y >= h - border;
        boolean nearLeft = x < corner;
        boolean nearRight = x >= w - corner;
        boolean nearTop = y < corner;
        boolean nearBottom = y >= h - corner;
        if ((top && nearLeft) || (left && nearTop)) {
            return HTTOPLEFT;
        }
        if ((top && nearRight) || (right && nearTop)) {
            return HTTOPRIGHT;
        }
        if ((bottom && nearLeft) || (left && nearBottom)) {
            return HTBOTTOMLEFT;
        }
        if ((bottom && nearRight) || (right && nearBottom)) {
            return HTBOTTOMRIGHT;
        }
        if (left) {
            return HTLEFT;
        }
        if (right) {
            return HTRIGHT;
        }
        if (top) {
            return HTTOP;
        }
        if (bottom) {
            return HTBOTTOM;
        }
        return HTCLIENT;
    }

    /**
     * NCCALCSIZE_PARAMS.rgrc[0] (RECT pertama di lParam) = area kerja monitor tujuan window. Monitornya dipilih dari
     * rgrc[0] (posisi baru window), bukan dari posisi window sekarang: saat dibuka dari minimize window masih di
     * -32000,-32000, sehingga monitor "terdekat" bisa monitor lain. Area isi lalu jatuh di luar window dan semua yang
     * digambar terpotong (window tampak freeze sampai di-resize).
     */
    private static void clientToWorkArea(Pointer params) {
        Pointer monitor = user32.MonitorFromRect(params, 2); // MONITOR_DEFAULTTONEAREST
        int[] info = new int[10]; // cbSize, rcMonitor(4), rcWork(4), dwFlags
        info[0] = 40;
        if (monitor == null || !user32.GetMonitorInfoW(monitor, info)) {
            return;
        }
        params.setInt(0, info[5]);
        params.setInt(4, info[6]);
        params.setInt(8, info[7]);
        params.setInt(12, info[8]);
    }

    /** Efek hover tombol maximize: event mouse di area HTMAXBUTTON tidak sampai ke Java. */
    private void setMaxHover(boolean hover, Pointer hwnd) {
        if (hover == maxHover) {
            return;
        }
        maxHover = hover;
        if (hover) {
            trackNonClientLeave(hwnd);
        }
        SwingUtilities.invokeLater(() -> {
            if (maxButton != null) {
                maxButton.getModel().setRollover(maxHover);
            }
        });
    }

    /** Minta WM_NCMOUSELEAVE saat mouse meninggalkan area non-client (TRACKMOUSEEVENT, TME_LEAVE | TME_NONCLIENT). */
    private static void trackNonClientLeave(Pointer hwnd) {
        var tme = new Memory(Native.POINTER_SIZE == 8 ? 24 : 16);
        tme.setInt(0, (int) tme.size());
        tme.setInt(4, 0x0002 | 0x0010);
        tme.setPointer(8, hwnd);
        tme.setInt(8 + Native.POINTER_SIZE, 0);
        user32.TrackMouseEvent(tme);
    }

    private void clickMaxButton() {
        if (maxButton != null && maxButton.isShowing()) {
            maxButton.getModel().setRollover(false);
            maxButton.doClick(0); // aksi FlatLaf: maximize atau restore
        }
    }

    /** Hitung ulang area title bar dan tombol dari title bar FlatLaf. EDT. */
    void updateGeometry() {
        if (!frame.isDisplayable()) {
            return;
        }
        double scale = frame.getGraphicsConfiguration().getDefaultTransform().getScaleX();
        border = (int) Math.round(RESIZE_BORDER * scale);
        corner = (int) Math.round(RESIZE_CORNER * scale);
        if (titlePane == null || titlePane.getParent() == null) {
            titlePane = findTitlePane(frame.getRootPane());
            if (titlePane != null) {
                titlePane.addComponentListener(relayout); // menu yang ditanam di title bar berubah lebar
            }
        }
        if (titlePane == null || !titlePane.isShowing()) {
            caption = new Rectangle();
            controls = List.of();
            maxButtonArea = new Rectangle();
            maxButton = null;
            return;
        }
        caption = physical(titlePane, new Rectangle(0, 0, titlePane.getWidth(), titlePane.getHeight()), scale);
        var found = new ArrayList<Rectangle>();
        JButton max = null;
        var inTitleBar = descendants(titlePane);
        // menu bar yang "ditanam" FlatLaf tetap anak root pane (diposisikan di atas title bar), bukan anak title bar
        var menuBar = frame.getRootPane().getJMenuBar();
        if (menuBar != null && menuBar.isShowing()) {
            inTitleBar.addAll(descendants(menuBar));
        }
        for (Component c : inTitleBar) {
            if (!c.isShowing()) {
                continue;
            }
            if (c instanceof JButton b && isMaximizeButton(b)) {
                max = b;
            } else if (isControl(c)) {
                found.add(physical(c, new Rectangle(0, 0, c.getWidth(), c.getHeight()), scale));
            }
        }
        controls = List.copyOf(found);
        maxButton = max;
        maxButtonArea = max == null ? new Rectangle()
                : physical(max, new Rectangle(0, 0, max.getWidth(), max.getHeight()), scale);
    }

    private Rectangle physical(Component c, Rectangle r, double scale) {
        Rectangle inFrame = SwingUtilities.convertRectangle(c, r, frame);
        return new Rectangle((int) Math.floor(inFrame.x * scale), (int) Math.floor(inFrame.y * scale),
                (int) Math.ceil(inFrame.width * scale), (int) Math.ceil(inFrame.height * scale));
    }

    /** Tombol maximize / restore milik FlatLaf (client property {@code AccessibleName}). */
    private static boolean isMaximizeButton(JButton b) {
        Object name = b.getClientProperty("AccessibleName");
        return "Maximize".equals(name) || "Restore".equals(name);
    }

    /**
     * Komponen di title bar yang menerima mouse (menu, tombol, input, indikator yang bisa diklik, tooltip); sisanya area
     * seret. Di area seret event mouse tidak sampai ke Java.
     */
    private static boolean isControl(Component c) {
        if (c instanceof JComponent jc && jc.getClientProperty("JComponent.titleBarCaption") instanceof Boolean caption) {
            return !caption;
        }
        return c instanceof JMenu || c instanceof AbstractButton || c instanceof JTextComponent
                || c instanceof JComboBox<?> || c instanceof JSpinner
                || c.getMouseListeners().length > 0 || c.getMouseMotionListeners().length > 0;
    }

    /** Title bar FlatLaf ({@code FlatTitlePane}) ada langsung di layered pane root pane. */
    private static JComponent findTitlePane(javax.swing.JRootPane root) {
        for (Component c : root.getLayeredPane().getComponents()) {
            if (c instanceof JComponent jc && jc.getClass().getSimpleName().contains("TitlePane")) {
                return jc;
            }
        }
        return null;
    }

    private static List<Component> descendants(Container parent) {
        var result = new ArrayList<Component>();
        for (Component c : parent.getComponents()) {
            result.add(c);
            if (c instanceof Container child) {
                result.addAll(descendants(child));
            }
        }
        return result;
    }
}
