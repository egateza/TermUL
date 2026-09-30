import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
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
 * Menggambar ikon aplikasi TermUL ({@code >_} di kotak bulat) dan menulis:
 * <ul>
 *   <li>{@code app/src/main/packaging/termul.ico} (16..256 px, entry PNG) untuk jpackage;</li>
 *   <li>{@code app/src/main/resources/dev/egateza/termul/app/icons/app-<ukuran>.png} untuk ikon window.</li>
 * </ul>
 * Jalankan dari root repo: {@code java tools/MakeAppIcon.java}
 */
public class MakeAppIcon {

    private static final int[] ICO_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    private static final int[] WINDOW_SIZES = {16, 32, 48, 64, 128, 256};

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
        for (int size : WINDOW_SIZES) {
            ImageIO.write(draw(size), "png", pngDir.resolve("app-" + size + ".png").toFile());
        }
        System.out.println("Ditulis: " + ico + " dan " + WINDOW_SIZES.length + " PNG di " + pngDir);
    }

    static BufferedImage draw(int size) {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size;
        double pad = s * 0.04;
        var box = new RoundRectangle2D.Double(pad, pad, s - 2 * pad, s - 2 * pad, s * 0.36, s * 0.36);
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B3245), 0, (float) s, new Color(0x151923)));
        g.fill(box);
        g.setStroke(new BasicStroke((float) Math.max(1, s * 0.03)));
        g.setColor(new Color(0x3A4460));
        g.draw(box);

        float stroke = (float) Math.max(1.5, s * 0.11);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x3DDC84));
        var chevron = new Path2D.Double();
        chevron.moveTo(s * 0.24, s * 0.30);
        chevron.lineTo(s * 0.46, s * 0.50);
        chevron.lineTo(s * 0.24, s * 0.70);
        g.draw(chevron);
        g.setColor(new Color(0xE6EAF2));
        var cursor = new Path2D.Double();
        cursor.moveTo(s * 0.54, s * 0.70);
        cursor.lineTo(s * 0.76, s * 0.70);
        g.draw(cursor);
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
}
