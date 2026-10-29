import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Menggambar ikon aplikasi TermUL ("T-cursor", sama dengan TermUL Mobile) dan menulis:
 * <ul>
 *   <li>{@code app/src/main/packaging/termul.ico} (16..256 px, entry PNG) dan {@code termul.icns} untuk jpackage;</li>
 *   <li>{@code app/src/main/resources/dev/egateza/termul/app/icons/app-<ukuran>.png} untuk ikon window;</li>
 *   <li>{@code .../icons/termul.ico} di jar, untuk ikon shortcut yang ikut update lewat menu (ADR 0010).</li>
 * </ul>
 * Jalankan dari root repo: {@code java tools/MakeAppIcon.java}
 */
public class MakeAppIcon {

    private static final int[] ICO_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    private static final int[] WINDOW_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        Path ico = Path.of("app/src/main/packaging/termul.ico");
        Path pngDir = Path.of("app/src/main/resources/dev/egateza/termul/app/icons");
        Files.createDirectories(ico.getParent());
        Files.createDirectories(pngDir);

        var entries = new ArrayList<byte[]>();
        for (int size : ICO_SIZES) {
            entries.add(png(draw(size)));
        }
        Files.write(ico, ico(ICO_SIZES, entries));
        // salinan di jar: ikon shortcut / menu Explorer ikut berganti lewat update menu (ADR 0010)
        Files.write(pngDir.resolve("termul.ico"), ico(ICO_SIZES, entries));
        Path icns = ico.resolveSibling("termul.icns");
        Files.write(icns, icns());
        for (int size : WINDOW_SIZES) {
            ImageIO.write(draw(size), "png", pngDir.resolve("app-" + size + ".png").toFile());
        }
        System.out.println("Ditulis: " + ico + ", " + icns + " dan " + WINDOW_SIZES.length + " PNG di " + pngDir);
    }

    /**
     * Ikon "T-cursor" (sama dengan TermUL Mobile, prototipe "TermUL Desktop" → Ikon): kotak bulat {@code #0F1316}
     * bergaris {@code #2F3549} (1 px di 16 px, supaya tidak hilang di title bar gelap), palang T terang dan batang T
     * berupa kursor blok amber. Koordinat dari SVG prototipe, viewBox {@code 18 18 72 72}.
     */
    static BufferedImage draw(int size) {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double k = size / 72.0;
        g.scale(k, k);
        g.translate(-18, -18);
        var box = new RoundRectangle2D.Double(20.25, 20.25, 67.5, 67.5, 28, 28); // rx 14 = diameter sudut 28
        g.setColor(new Color(0x0F1316));
        g.fill(box);
        g.setStroke(new BasicStroke(4.5f));
        g.setColor(new Color(0x2F3549));
        g.draw(box);
        g.setColor(new Color(0xE8EEF0));
        g.fill(new RoundRectangle2D.Double(33, 35, 42, 9, 4, 4));
        g.setColor(new Color(0xF0B34A));
        g.fill(new RoundRectangle2D.Double(48, 48, 12, 27, 4, 4));
        g.dispose();
        return img;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Format ICO: header 6 byte, direktori 16 byte per gambar, lalu data PNG. */
    private static byte[] ico(int[] sizes, List<byte[]> pngs) {
        int offset = 6 + 16 * sizes.length;
        int total = offset + pngs.stream().mapToInt(b -> b.length).sum();
        var buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            int dim = sizes[i] >= 256 ? 0 : sizes[i]; // 0 = 256
            buf.put((byte) dim).put((byte) dim).put((byte) 0).put((byte) 0);
            buf.putShort((short) 1).putShort((short) 32);
            buf.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(buf::put);
        return buf.array();
    }

    /** Format ICNS (macOS, jpackage): header 'icns' + panjang, lalu entry tipe OSType + panjang + PNG (big endian). */
    private static byte[] icns() throws IOException {
        String[] types = {"icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10", "ic11", "ic12", "ic13", "ic14"};
        int[] sizes = {16, 32, 64, 128, 256, 512, 1024, 32, 64, 256, 512};
        var body = new ByteArrayOutputStream();
        for (int i = 0; i < types.length; i++) {
            byte[] data = png(draw(sizes[i]));
            body.write(types[i].getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            body.write(ByteBuffer.allocate(4).putInt(8 + data.length).array());
            body.write(data);
        }
        var out = ByteBuffer.allocate(8 + body.size());
        out.put("icns".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(8 + body.size());
        out.put(body.toByteArray());
        return out.array();
    }
}
