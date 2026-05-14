package xyz.zcraft.util;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import xyz.zcraft.model.replay.OsuReplay;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class ReplayParser {
    public static OsuReplay parseReplay(String filePath) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        return parseReplay(bytes);
    }

    public static OsuReplay parseReplay(byte[] bytes) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        byte gameMode = buffer.get();
        int gameVersion = buffer.getInt();

        String beatmapHash = readOsuString(buffer);
        String playerName = readOsuString(buffer);
        String replayHash = readOsuString(buffer);

        short count300 = buffer.getShort();
        short count100 = buffer.getShort();
        short count50 = buffer.getShort();
        short countGeki = buffer.getShort();
        short countKatu = buffer.getShort();
        short countMiss = buffer.getShort();

        int totalScore = buffer.getInt();
        short maxCombo = buffer.getShort();
        byte perfectCombo = buffer.get();
        int mods = buffer.getInt();

        String lifeBarGraph = readOsuString(buffer);

        long timestamp = buffer.getLong();

        final List<OsuReplay.KeyFrame> keyFrames = parseReplayFrames(buffer);

        return new OsuReplay(gameMode, gameVersion, beatmapHash, playerName, replayHash,
                count300, count100, count50, countGeki, countKatu, countMiss,
                totalScore, maxCombo, perfectCombo == 1, mods, lifeBarGraph, timestamp, keyFrames);
    }

    private static String readOsuString(ByteBuffer buffer) {
        byte indicator = buffer.get();
        if (indicator == 0x00) return "";
        if (indicator != 0x0b) throw new IllegalStateException("Expected 0x0b for string");

        int length = readULEB128(buffer);
        byte[] stringBytes = new byte[length];
        buffer.get(stringBytes);
        return new String(stringBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<OsuReplay.KeyFrame> parseReplayFrames(ByteBuffer buffer) throws Exception {
        int compressedDataLength = buffer.getInt();

        byte[] compressedBytes = new byte[compressedDataLength];
        buffer.get(compressedBytes);

        ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);

        try (LZMACompressorInputStream lzmaIn = new LZMACompressorInputStream(bais);
             BufferedReader reader = new BufferedReader(new InputStreamReader(lzmaIn, StandardCharsets.UTF_8))) {

            String replayDataString = reader.readLine();

            if (replayDataString != null) {
                return analyzeFrames(replayDataString);
            }
        }

        return null;
    }

    private static List<OsuReplay.KeyFrame> analyzeFrames(String replayDataString) {
        List<OsuReplay.KeyFrame> keyFrames = new LinkedList<>();

        String[] frames = replayDataString.split(",");

        for (String frame : frames) {
            if (frame.trim().isEmpty()) continue;

            String[] data = frame.split("\\|");

            if (data.length != 4) continue;

            long w = Long.parseLong(data[0]); // Time elapsed since last frame
            float x = Float.parseFloat(data[1]); // Cursor X (0 - 512)
            float y = Float.parseFloat(data[2]); // Cursor Y (0 - 384)
            int z = Integer.parseInt(data[3]); // Keys pressed (Bitwise)

            if (w == -12345) {
                break;
            }

            keyFrames.add(new OsuReplay.KeyFrame(w, x, y, z));
        }

        return keyFrames;
    }

    private static int readULEB128(ByteBuffer buffer) {
        int result = 0;
        int shift = 0;

        while (true) {
            byte b = buffer.get();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }

        return result;
    }
}
