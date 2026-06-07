package xyz.zcraft.ostella;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Util {
    public static Path getRes(String file) throws URISyntaxException {
        URL beatmapUrl = Util.class.getClassLoader().getResource(file);
        assertNotNull(beatmapUrl, "Test file not found!");

        return Path.of(beatmapUrl.toURI());
    }
}
