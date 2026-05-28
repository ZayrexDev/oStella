package xyz.zcraft.ostella.service;

import xyz.zcraft.osu.parser.data.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class MissVisualizeService {
    public static final Color PRESSED_COLOR = new Color(255, 204, 34);
    public static final Color UNPRESSED_COLOR = new Color(68, 68, 68);
    public static final Color PATH_COLOR = new Color(235, 71, 145);
    private static final int CANVAS_WIDTH = 512;
    private static final int CANVAS_HEIGHT = 384;
    private static final double ZOOM_FACTOR = 1.5;
    private static final int WINDOW_MILLIS = 200;

    public static byte[] visualizeMiss(ReplayAnalyze replayAnalyze, int missIndex) {
        final List<HitEvent> missEvents = replayAnalyze.events().stream()
                .filter(hitEvent -> !hitEvent.wasHit())
                .toList();

        if (missIndex < 0 || missIndex >= missEvents.size()) {
            throw new ArrayIndexOutOfBoundsException("Invalid miss index: " + missIndex + ", should be 0-" + (missEvents.size() - 1));
        }

        final HitEvent targetMiss = missEvents.get(missIndex);

        final List<OsuReplay.KeyFrame> keyFrames = extractNearbyKeyFrames(replayAnalyze.replay().keyFrames(), targetMiss.hitObject());

        boolean hasEZ = (replayAnalyze.replay().mods() & 2) > 0;
        boolean hasHR = (replayAnalyze.replay().mods() & 16) > 0;

        double cs = replayAnalyze.beatmap().getCs();

        if (hasHR) {
            cs = Math.min(10.0, cs * 1.3);
        } else if (hasEZ) {
            cs = cs * 0.5;
        }

        final double circleRadius = 54.4 - 4.48 * cs;

        return ImageHelper.drawMiss(missIndex + 1, circleRadius, targetMiss.hitObject(), keyFrames, replayAnalyze.beatmap());
    }

    private static List<OsuReplay.KeyFrame> extractNearbyKeyFrames(List<OsuReplay.KeyFrame> keyFrames, HitObject hitObject) {
        final int n = keyFrames.size();
        long[] cumulative = new long[n];
        long t = 0L;
        for (int i = 0; i < n; i++) {
            final long offset = keyFrames.get(i).offset();
            t += offset;
            cumulative[i] = t;
        }

        int leftIndex = -1, rightIndex = -1;
        for (int i = 0; i < cumulative.length; i++) {
            if (cumulative[i] > hitObject.getTime()) {
                leftIndex = i;
                rightIndex = i + 1;
                break;
            }
        }

        if (leftIndex == -1) {
            throw new RuntimeException("Could not find keyframe to lookup");
        }

        while (leftIndex > 0 && cumulative[leftIndex] >= hitObject.getTime() - WINDOW_MILLIS) {
            leftIndex--;
        }

        while (rightIndex < keyFrames.size() - 1 && cumulative[rightIndex] <= hitObject.getTime() + WINDOW_MILLIS) {
            rightIndex++;
        }

        return keyFrames.subList(leftIndex, rightIndex);
    }

    private static class ImageHelper {
        public static BufferedImage zoomAndCrop(BufferedImage originalImage, double zoomFactor) {
            if (zoomFactor == 1) return originalImage;

            int origWidth = originalImage.getWidth();
            int origHeight = originalImage.getHeight();

            int cropWidth = (int) (origWidth / zoomFactor);
            int cropHeight = (int) (origHeight / zoomFactor);

            int cropX = (origWidth - cropWidth) / 2;
            int cropY = (origHeight - cropHeight) / 2;

            BufferedImage croppedImage = originalImage.getSubimage(cropX, cropY, cropWidth, cropHeight);

            BufferedImage zoomedImage = new BufferedImage(origWidth, origHeight, originalImage.getType());
            Graphics2D g2d = zoomedImage.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.drawImage(croppedImage, 0, 0, origWidth, origHeight, null);

            g2d.dispose();
            return zoomedImage;
        }

        public static void drawSemicircle(Graphics2D g2d, boolean left, boolean fill, double centerX, double centerY, double radius, Color color) {
            double topLeftX = centerX - radius;
            double topLeftY = centerY - radius;
            double diameter = 2 * radius;

            double startAngle = left ? 90 : 270;
            double extentAngle = 180;

            Arc2D.Double semicircle = new Arc2D.Double(
                    topLeftX, topLeftY,
                    diameter, diameter,
                    startAngle, extentAngle,
                    fill ? Arc2D.PIE : Arc2D.OPEN
            );

            g2d.setColor(color);
            if (fill) {
                g2d.fill(semicircle);
            } else {
                g2d.draw(semicircle);
            }
        }

        private static byte[] drawMiss(int missIndex, double circleRadius, HitObject hitObject, List<OsuReplay.KeyFrame> keyFrames, OsuBeatmap beatmap) {
            BufferedImage canvas = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = canvas.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

            // Draw circle
            Ellipse2D circle = new Ellipse2D.Double(
                    CANVAS_WIDTH * 0.5 - circleRadius * ZOOM_FACTOR,
                    CANVAS_HEIGHT * 0.5 - circleRadius * ZOOM_FACTOR,
                    circleRadius * 2 * ZOOM_FACTOR,
                    circleRadius * 2 * ZOOM_FACTOR
            );

            g2d.setColor(Color.BLACK);
            g2d.draw(circle);

            // Draw cursor path
            int previousFlags = keyFrames.getFirst().key();

            Path2D.Double path = new Path2D.Double();
            path.moveTo(
                    (keyFrames.getFirst().cursorX() - hitObject.getX()) * ZOOM_FACTOR + CANVAS_WIDTH * 0.5,
                    (keyFrames.getFirst().cursorY() - hitObject.getY()) * ZOOM_FACTOR + CANVAS_HEIGHT * 0.5
            );
            for (OsuReplay.KeyFrame keyFrame : keyFrames) {
                final double x = (keyFrame.cursorX() - hitObject.getX()) * ZOOM_FACTOR + CANVAS_WIDTH * 0.5;
                final double y = (keyFrame.cursorY() - hitObject.getY()) * ZOOM_FACTOR + CANVAS_HEIGHT * 0.5;

                path.lineTo(x, y);

                int currentFlags = keyFrame.key();
                int newlyPressed = currentFlags & ~previousFlags;
                boolean isNewPress = (newlyPressed & 15) > 0;

                boolean leftPressed = (currentFlags & 4) > 0 || (currentFlags & 1) > 0;
                boolean rightPressed = (currentFlags & 8) > 0 || (currentFlags & 2) > 0;

                if (isNewPress) {
                    g2d.setStroke(new BasicStroke(3));
                    drawSemicircle(g2d, true, false, x, y, 6 * ZOOM_FACTOR, leftPressed ? PRESSED_COLOR : UNPRESSED_COLOR);
                    drawSemicircle(g2d, false, false, x, y, 6 * ZOOM_FACTOR, rightPressed ? PRESSED_COLOR : UNPRESSED_COLOR);
                    g2d.setStroke(new BasicStroke(1));
                } else {
                    if (leftPressed || rightPressed) {
                        drawSemicircle(g2d, true, true, x, y, 2 * ZOOM_FACTOR, leftPressed ? PRESSED_COLOR : UNPRESSED_COLOR);
                        drawSemicircle(g2d, false, true, x, y, 2 * ZOOM_FACTOR, rightPressed ? PRESSED_COLOR : UNPRESSED_COLOR);
                    } else {
                        drawSemicircle(g2d, true, true, x, y, 1 * ZOOM_FACTOR, PATH_COLOR);
                        drawSemicircle(g2d, false, true, x, y, 1 * ZOOM_FACTOR, PATH_COLOR);
                    }
                }

                previousFlags = currentFlags;
            }

            g2d.setColor(PATH_COLOR);
            g2d.draw(path);


            // Text
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Dejavu Sans", Font.PLAIN, 20));

            final Duration duration = Duration.of(hitObject.getTime(), ChronoUnit.MILLIS);
            String missInfo = "#" + missIndex + " Miss: " + hitObject.getObjectType() + " at " +
                    String.format("%02d:%02d.%03d", duration.toMinutesPart(), duration.toSecondsPart(), duration.toMillisPart());

            g2d.drawString(missInfo, 5, CANVAS_HEIGHT - 5);

            String beatmapInfo = beatmap.getArtist() + " - " + beatmap.getTitle() + " [" + beatmap.getVersion() + "]";
            g2d.drawString(beatmapInfo, 5,  20);

            g2d.dispose();

            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            try {
                ImageIO.write(zoomAndCrop(canvas, 1), "png", output);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return output.toByteArray();
        }
    }
}
