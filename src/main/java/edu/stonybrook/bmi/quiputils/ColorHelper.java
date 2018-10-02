package edu.stonybrook.bmi.quiputils;

import java.awt.Color;
import java.util.Random;

public class ColorHelper {

    private final int numAvailColors = 12;

    /**
     * SHOW AVAILABLE COLORS.
     */
    public void showAvailableColors() {
        for (int i = 1; i < (numAvailColors + 1); i++) {
            System.out.println(i + ": " + getColor(i));
        }
    }

    /**
     * GET COLOR.
     *
     * @param num
     * @return
     */
    public Color getColor(int num) {

        Color rtnColor;

        switch (num) {
            case 1:
                // light blue, #a6cee3
                rtnColor = new Color(166, 206, 227);
                break;
            case 2:
                // strong blue, #1f78b4
                rtnColor = new Color(31, 120, 180);
                break;
            case 3:
                // light green, #b2df8a
                rtnColor = new Color(178, 223, 138);
                break;
            case 4:
                // green, #33a02c
                rtnColor = new Color(51, 160, 44);
                break;
            case 5:
                // pink, #fb9a99
                rtnColor = new Color(251, 154, 153);
                break;
            case 6:
                // red, #e31a1c
                rtnColor = new Color(227, 26, 28);
                break;
            case 7:
                // light orange, #fdbf6f
                rtnColor = new Color(253, 191, 111);
                break;
            case 8:
                // orange, #ff7f00
                rtnColor = new Color(255, 127, 0);
                break;
            case 9:
                // light violet, #cab2d6
                rtnColor = new Color(202, 178, 214);
                break;
            case 10:
                // violet, #6a3d9a
                rtnColor = new Color(106, 61, 154);
                break;
            case 11:
                // light yellow, #ffff99
                rtnColor = new Color(255, 255, 153);
                break;
            case 12:
                // sienna, #b15928
                rtnColor = new Color(177, 89, 40);
                break;
            default:
                // blue
                rtnColor = new Color(0, 0, 255);
        }
        return rtnColor;

    }

    /**
     * Random color.
     *
     * @return
     */
    public Color getColor() {

        Random rn = new Random();
        int num = rn.nextInt(numAvailColors) + 1;

        return getColor(num);

    }

    public int getNumAvailColors() {
        return numAvailColors;
    }

}
