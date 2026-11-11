import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Helper for tools/prepare_media.sh (the local ffmpeg build has no drawtext filter).
 * Run with the single-file launcher: java tools/MediaTool.java <command> ...
 *
 *   title  <w> <h> <out.png> <title> [subtitle]   transparent overlay: bottom gradient + title text
 *   card   <w> <h> <out.png> <title>              opaque card used for placeholder (fallback) clips
 *   srtcut <in.srt> <startSec> <durSec> <out.srt>  keep cues inside the window, shifted to start at 0
 */
public class MediaTool {
    private static final Color ACCENT = new Color(0xFF, 0x8A, 0x3D);

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        switch (args[0]) {
            case "title" -> title(Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3], args[4],
                    args.length > 5 ? args[5] : "", false);
            case "card" -> title(Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3], args[4], "", true);
            case "srtcut" -> srtCut(args[1], Double.parseDouble(args[2]), Double.parseDouble(args[3]), args[4]);
            default -> throw new IllegalArgumentException("unknown command " + args[0]);
        }
    }

    private static void title(int w, int h, String out, String text, String sub, boolean opaque) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (opaque) {
            // Placeholder card: transparent centre band so the test pattern stays visible behind the title.
            g.setColor(new Color(11, 13, 18, 200));
            g.fillRect(0, h / 3, w, h / 3);
        } else {
            int gradTop = (int) (h * 0.45);
            g.setPaint(new GradientPaint(0, gradTop, new Color(0, 0, 0, 0), 0, h, new Color(8, 9, 12, 235)));
            g.fillRect(0, gradTop, w, h - gradTop);
        }
        int pad = Math.max(12, w / 20);
        int maxWidth = w - 2 * pad;
        int size = Math.max(18, Math.min(h / 7, w / 6));
        Font font;
        List<String> lines;
        do {
            font = new Font("SansSerif", Font.BOLD, size);
            lines = wrap(text, g.getFontMetrics(font), maxWidth);
            size -= 2;
        } while ((lines.size() > 3 || widest(lines, g.getFontMetrics(font)) > maxWidth) && size > 14);
        FontMetrics fm = g.getFontMetrics(font);
        Font subFont = new Font("SansSerif", Font.PLAIN, Math.max(12, size / 3));
        FontMetrics sfm = g.getFontMetrics(subFont);
        int blockH = lines.size() * fm.getHeight() + (sub.isEmpty() ? 0 : sfm.getHeight() + 6);
        int y = opaque ? (h - blockH) / 2 + fm.getAscent() : h - pad - blockH + fm.getAscent();
        g.setFont(font);
        for (String line : lines) {
            int x = opaque ? (w - fm.stringWidth(line)) / 2 : pad;
            g.setColor(new Color(0, 0, 0, 160));
            g.drawString(line, x + 2, y + 2);
            g.setColor(Color.WHITE);
            g.drawString(line, x, y);
            y += fm.getHeight();
        }
        if (!sub.isEmpty()) {
            g.setColor(ACCENT);
            g.fillRect(pad, y - fm.getAscent() + 4, Math.max(24, w / 12), 4);
            g.setFont(subFont);
            g.setColor(new Color(230, 232, 238));
            g.drawString(sub, pad, y - fm.getAscent() + 10 + sfm.getAscent());
        }
        g.dispose();
        ImageIO.write(img, "png", new File(out));
    }

    private static int widest(List<String> lines, FontMetrics fm) {
        int m = 0;
        for (String l : lines) m = Math.max(m, fm.stringWidth(l));
        return m;
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(trial) <= maxWidth || cur.length() == 0) {
                cur.setLength(0);
                cur.append(trial);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private static final Pattern TIME = Pattern.compile(
            "(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->\\s*(\\d+):(\\d+):(\\d+)[,.](\\d+)");

    private static double secs(Matcher m, int i) {
        return Integer.parseInt(m.group(i)) * 3600 + Integer.parseInt(m.group(i + 1)) * 60
                + Integer.parseInt(m.group(i + 2)) + Integer.parseInt(m.group(i + 3)) / 1000.0;
    }

    private static String fmt(double s) {
        long ms = Math.round(s * 1000);
        return String.format("%02d:%02d:%02d,%03d", ms / 3600000, (ms / 60000) % 60, (ms / 1000) % 60, ms % 1000);
    }

    private static void srtCut(String in, double start, double dur, String out) throws Exception {
        String src = Files.readString(new File(in).toPath(), StandardCharsets.UTF_8)
                .replace("﻿", "").replace("\r", "");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String block : src.split("\n\\s*\n")) {
            String[] lines = block.trim().split("\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher m = TIME.matcher(lines[i]);
                if (!m.find()) continue;
                double a = secs(m, 1) - start, b = secs(m, 5) - start;
                if (b <= 0 || a >= dur) break;
                a = Math.max(0, a);
                b = Math.min(dur, b);
                sb.append(++n).append('\n').append(fmt(a)).append(" --> ").append(fmt(b)).append('\n');
                for (int j = i + 1; j < lines.length; j++) sb.append(lines[j]).append('\n');
                sb.append('\n');
                break;
            }
        }
        Files.writeString(new File(out).toPath(), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("srtcut: " + n + " cues -> " + out);
    }
}
