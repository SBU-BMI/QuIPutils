package edu.stonybrook.bmi.quiputils;

import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;

import static org.junit.Assert.*;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ColorHelperTest {

	ColorHelper helper;
	
	@Before
	public void before(){
		helper = new ColorHelper();
	}

	@Test
    public void test()
    {
        int numAvailColors = helper.getNumAvailColors();
        System.out.println("\nTest: numAvailColors > 0");
        assertTrue (numAvailColors > 0);

        System.out.println("\nTest: color not null");
        assertNotNull(helper.getColor());

        System.out.println("\nTest: color not null");
        assertNotNull(helper.getColor(0));

        Color previous = null;
        Color actual = null;
        System.out.println("\nTest: index corresponds to unique color");
        for (int i = 1; i < (numAvailColors + 1); i++) {
			actual = helper.getColor(i);
            assertNotSame(previous, actual);
            previous = helper.getColor(i);
        }
    }

    @Test
    public void testWrite()
    {
        int w = 200, h = 200;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphic = image.createGraphics();

        try {
            File output = new File("testOut.png");
            draw(graphic, w, h);
            ImageIO.write(image, "png", output);
        } catch(IOException log) {
            log.printStackTrace();
        }
        finally {
            graphic.dispose();
        }
    }

    private void draw(Graphics2D graphic, int w, int h) {

	    // Paint background
        graphic.setColor(new Color(255, 255, 255, 0));
        graphic.fillRect(0, 0, w, h);

        // Set random line color
        graphic.setColor(helper.getColor());
        graphic.setStroke(new BasicStroke(2.0f));

        // Line
        Path2D path = new Path2D.Double();
        path.moveTo(50,50);
        path.lineTo(150,150);

        // Draw
        graphic.draw(path);

    }

}