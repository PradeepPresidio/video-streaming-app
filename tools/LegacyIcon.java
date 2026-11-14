import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the legacy (pre-Android 8.0) launcher icon PNGs from the same geometry as
 * res/drawable/ic_launcher_foreground.xml (108-unit viewport) on the ic_launcher_background colour.
 * Android 8.0+ uses the adaptive icon in mipmap-anydpi-v26 instead.
 *
 *   java tools/LegacyIcon.java app/src/main/res
 */
public class LegacyIcon {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String res = args[0];
        String[] dirs = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int i = 0; i < dirs.length; i++) {
            File dir = new File(res, "mipmap-" + dirs[i]);
            dir.mkdirs();
            ImageIO.write(render(sizes[i]), "png", new File(dir, "ic_launcher.png"));
        }
    }

    static BufferedImage render(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // Legacy icon shape: rounded square, 1/12 margin (Material legacy grid), ink background.
        double m = s / 24.0, side = s - 2 * m;
        g.setColor(new Color(0x0B, 0x0D, 0x12));
        g.fill(new RoundRectangle2D.Double(m, m, side, side, side * 0.22, side * 0.22));
        // Foreground glyph spans x 26..82 of the 108 viewport; fit it to ~68% of the tile, centred.
        double k = side * 0.68 / 56.0;
        AffineTransform t = new AffineTransform();
        t.translate(s / 2.0, s / 2.0);
        t.scale(k, k);
        t.translate(-54, -54);
        g.transform(t);
        g.setColor(new Color(0xFF, 0x8A, 0x3D));
        g.fill(new RoundRectangle2D.Double(26, 30, 56, 48, 16, 16));
        g.setColor(new Color(0x0B, 0x0D, 0x12));
        Path2D tri = new Path2D.Double();
        tri.moveTo(47, 42); tri.lineTo(66, 54); tri.lineTo(47, 66); tri.closePath();
        g.fill(tri);
        g.fill(new Ellipse2D.Double(33 - 2.5, 36 - 2.5, 5, 5));
        g.fill(new Ellipse2D.Double(75 - 2.5, 72 - 2.5, 5, 5));
        g.dispose();
        return img;
    }
}
