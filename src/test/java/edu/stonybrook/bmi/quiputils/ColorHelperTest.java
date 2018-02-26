package edu.stonybrook.bmi.quiputils;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.awt.Color;

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

}