package edu.stonybrook.bmi.quiputils;

import org.junit.Before;
import org.junit.Test;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class CustomTilerTest {

    CustomTiler tiler;
    String src;
    String dest;
    String name;
    int tileSizeX;
    int tileSizeY;

    @Before
    public void setUp() {
        src = "D:\\validate\\results\\";
        dest = "D:\\rock\\";
        name = "";
        tileSizeX = 0;
        tileSizeY = 0;
        tiler = new CustomTiler(src, dest, name, tileSizeX, tileSizeY);
    }

    //Uncomment to fail on purpose:
    //@Test(expected = NoSuchFileException.class)
    public void missingInput() {
        final Path inputPath = Paths.get(src);
        final Path outputPath = Paths.get(dest);
    }

    //Uncomment to fail on purpose:
    //@Test
    public void setTileSize() {
        assert (tileSizeX > 0 && tileSizeY > 0);
    }
}