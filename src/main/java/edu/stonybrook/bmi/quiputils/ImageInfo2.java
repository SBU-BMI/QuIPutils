/*
 * Software by Erich Bremer
 * ALL RIGHTS RESERVED
 */

package edu.stonybrook.bmi.quiputils;

import java.io.IOException;
import java.math.BigDecimal;
import javax.json.Json;
import javax.json.JsonObject;
import loci.formats.FormatException;
import loci.formats.ImageReader;

/**
 *
 * @author Erich Bremer
 */
public class ImageInfo2 {
    public static void main(String[] args) {
        loci.common.DebugTools.setRootLevel("WARN");
        ImageReader reader = new ImageReader();
        //reader = loci.formats.Memoizer(reader);
        String fpath = args[0];
        try {
            reader.setId(fpath);
        } catch (FormatException | IOException ex) {
            System.out.println(ex.toString());
        }
        JsonObject json = Json.createObjectBuilder()
            .add("field_width", Json.createArrayBuilder().add(Json.createObjectBuilder().add("value", BigDecimal.valueOf(reader.getSizeX()))))
            .add("field_height", Json.createArrayBuilder().add(Json.createObjectBuilder().add("value", BigDecimal.valueOf(reader.getSizeY()))))
            .add("type", Json.createArrayBuilder().add(Json.createObjectBuilder().add("target_id", "wsi"))).build();
        System.out.println(json.toString());
    }
}
