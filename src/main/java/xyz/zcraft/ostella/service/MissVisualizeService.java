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
import java.util.LinkedList;
import java.util.List;

public class MissVisualizeService {
    private static final Color PRESSED_COLOR = new Color(255, 204, 34);
    private static final Color UNPRESSED_COLOR = new Color(68, 68, 68);

    private static final int CANVAS_WIDTH = 512;
    private static final int CANVAS_HEIGHT = 384;
    private static final double ZOOM_FACTOR = 2;
    private static final int WINDOW_MILLIS = 250;

    private static final List<Color> PATH_COLORS;

    static {
        final Color PATH_COLOR_PERFECT = new Color(102, 204, 255);
        final Color PATH_COLOR_OK = new Color(136, 179, 0);
        final Color PATH_COLOR_MEH = new Color(255, 204, 34);
        final Color PATH_COLOR_MISS = new Color(239, 83, 80);

        PATH_COLORS = List.of(
                UNPRESSED_COLOR, PATH_COLOR_MISS, PATH_COLOR_MEH, PATH_COLOR_OK,
                PATH_COLOR_PERFECT,
                PATH_COLOR_OK, PATH_COLOR_MEH, PATH_COLOR_MISS, UNPRESSED_COLOR
        );
    }

    public static byte[] visualizeMiss(ReplayAnalyze replayAnalyze, int missIndex) {
        final List<HitEvent> missEvents = replayAnalyze.events().stream()
                .filter(hitEvent -> !hitEvent.wasHit())
                .toList();

        if (missIndex < 0 || missIndex >= missEvents.size()) {
            throw new ArrayIndexOutOfBoundsException("Invalid miss index: " + missIndex + ", should be 0-" + (missEvents.size() - 1));
        }

        final HitEvent targetMiss = missEvents.get(missIndex);

        final List<TimedFrame> keyFrames = new LinkedList<>();

        final int n = replayAnalyze.replay().keyFrames().size();
        long[] cumulative = new long[n];
        long t = 0L;
        for (int i = 0; i < n; i++) {
            final long offset = replayAnalyze.replay().keyFrames().get(i).offset();
            t += offset;
            cumulative[i] = t;
        }

        for (int i = 0; i < replayAnalyze.replay().keyFrames().size(); i++) {
            keyFrames.add(new TimedFrame(cumulative[i], replayAnalyze.replay().keyFrames().get(i)));
        }

        return ImageHelper.drawMiss(
                missIndex + 1,
                targetMiss.hitObject(),
                extractNearbyKeyFrames(keyFrames, targetMiss.hitObject()),
                replayAnalyze.beatmap(),
                replayAnalyze.calculatedDifficulty()
        );
    }

    private static List<TimedFrame> extractNearbyKeyFrames(List<TimedFrame> keyFrames, HitObject hitObject) {
        int leftIndex = -1, rightIndex = -1;
        for (int i = 0; i < keyFrames.size(); i++) {
            if (keyFrames.get(i).time() > hitObject.getTime()) {
                leftIndex = i;
                rightIndex = i + 1;
                break;
            }
        }

        if (leftIndex == -1) {
            throw new RuntimeException("Could not find keyframe to lookup");
        }

        while (leftIndex > 0 && keyFrames.get(leftIndex).time() >= hitObject.getTime() - WINDOW_MILLIS) {
            leftIndex--;
        }

        while (rightIndex < keyFrames.size() - 1 && keyFrames.get(rightIndex).time() <= hitObject.getTime() + WINDOW_MILLIS) {
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

        private static int getHitWindowCategory(long offset, DifficultyAttribute diff) {
            long absOffset = Math.abs(offset);

            if (absOffset < diff.getPerfectWindow()) return 4;

            boolean isEarly = offset < 0;
            if (absOffset < diff.getOkWindow())   return isEarly ? 3 : 5;
            if (absOffset < diff.getMehWindow())  return isEarly ? 2 : 6;
            if (absOffset < diff.getMissWindow()) return isEarly ? 1 : 7;

            return isEarly ? 0 : 8;
        }

        private static byte[] drawMiss(int missIndex,
                                       HitObject hitObject,
                                       List<TimedFrame> keyFrames,
                                       OsuBeatmap beatmap,
                                       DifficultyAttribute diff) {
            final double circleRadius = diff.getCircleRadiusInPixel();
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
            Path2D.Double currentPath = new Path2D.Double();
            int currentCategory = -1;

            double lastX = 0;
            double lastY = 0;
            boolean hasLast = false;

            for (var keyFrame : keyFrames) {
                long offset = keyFrame.time() - hitObject.getTime();
                int category = getHitWindowCategory(offset, diff);

                double x = (keyFrame.keyFrame().cursorX() - hitObject.getX()) * ZOOM_FACTOR + CANVAS_WIDTH * 0.5;
                double y = (keyFrame.keyFrame().cursorY() - hitObject.getY()) * ZOOM_FACTOR + CANVAS_HEIGHT * 0.5;

                if (category != currentCategory) {
                    if (currentCategory != -1) {
                        g2d.setColor(PATH_COLORS.get(currentCategory));
                        g2d.setStroke(new BasicStroke(1.5F));
                        g2d.draw(currentPath);
                    }

                    currentPath = new Path2D.Double();
                    currentCategory = category;

                    if (hasLast) {
                        currentPath.moveTo(lastX, lastY);
                        currentPath.lineTo(x, y);
                    } else {
                        currentPath.moveTo(x, y);
                    }
                } else {
                    currentPath.lineTo(x, y);
                }

                lastX = x;
                lastY = y;
                hasLast = true;
            }

            if (currentCategory != -1) {
                g2d.setColor(PATH_COLORS.get(currentCategory));
                g2d.draw(currentPath);
            }

            // Draw frame points
            int previousFlags = keyFrames.getFirst().keyFrame().key();

            for (var keyFrame : keyFrames) {
                final double x = (keyFrame.keyFrame().cursorX() - hitObject.getX()) * ZOOM_FACTOR + CANVAS_WIDTH * 0.5;
                final double y = (keyFrame.keyFrame().cursorY() - hitObject.getY()) * ZOOM_FACTOR + CANVAS_HEIGHT * 0.5;

                int currentFlags = keyFrame.keyFrame().key();
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
                        drawSemicircle(g2d, true, true, x, y, 1 * ZOOM_FACTOR, UNPRESSED_COLOR);
                        drawSemicircle(g2d, false, true, x, y, 1 * ZOOM_FACTOR, UNPRESSED_COLOR);
                    }
                }

                previousFlags = currentFlags;
            }


            // Text
            g2d.setColor(Color.BLACK);

            final Duration duration = Duration.of(hitObject.getTime(), ChronoUnit.MILLIS);
            String missInfo = "#" + missIndex + " Miss: " + hitObject.getObjectType() + " @" +
                    String.format("%02d:%02d.%03d", duration.toMinutesPart(), duration.toSecondsPart(), duration.toMillisPart());

            g2d.setFont(new Font("Dejavu Sans", Font.PLAIN, 20));
            g2d.drawString(missInfo, 5, CANVAS_HEIGHT - 5);

            g2d.setFont(new Font("Dejavu Sans", Font.BOLD, 20));
            g2d.drawString(beatmap.getBeatmapId() + " - " + beatmap.getTitle(), 5, 20);
            g2d.setFont(new Font("Dejavu Sans", Font.PLAIN, 20));
            g2d.drawString(beatmap.getArtist() + " [" + beatmap.getVersion() + "]", 5, 40);

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

    private record TimedFrame(long time, OsuReplay.KeyFrame keyFrame) {
    }
}
