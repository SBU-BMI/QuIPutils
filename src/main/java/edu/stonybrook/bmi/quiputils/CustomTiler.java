/*
 * Software by Erich Bremer
 * ALL RIGHTS RESERVED
 */

package edu.stonybrook.bmi.quiputils;

//import com.googlecode.pngtastic.core.PngImage;
//import com.googlecode.pngtastic.core.PngOptimizer;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.gui.AWTImageTools;
import loci.formats.gui.BufferedImageWriter;
import loci.formats.gui.SignedColorModel;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import ome.xml.model.primitives.PositiveInteger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bson.Document;
import java.awt.BasicStroke;

public class CustomTiler {

    private ImageReader reader;
    private TiffWriter writer;
    //private BufferedImageWriter biwriter;
    private String inputFile;
    private String outputFile;
    private int tileSizeX = 256;
    private int tileSizeY = 256;
    private int width;
    private int height;
    private MongoClient client;
    private MongoDatabase database;
    private MongoClientOptions settings;
    private IMetadata omexml;
    private static Semaphore sema;
    private final long start;
    private String path = "";
    private String src = "";
    private String dest = "";
    private String name = "";
    private int numthreads = Runtime.getRuntime().availableProcessors();
    private static String fileSep = System.getProperty("file.separator");
    //private static String userHome = System.getProperty("user.home");
    private static Color lineColor;
    private static String svsFilePath;
    private static Stroke stroke = new BasicStroke(3.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.0f);

    public CustomTiler(String src, String dest, String name, int tileSizeX, int tileSizeY) {
        this.dest = dest;
        this.src = src;
        this.name = name;
        this.tileSizeX = tileSizeX;
        this.tileSizeY = tileSizeY;
        sema = new Semaphore(numthreads);
        start = System.nanoTime();
    }

    public CustomTiler(String path, String src, String inputFile, String outputFile, int tileSizeX, int tileSizeY) {
        sema = new Semaphore((int) Runtime.getRuntime().availableProcessors());
        start = System.nanoTime();
        this.path = path;
        this.src = src;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.tileSizeX = tileSizeX;
        this.tileSizeY = tileSizeY;
        this.settings = (MongoClientOptions.builder().codecRegistry(com.mongodb.MongoClient.getDefaultCodecRegistry()).build());
        this.client = new MongoClient(new ServerAddress("quip3.bmi.stonybrook.edu:27017"), settings);
        this.database = client.getDatabase("quip");
    }

    private void initialize() throws DependencyException, FormatException, IOException, ServiceException {

        OMEXMLService service = initOMEXMLService();
        omexml = service.createOMEXMLMetadata();
        reader = new ImageReader();
        reader.setMetadataStore(omexml);
        reader.setId(inputFile);
        writer = new TiffWriter();
        MetadataStore outta = service.createOMEXMLMetadata();
        for (int series = 0; series < reader.getSeriesCount(); series++) {
            reader.setSeries(series);
            System.out.println(reader.getSizeX() + " " + reader.getSizeY());
            MetadataTools.populateMetadata(outta, series, "overlay", false, "XYZCT", FormatTools.getPixelTypeString(FormatTools.UINT8), reader.getSizeX(), reader.getSizeY(), 1, 4, 1, 4);
        }
        outta.setPixelsSizeC(new PositiveInteger(4), 0);
        //writer.setMetadataRetrieve((MetadataRetrieve) outta);
        writer.setMetadataRetrieve(omexml);
        writer.setInterleaved(true);
        writer.setBigTiff(false);
        SignedColorModel sc = new SignedColorModel(8, DataBuffer.TYPE_USHORT, 4);
        writer.setColorModel(sc);
        this.tileSizeX = writer.setTileSizeX(tileSizeX);
        this.tileSizeY = writer.setTileSizeY(tileSizeY);
        writer.setId(outputFile);
        writer.setCompression("LZW");
        System.out.println(writer.getCompression());
        //biwriter = new BufferedImageWriter(writer);
        reader.setSeries(0);
        //width = reader.getSizeX();
        //height = reader.getSizeY();
    }

    public void readWriteALLTiles() throws FormatException, IOException, DependencyException, ServiceException, InterruptedException {
        MongoCollection<Document> collection = database.getCollection("objects");
        for (int series = 0; series < reader.getSeriesCount(); series++) {
            //for (int series=0; series<1; series++) {
            reader.setSeries(series);
            writer.setSeries(series);
            for (int image = 0; image < reader.getImageCount(); image++) {
                System.out.println("series: " + series + " image: " + reader.getImageCount());
                //for (int image=0; image<1; image++) {
                int width = reader.getSizeX();
                int height = reader.getSizeY();
                int nXTiles = width / tileSizeX;
                int nYTiles = height / tileSizeY;
                if (nXTiles * tileSizeX != width) nXTiles++;
                if (nYTiles * tileSizeY != height) nYTiles++;
                for (int y = 0; y < nYTiles; y++) {
                    System.out.println(y);
                    for (int x = 0; x < nXTiles; x++) {
                        //System.out.println(x+" : "+y);
                        int tileX = x * tileSizeX;
                        int tileY = y * tileSizeY;
                        int effTileSizeX = (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
                        int effTileSizeY = (tileY + tileSizeY) < height ? tileSizeY : height - tileY;
                    /*
                    double startx = (double) tileX/reader.getSizeX();
                    double endx = (double) (tileX+effTileSizeX)/reader.getSizeX();
                    double starty = (double) tileY/reader.getSizeY();
                    double endy = (double) (tileY+effTileSizeY)/reader.getSizeY();
                    
                    BasicDBObject ebquery = new BasicDBObject();
                    List<BasicDBObject> andQuery = new ArrayList<>();
                    andQuery.add(new BasicDBObject("provenance.image.case_id", "PC_052_0_1"));
                    andQuery.add(new BasicDBObject("provenance.analysis.execution_id", "wsi:r0.8:w0.8:l3:u200:k20:j0"));
                    andQuery.add(new BasicDBObject("x", new BasicDBObject("$gte", startx)));
                    andQuery.add(new BasicDBObject("x", new BasicDBObject("$lte", endx)));
                    andQuery.add(new BasicDBObject("y", new BasicDBObject("$gte", starty)));
                    andQuery.add(new BasicDBObject("y", new BasicDBObject("$lte", endy)));
                    ebquery.put("$and", andQuery); 
                    MongoCursor<Document> cursor = collection.find(ebquery).iterator();
                    */


                        int bpp = FormatTools.getBytesPerPixel(reader.getPixelType());
                        int tilePlaneSize = tileSizeX * tileSizeY * reader.getRGBChannelCount() * bpp;
                        byte[] buf = new byte[tilePlaneSize];
                        buf = reader.openBytes(image, tileX, tileY, effTileSizeX, effTileSizeY);
                        IMetadata meta = (IMetadata) writer.getMetadataRetrieve();
                        meta.setPixelsSizeX(new PositiveInteger(effTileSizeX), 0);
                        meta.setPixelsSizeY(new PositiveInteger(effTileSizeY), 0);
                        BufferedImage boom = AWTImageTools.makeImage(buf, false, meta, 0);
                    
                    
                    /*
                    
                    //BufferedImage boom = new BufferedImage(effTileSizeX, effTileSizeY, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = boom.createGraphics();
                    //g.setColor(new Color(0,255,0,0));
                    g.setColor(new Color(255,255,255,0));
                    g.fillRect(0, 0, effTileSizeX, effTileSizeY);
                    g.setColor(new Color(0,0,255,255));
                    Path2D path;
                    while(cursor.hasNext()) {
                        //System.out.println("=========================================");
                        Document d = (Document) cursor.next();
                        //System.out.println(d.toJson(new JsonWriterSettings(JsonMode.SHELL, true)));
                        //String wow = (String) d.get("geometry");
                        Document geo = (Document) d.get("geometry");
                        ArrayList wow = (ArrayList) geo.get("coordinates");
                        ArrayList sub = (ArrayList) wow.get(0);
                        ArrayList initpair = (ArrayList) sub.get(0);
                        double ida = (double) initpair.get(0);
                        double idb = (double) initpair.get(1);
                        int ia = (int) ((ida-startx)*reader.getSizeX());
                        int ib = (int) ((idb-starty)*reader.getSizeY());
                        path = new Path2D.Double();
                        path.moveTo(ia,ib);
                        for (int c=0; c<sub.size();c++) {
                            ArrayList pair = (ArrayList) sub.get(c);
                            //System.out.println(">> "+pair.get(0));
                            //System.out.println(">> "+pair.get(1));
                            double da = (double) pair.get(0);
                            double db = (double) pair.get(1);
                            int a = (int) ((da-startx)*reader.getSizeX());
                            int b = (int) ((db-starty)*reader.getSizeY());
                            //System.out.println("coord : ["+a+","+b+"]");
                            path.lineTo(a,b);
                            g.draw(path);
                        }
                    }
                    */
                        // TODO: replace D: with userHome
                        //ImageIO.write(boom,"png",new BufferedOutputStream(new FileOutputStream(new File("d:\\booga\\saved-"+series+"-"+x+"-"+y+".png"))));
                        String ffn = "d:\\booga\\saved-" + series + "-" + x + "-" + y + ".png";
                        sema.acquire();
                        (new Thread() {
                            public void run() {
                                try {
                                    //ImageIO.write(boom,"png",new BufferedOutputStream(new FileOutputStream(new File(ffn))));
                                    ImageIO.write(boom, "png", new File(ffn));
                                } catch (IOException ex) {

                                } finally {
                                    sema.release();
                                }
                            }
                        }).start();
                    /*
                    if (ffn.length()>4000) {
                        PngImage pi = new PngImage(fn,"none");
                        // TODO: replace D: with userHome
                        String ofn = "d:\\booga\\min-"+series+"-"+x+"-"+y+".png";
                        PngOptimizer optimizer = new PngOptimizer("none");
                        optimizer.setCompressor("zopfli", 1);
                        optimizer.optimize(pi, ofn, false, null);
                        File output = new File(ofn);
                        double per = 100.0d*(1.0d-(double) output.length()/ (double) ffn.length());
                        if (per>0) {
                            System.out.println("compression: "+per+"%original : "+ffn.length()+" optimized: "+output.length()+" file : "+ofn);
                        }
                    } 
                    */
                        //System.out.println("===> "+tileX+" "+tileY+" "+effTileSizeX+" "+effTileSizeY);
                        //biwriter.saveBytes(image, buf, tileX, tileY, effTileSizeX, effTileSizeY);
                        //biwriter.saveImage(image, boom, tileX, tileY, effTileSizeX, effTileSizeY);
                        //biwriter.saveImage(image, buf, tileX, tileY, effTileSizeX, effTileSizeY);
                    }
                    double delta = (double) System.nanoTime() - start;
                    delta = delta / 1000000000d;
                    System.out.println("Time : " + String.valueOf(delta) + " seconds.");
                }
            }
        }
    }

    public void readWriteTiles() throws FormatException, IOException, DependencyException, ServiceException, InterruptedException {
        MongoCollection<Document> collection = database.getCollection("objects");
        //for (int series==0; series<reader.getSeriesCount(); series++) {
        long maxx = Long.MIN_VALUE;
        long maxy = Long.MIN_VALUE;
        long minx = Long.MAX_VALUE;
        long miny = Long.MAX_VALUE;
        long numpoly = 0;
        long numbad = 0;
        for (int series = 0; series < 1; series++) {
            reader.setSeries(series);
            //writer.setSeries(series);
            for (int image = 0; image < reader.getImageCount(); image++) {
                System.out.println("series: " + series + " image: " + reader.getImageCount());
                //for (int image=0; image<1; image++) {
                int width = reader.getSizeX();
                int height = reader.getSizeY();
                int nXTiles = width / tileSizeX;
                int nYTiles = height / tileSizeY;
                if (nXTiles * tileSizeX != width) nXTiles++;
                if (nYTiles * tileSizeY != height) nYTiles++;
                for (int y = 0; y < nYTiles; y++) {
                    System.out.println(y);
                    for (int x = 0; x < nXTiles; x++) {
                        //System.out.println(x+" : "+y);
                        int tileX = x * tileSizeX;
                        int tileY = y * tileSizeY;
                        int effTileSizeX = (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
                        int effTileSizeY = (tileY + tileSizeY) < height ? tileSizeY : height - tileY;

                        double startx = (double) tileX / reader.getSizeX();
                        double endx = (double) (tileX + effTileSizeX) / reader.getSizeX();
                        double starty = (double) tileY / reader.getSizeY();
                        double endy = (double) (tileY + effTileSizeY) / reader.getSizeY();
                        double TS = (double) tileSizeX / reader.getSizeX();
                        //System.out.println(startx+":"+endx+"  "+starty+":"+endy+" "+reader.getSizeX()+"  "+reader.getSizeY());

                        BasicDBObject ebquery = new BasicDBObject();
                        List<BasicDBObject> andQuery = new ArrayList<>();
                        andQuery.add(new BasicDBObject("provenance.image.case_id", src));
                        andQuery.add(new BasicDBObject("provenance.analysis.execution_id", "wsi:r0.8:w0.8:l3:u200:k20:j0"));
                        //andQuery.add(new BasicDBObject("provenance.analysis.execution_id", "human_markup_composite_dataset"));
                        andQuery.add(new BasicDBObject("x", new BasicDBObject("$gte", startx - TS)));
                        andQuery.add(new BasicDBObject("x", new BasicDBObject("$lte", endx + TS)));
                        andQuery.add(new BasicDBObject("y", new BasicDBObject("$gte", starty - TS)));
                        andQuery.add(new BasicDBObject("y", new BasicDBObject("$lte", endy + TS)));
                        ebquery.put("$and", andQuery);
                        MongoCursor<Document> cursor = collection.find(ebquery).iterator();
                        //System.out.println("has next : "+cursor.hasNext());
                        //System.out.println("Coordinates=============================");

                        //int bpp = FormatTools.getBytesPerPixel(reader.getPixelType());
                        //int tilePlaneSize = tileSizeX * tileSizeY * reader.getRGBChannelCount() * bpp;
                        //byte[] buf = new byte[tilePlaneSize];
                        //buf = reader.openBytes(image, tileX, tileY, effTileSizeX, effTileSizeY);
                        //writer.saveBytes(image, buf, tileX, tileY, effTileSizeX, effTileSizeY);
                        //IMetadata meta = (IMetadata) writer.getMetadataRetrieve();
                        //meta.setPixelsSizeX(new PositiveInteger(effTileSizeX), 0);
                        //meta.setPixelsSizeY(new PositiveInteger(effTileSizeY), 0);
                        //byte[] buf2 = buf.clone();
                        //BufferedImage boom = AWTImageTools.makeImage(buf2, false, meta, 0);

                        BufferedImage boom = new BufferedImage(effTileSizeX, effTileSizeY, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = boom.createGraphics();
                        //g.setStroke(stroke);
                        g.setColor(new Color(255, 255, 255, 0));
                        g.fillRect(0, 0, effTileSizeX, effTileSizeY);
                        g.setColor(lineColor);
                        Path2D p;
                        boolean bad = false;
                        while (cursor.hasNext()) {
                            numpoly++;
                            g.setColor(lineColor);
                            Document d = (Document) cursor.next();
                            //System.out.println(d.toJson(new JsonWriterSettings(JsonMode.SHELL, true)));
                            //String wow = (String) d.get("geometry");
                            Document geo = (Document) d.get("geometry");
                            ArrayList wow = (ArrayList) geo.get("coordinates");
                            ArrayList sub = (ArrayList) wow.get(0);
                            ArrayList initpair = (ArrayList) sub.get(0);
                            double ida = (double) initpair.get(0);
                            double idb = (double) initpair.get(1);

                            int ia = (int) ((ida - startx) * reader.getSizeX());
                            int ib = (int) ((idb - starty) * reader.getSizeY());
                            if (ia > maxx) {
                                maxx = ia;
                                //System.out.println(geo.get("coordinates").toString());
                                g.setColor(lineColor);
                                bad = true;
                            }
                            if (ia < minx) {
                                minx = ia;
                                //System.out.println(geo.get("coordinates").toString());
                                g.setColor(lineColor);
                                bad = true;
                            }
                            if (ib > maxy) {
                                maxy = ib;
                                //System.out.println(geo.get("coordinates").toString());
                                g.setColor(lineColor);
                                bad = true;
                            }
                            if (ib < miny) {
                                miny = ib;
                                //System.out.println(geo.get("coordinates").toString());
                                g.setColor(lineColor);
                                bad = true;
                            }
                            p = new Path2D.Double();
                            p.moveTo(ia, ib);
                            for (int c = 0; c < sub.size(); c++) {
                                ArrayList pair = (ArrayList) sub.get(c);
                                //System.out.println(">> "+pair.get(0));
                                //System.out.println(">> "+pair.get(1));
                                double da = (double) pair.get(0);
                                double db = (double) pair.get(1);
                                int a = (int) ((da - startx) * reader.getSizeX());
                                int b = (int) ((db - starty) * reader.getSizeY());
                                //System.out.println("coord : ["+a+","+b+"]");
                                p.lineTo(a, b);
                            }
                            p.lineTo(ia, ib);
                            g.draw(p);
                        }
                        cursor.close();
                        if (bad) numbad++;
                        String ffn = path + fileSep + src + fileSep + src + "-" + series + "-" + x + "-" + y + ".png";
                        sema.acquire();
                        (new Thread() {
                            public void run() {
                                try {
                                    //ImageIO.write(boom,"png",new BufferedOutputStream(new FileOutputStream(new File(ffn))));
                                    ImageIO.write(boom, "png", new File(ffn));
                                    boom.flush();
                                } catch (IOException ex) {

                                } finally {
                                    sema.release();
                                }
                            }
                        }).start();
                    
                    
                    
                    /*
                    // TODO: replace D: with userHome
                    String fn = "d:\\booga\\saved-"+series+"-"+x+"-"+y+".png";
                    File ffn = new File(fn);
                    ImageIO.write( boom, "png",ffn);
                    if (ffn.length()>4000) {
                        PngImage pi = new PngImage(fn,"none");
                        String ofn = "d:\\booga\\min-"+series+"-"+x+"-"+y+".png";
                        PngOptimizer optimizer = new PngOptimizer("none");
                        //optimizer.setCompressor("zopfli", 1);
                        optimizer.optimize(pi, ofn, false, null);
                        File output = new File(ofn);
                        double per = 100.0d*(1.0d-(double) output.length()/ (double) ffn.length());
                        if (per>0) {
                            System.out.println("compression: "+per+"%original : "+ffn.length()+" optimized: "+output.length()+" file : "+ofn);
                        }
                    }     */
                        //biwriter.saveImage(image, boom, tileX, tileY, effTileSizeX, effTileSizeY);
                    }
                    //double delta = (double) System.nanoTime() - start;
                    //delta = delta / 1000000000d;
                    //System.out.println("Time : "+String.valueOf(delta));
                    //System.out.println("maxx : "+maxx+" minx : "+minx+" maxy : "+maxy+" miny : "+miny);
                    //System.out.println("number of polygons : "+numpoly+"  numbad : "+numbad+"  numgood : "+(numpoly-numbad));
                }
            }
        }
    }

    public static BufferedImage scale(BufferedImage sbi, double scale) {
        BufferedImage dbi = null;
        if (sbi != null) {
            dbi = new BufferedImage((int) (sbi.getWidth() * scale), (int) (sbi.getHeight() * scale), sbi.getType());
            Graphics2D g = dbi.createGraphics();
            AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
            g.drawRenderedImage(sbi, at);
        }
        return dbi;
    }

    public BufferedImage VStack(BufferedImage image1, BufferedImage image2) {
        BufferedImage combined = new BufferedImage(image1.getWidth(), image1.getHeight() + image2.getHeight(), image1.getType());
        Graphics2D g = (Graphics2D) combined.getGraphics();
        g.drawImage(image1, 0, 0, null);
        g.drawImage(image2, 0, image1.getHeight(), null);
        return combined;
    }

    public BufferedImage HStack(BufferedImage image1, BufferedImage image2) {
        BufferedImage combined = new BufferedImage(image1.getWidth() + image2.getWidth(), image1.getHeight(), image1.getType());
        Graphics2D g = (Graphics2D) combined.getGraphics();
        g.drawImage(image1, 0, 0, null);
        g.drawImage(image2, image1.getWidth(), 0, null);
        return combined;
    }

    public BufferedImage QuadStack(BufferedImage image1, BufferedImage image2, BufferedImage image3, BufferedImage image4) {
        BufferedImage combined = new BufferedImage(image1.getWidth() + image2.getWidth(), image1.getHeight() + image3.getHeight(), image1.getType());
        Graphics2D g = (Graphics2D) combined.getGraphics();
        g.drawImage(image1, 0, 0, null);
        g.drawImage(image2, image1.getWidth(), 0, null);
        g.drawImage(image3, 0, image1.getHeight(), null);
        g.drawImage(image4, image1.getWidth(), image1.getHeight(), null);
        return combined;
    }

    public File TileFile(int series, int x, int y) {
        return new File(path + fileSep + src + fileSep + src + "-" + series + "-" + x + "-" + y + ".png");
    }

    public void GeneratePyramidTiles2() throws DependencyException, ServiceException, IOException {

        OMEXMLService service = initOMEXMLService();
        MetadataStore outta = service.createOMEXMLMetadata();
        for (int series = 0; series < reader.getSeriesCount(); series++) {
            reader.setSeries(series);
            System.out.println(reader.getSizeX() + " " + reader.getSizeY());
            MetadataTools.populateMetadata(outta, series, "overlay", false, "XYZCT", FormatTools.getPixelTypeString(FormatTools.UINT8), reader.getSizeX(), reader.getSizeY(), 1, 4, 1, 4);
        }
        //reader.setSeries(0);
        for (int series = 0; series < reader.getSeriesCount(); series++) {
            reader.setSeries(series);
            if (reader.getImageCount() > 1) {
                System.out.println("UNEXPECTED NUMBER OF IMAGES!!!! " + reader.getImageCount());
            }
            for (int image = 0; image < reader.getImageCount(); image++) {
                int width = (int) (reader.getSizeX() / Math.pow(2.0, series));
                int height = (int) (reader.getSizeY() / Math.pow(2.0, series));
                int nXTiles = width / tileSizeX;
                int nYTiles = height / tileSizeY;
                if (nXTiles * tileSizeX != width) nXTiles++;
                if (nYTiles * tileSizeY != height) nYTiles++;
                for (int y = 0; y < nYTiles; y = y + 2) {
                    System.out.println(y);
                    for (int x = 0; x < nXTiles; x = x + 2) {
                        //System.out.println(x+" : "+y);
                        File image1 = TileFile(series, x, y);
                        File image2 = TileFile(series, x + 1, y);
                        File image3 = TileFile(series, x, y + 1);
                        File image4 = TileFile(series, x + 1, y + 1);
                        BufferedImage boom = ImageIO.read(image1);
                        if (image2.exists() && image3.exists() && image4.exists()) {
                            boom = QuadStack(boom, ImageIO.read(image2), ImageIO.read(image3), ImageIO.read(image4));
                        } else if (!image2.exists() && image3.exists() && !image4.exists()) {
                            boom = VStack(boom, ImageIO.read(image3));
                        } else if (image2.exists() && !image3.exists() && !image4.exists()) {
                            boom = HStack(boom, ImageIO.read(image2));
                        } else {
                            if (!image1.exists()) {
                                System.out.println("There is a major problem is our local Universe!!!!");
                                System.out.println("image1: " + image1.exists());
                                System.out.println("image2: " + image2.exists());
                                System.out.println("image3: " + image3.exists());
                                System.out.println("image4: " + image4.exists());
                            }
                        }
                        File newfile = TileFile(series + 1, x / 2, y / 2);
                        if (!newfile.exists()) {
                            ImageIO.write(scale(boom, 0.5), "png", newfile);
                            boom.flush();
                        } else {
                            System.out.println("OUTPUT FILE ALREADY EXISTS.  CHECK YOUR CODE!!!");
                        }
                    }
                }
            }
        }
    }

    public void GeneratePyramidTiles() throws FormatException, IOException, DependencyException, ServiceException {
        reader.setSeries(0);
        //for (int series=0; series<reader.getSeriesCount()-1; series++) {
        for (int series = 0; series < 6; series++) {
            if (reader.getImageCount() > 1) {
                System.out.println("UNEXPECTED NUMBER OF IMAGES!!!! " + reader.getImageCount());
            }
            for (int image = 0; image < reader.getImageCount(); image++) {
                int width = (int) (reader.getSizeX() / Math.pow(2.0, series));
                int height = (int) (reader.getSizeY() / Math.pow(2.0, series));
                int nXTiles = width / tileSizeX;
                int nYTiles = height / tileSizeY;
                if (nXTiles * tileSizeX != width) nXTiles++;
                if (nYTiles * tileSizeY != height) nYTiles++;
                for (int y = 0; y < nYTiles; y = y + 2) {
                    System.out.println(y);
                    for (int x = 0; x < nXTiles; x = x + 2) {
                        //System.out.println(x+" : "+y);
                        File image1 = TileFile(series, x, y);
                        File image2 = TileFile(series, x + 1, y);
                        File image3 = TileFile(series, x, y + 1);
                        File image4 = TileFile(series, x + 1, y + 1);
                        BufferedImage boom = ImageIO.read(image1);
                        if (image2.exists() && image3.exists() && image4.exists()) {
                            boom = QuadStack(boom, ImageIO.read(image2), ImageIO.read(image3), ImageIO.read(image4));
                        } else if (!image2.exists() && image3.exists() && !image4.exists()) {
                            boom = VStack(boom, ImageIO.read(image3));
                        } else if (image2.exists() && !image3.exists() && !image4.exists()) {
                            boom = HStack(boom, ImageIO.read(image2));
                        } else {
                            if (!image1.exists()) {
                                System.out.println("There is a major problem is our local Universe!!!!");
                                System.out.println("image1: " + image1.exists());
                                System.out.println("image2: " + image2.exists());
                                System.out.println("image3: " + image3.exists());
                                System.out.println("image4: " + image4.exists());
                            }
                        }
                        File newfile = TileFile(series + 1, x / 2, y / 2);
                        if (!newfile.exists()) {
                            ImageIO.write(scale(boom, 0.5), "png", newfile);
                            boom.flush();
                        } else {
                            System.out.println("OUTPUT FILE ALREADY EXISTS.  CHECK YOUR CODE!!!");
                        }
                    }
                }
            }
        }
    }

    public void iCheck(File file, int tx, int ty) throws IOException {
        if (!file.exists()) {
            //System.out.println(file.toString()+" "+tx+" "+ty);
            BufferedImage boom = new BufferedImage(tx, ty, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = boom.createGraphics();
            //g.setStroke(stroke);
            g.setColor(new Color(255, 255, 255, 0));
            //g.setColor(new Color(255,83,21,255));
            g.fillRect(0, 0, tx, ty);
            ImageIO.write(boom, "png", file);
        }
    }

    public void GeneratePyramidTiles3() throws FormatException, IOException, DependencyException, ServiceException, InterruptedException {
        for (int series = 0; series < 6; series++) {
            while (sema.availablePermits() != numthreads) {
                // wait until prior resolution finished since next scale depends on it
            }
            int width = (int) (this.width / Math.pow(2.0, series));
            int height = (int) (this.height / Math.pow(2.0, series));
            int nXTiles = width / tileSizeX;
            int nYTiles = height / tileSizeY;
            if (nXTiles * tileSizeX != width) nXTiles++;
            if (nYTiles * tileSizeY != height) nYTiles++;
            for (int y = 0; y < nYTiles; y = y + 2) {
                System.out.println(y);
                for (int x = 0; x < nXTiles; x = x + 2) {
                    File image1 = new File(dest + fileSep + name + "-" + series + "-" + x + "-" + y + ".png");
                    File image2 = new File(dest + fileSep + name + "-" + series + "-" + ((int) (x + 1)) + "-" + y + ".png");
                    File image3 = new File(dest + fileSep + name + "-" + series + "-" + x + "-" + ((int) (y + 1)) + ".png");
                    File image4 = new File(dest + fileSep + name + "-" + series + "-" + ((int) (x + 1)) + "-" + ((int) (y + 1)) + ".png");
                    try {
                        iCheck(image1, tileSizeX, tileSizeY);
                        if (x + 1 < nXTiles) {
                            iCheck(image2, tileSizeX, tileSizeY);
                        }
                        if (y + 1 < nYTiles) {
                            iCheck(image3, tileSizeX, tileSizeY);
                        }
                        if ((x + 1 < nXTiles) && (y + 1 < nYTiles)) {
                            iCheck(image4, tileSizeX, tileSizeY);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    sema.acquire();
                    new PolyThread2(series, x, y).start();
                }
            }
        }
    }

    private void cleanup() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            System.err.println("Failed to close reader.");
        }

        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("Failed to close writer.");
        }
    }


    public void mongotiler() throws IOException, DependencyException, FormatException, ServiceException, InterruptedException {
        int tileSizeX = 256;
        int tileSizeY = 256;
        // TODO: replace D: with userHome
        String path = "d:\\validate\\";
        //String src = "BC_201_1_1";
        String src = "17039889";
        CustomTiler overlappedTiledWriter = new CustomTiler(path, src, path + src + ".svs", path + src + ".tif", tileSizeX, tileSizeY);
        overlappedTiledWriter.initialize();
        try {
            overlappedTiledWriter.readWriteTiles();
            overlappedTiledWriter.GeneratePyramidTiles();
        } catch (DependencyException | ServiceException e) {
            System.err.println("Failed to read and write tiles.");
            throw e;
        } finally {
            overlappedTiledWriter.cleanup();
        }
    }

    public class PolyThread2 extends Thread {
        int series;
        int x;
        int y;

        PolyThread2(int series, int x, int y) {
            this.series = series;
            this.x = x;
            this.y = y;
        }

        public void run() {
            //System.out.println("executing thread..."+x+" "+y);
            //System.out.println(dest+fileSep+name+"-"+series+"-"+x+"-"+y+".png");
            File image1 = new File(dest + fileSep + name + "-" + series + "-" + x + "-" + y + ".png");
            File image2 = new File(dest + fileSep + name + "-" + series + "-" + ((int) (x + 1)) + "-" + y + ".png");
            File image3 = new File(dest + fileSep + name + "-" + series + "-" + x + "-" + ((int) (y + 1)) + ".png");
            File image4 = new File(dest + fileSep + name + "-" + series + "-" + ((int) (x + 1)) + "-" + ((int) (y + 1)) + ".png");
            BufferedImage boom = null;
            try {
                boom = ImageIO.read(image1);
                if (image2.exists() && image3.exists() && image4.exists()) {
                    boom = QuadStack(boom, ImageIO.read(image2), ImageIO.read(image3), ImageIO.read(image4));
                } else if (!image2.exists() && image3.exists() && !image4.exists()) {
                    boom = VStack(boom, ImageIO.read(image3));
                } else if (image2.exists() && !image3.exists() && !image4.exists()) {
                    boom = HStack(boom, ImageIO.read(image2));
                }
            } catch (IOException | java.lang.IndexOutOfBoundsException ex) {
                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println("image1 " + image1.toString());
                System.out.println("image2 " + image2.toString());
                System.out.println("image3 " + image3.toString());
                System.out.println("image4 " + image4.toString());
            }
            int next = series + 1;
            File newfile = new File(dest + fileSep + name + "-" + next + "-" + ((int) (x / 2)) + "-" + ((int) (y / 2)) + ".png");
            if (!newfile.exists()) {
                newfile.delete();
            }
            try {
                ImageIO.write(scale(boom, 0.5), "png", newfile);
            } catch (IOException | java.lang.IllegalArgumentException ex) {
                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
            }
            boom.flush();
            sema.release();
            return;
        }

    }

    public class PolyThread extends Thread {
        Path file;

        PolyThread(Path file) {
            this.file = file;
        }

        public void run() {
            //System.out.println("executing thread..."+file);
            //int height = 0;
            //int width = 0;
            //OMEXMLService service = initOMEXMLService();

            //ImageReader oreader = new ImageReader();
            //IMetadata omexml = null;
            //try {
//                omexml = service.createOMEXMLMetadata();
//            } catch (ServiceException ex) {
//                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            oreader.setMetadataStore(omexml);
//            try {
//                oreader.setId(getSvsFilePath());
//            } catch (FormatException ex) {
//                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
//            } catch (IOException ex) {
//                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
//            }
  //          int bpp = FormatTools.getBytesPerPixel(oreader.getPixelType());
            int tx = 0;
            int ty = 0;
            int tileminx = 0;
            int tileminy = 0;
            Reader reader = null;
            List<CSVRecord> records = null;
            CSVParser parser = null;
            try {
                reader = Files.newBufferedReader(file);
                parser = getCsvParser(reader, true);
                records = parser.getRecords();
            } catch (IOException ex) {
                error("**CSVRecord**", ex, true);
            } finally {
                try {
                    if (parser != null) {
                        parser.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            width = Integer.parseInt(records.get(0).get("image_width"));
            height = Integer.parseInt(records.get(0).get("image_height"));

            tx = Integer.parseInt(records.get(0).get("tile_width"));
            ty = Integer.parseInt(records.get(0).get("tile_height"));
            tileminx = Integer.parseInt(records.get(0).get("tile_minx"));
            tileminy = Integer.parseInt(records.get(0).get("tile_miny"));
            //System.out.println(tx+" "+ty);
/*
            int tilePlaneSize = tileSizeX * tileSizeY * oreader.getRGBChannelCount() * bpp;
            byte[] buf = new byte[tilePlaneSize];
            try {
                buf = oreader.openBytes(0, tileminx, tileminy, tx, ty);
            } catch (FormatException ex) {
                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                // We're done with the reader. Close it.
                try {
                    if (oreader != null) {
                        oreader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            omexml.setPixelsSizeX(new PositiveInteger(tx), 0);
            omexml.setPixelsSizeY(new PositiveInteger(ty), 0);
            BufferedImage boom = null;
            try {
                boom = AWTImageTools.makeImage(buf, false, omexml, 0);
            } catch (FormatException ex) {
                Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, null, ex);
            }
*/
            BufferedImage boom = new BufferedImage(tx, ty, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = boom.createGraphics();
            g.setColor(new Color(255, 255, 255, 0));
            g.fillRect(0, 0, tx, ty);
            g.setColor(lineColor);
            //Path2D path = new Path2D.Double();
            g.setStroke(stroke);

            String polys = file.toString();
            polys = polys.substring(0, polys.length() - 12) + "-features.csv";
            Reader reader2;
            CSVParser polyparser;
            try {
                reader2 = Files.newBufferedReader(Paths.get(polys));
                polyparser = getCsvParser(reader2, false);
                records = polyparser.getRecords();
            } catch (IOException ex) {
                error("COULD NOT FIND: *-features.csv", ex, true);
            }
            try {

                if (records.size() > 1) {
                    for (int i = 1; i < records.size(); i++) {
                        CSVRecord record = records.get(i);
                        int recordSize = record.size();
                        String poly = record.get(recordSize - 1);
                        poly = poly.substring(1, poly.length() - 1);
                        String[] coords = poly.split(":");
                        int fia = (int) Math.round(Float.parseFloat(coords[0]));
                        int fib = (int) Math.round(Float.parseFloat(coords[1]));
                        int ia = fia - tileminx;
                        int ib = fib - tileminy;
                        int lastx = ia;
                        int lasty = ib;
                        //path.moveTo(ia, ib);
                        for (int c = 0; c < coords.length; c = c + 2) {
                            int fa = (int) Math.round(Float.parseFloat(coords[c]));
                            int fb = (int) Math.round(Float.parseFloat(coords[c + 1]));
                            int a = fa - tileminx;
                            int b = fb - tileminy;
                            g.drawLine(a, b, lastx, lasty);
                            lastx = a;
                            lasty = b;
                            //path.lineTo(a, b);
                        }
                        //path.lineTo(ia, ib);
                        g.drawLine(ia, ib, lastx, lasty);
                        //g.draw(path);
                    }
                    for (int i = 0; i < tx; i = i + 256)
                        for (int j = 0; j < ty; j = j + 256) {
                            int tilex = (tileminx + i) / 256;
                            int tiley = (tileminy + j) / 256;
                            //System.out.println(i+" "+j);
                            int effx = 256;
                            int effy = 256;
                            if ((i + 256) > tx) {
                                effx = tx - i;
                            }
                            if ((j + 256) > ty) {
                                effy = ty - j;
                            }
                            BufferedImage bb = boom.getSubimage(i, j, effx, effy);
                            try {
                                //System.out.println(src+"  "+);
                                ImageIO.write(bb, "png", new File(dest + fileSep + name + "-0-" + tilex + "-" + tiley + ".png"));
                            } catch (IOException ex) {
                                error("IMAGE-WRITING FAILED", ex, false);
                            }
                            bb.flush();
                        }
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                error("**BOUNDS ISSUE**", ex, false);
            }

            sema.release();
            boom.flush();
            return;


            /*
            int nXTiles = width / tx;
            int nYTiles = height / ty;
            if (nXTiles * tileSizeX != width) nXTiles++;
            if (nYTiles * tileSizeY != height) nYTiles++;
            for (int y=0; y<nYTiles; y++) {
                System.out.println(y);
                for (int x=0; x<nXTiles; x++) {
                    int tileX = x * tileSizeX;
                    int tileY = y * tileSizeY;
                    int effTileSizeX = (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
                    int effTileSizeY = (tileY + tileSizeY) < height ? tileSizeY : height - tileY;
  
                    while(cursor.hasNext()) {
                        double ida = (double) initpair.get(0);
                        double idb = (double) initpair.get(1);
                        int ia = (int) ((ida-startx)*reader.getSizeX());
                        int ib = (int) ((idb-starty)*reader.getSizeY());
                        path = new Path2D.Double();
                        path.moveTo(ia,ib);

                        path.lineTo(ia,ib);
                        g.draw(path);
                    }
                    String ffn = path+fileSep+src+fileSep+src+"-0-"+x+"-"+y+".png";
                    sema.acquire();
                    (new Thread() {
                        public void run() {
                            try {
                                ImageIO.write(boom,"png",new File(ffn));
                                boom.flush();
                            } catch (IOException ex) {
                                
                            } finally {
                                sema.release();
                            }
                        }}).start();

                }
                double delta = (double) System.nanoTime()-start;
                delta = delta / 1000000000d;
            }
                        */

            // } finally {
            //   sema.release();
            //}

        }

    }

    public void cvstiler() throws IOException, InterruptedException {
        String strGlob = "*-algmeta.{csv}";
        DirectoryStream<Path> stream;
        Path dir = Paths.get(src);
        stream = Files.newDirectoryStream(dir, strGlob);
        Reader reader = null;
        List<CSVRecord> records = null;
        Path pp = null;
        try {
            pp = stream.iterator().next();
        } catch (NoSuchElementException e) {
            error("COULD NOT FIND: " + strGlob, e, true);
        }
        try {
            reader = Files.newBufferedReader(pp);
            CSVParser parser = getCsvParser(reader, true);
            records = parser.getRecords();
        } catch (IOException ex) {
            error("**CSVRecord-2**", ex, false);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        width = Integer.parseInt(records.get(0).get("image_width"));
        height = Integer.parseInt(records.get(0).get("image_height"));
        System.out.println(width + " " + height);
        stream = Files.newDirectoryStream(dir, strGlob);
        for (Path p : stream) {
            sema.acquire();
            new PolyThread(p).start();
        }
        stream.close();
        
        try {
            GeneratePyramidTiles3();
        } catch (FormatException | DependencyException | ServiceException ex) {
            error("**GeneratePyramidTiles**", ex, false);
        }
        
        double delta = (double) System.nanoTime() - start;
        delta = delta / 1000000000d;
        System.out.println("Time : " + String.valueOf(delta) + " seconds.");
    }

    public void error(String message, Exception ex, boolean end) {
        System.out.println(this.src + "\n" + dest + "\n" + name + "\t" + tileSizeX + "\t" + tileSizeY);
        Logger.getLogger(CustomTiler.class.getName()).log(Level.SEVERE, message, ex);
        if (end) {
            System.exit(1);
        }
    }

    public static void testPath(String pathToFile) {
        try {
            Files.newDirectoryStream(Paths.get(pathToFile));
        } catch (Exception ex) {
            System.out.println("DIRECTORY DOES NOT EXIST: " + pathToFile);
            System.exit(1);
        }
    }

    private synchronized OMEXMLService initOMEXMLService() {
        OMEXMLService omexmlService;
        try {
            final ServiceFactory serviceFactory = new ServiceFactory();
            omexmlService = serviceFactory.getInstance(loci.formats.services.OMEXMLService.class);
        } catch (final DependencyException exc) {
            throw new IllegalStateException("Cannot access OME-XML service", exc);
        }
        return omexmlService;
    }

    private static CSVParser getCsvParser(Reader reader, boolean hasHeader) throws IOException {
        CSVParser csvParser;

        if (hasHeader) {
            csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim());

        } else {
            csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                    .withSkipHeaderRecord()
                    .withTrim());
        }

        return csvParser;

    }

    private void setColor(int color) {
        this.lineColor = new ColorHelper().getColor(color);
        System.out.println("color: " + this.lineColor);
    }

    public String getSvsFilePath() {
        return svsFilePath;
    }

    public void setSvsFilePath(String svsFilePath) {
        this.svsFilePath = svsFilePath;
    }

    public static void main(String[] args) {
        loci.common.DebugTools.setRootLevel("WARN");

        // NOTE: usage: CustomTiler inputDir, outputDir, svsFilePath, imgName, color [int]
        String inputDir;
        String outputDir;
        String imgName;
        String svsFilePath;
        int color = 0; //default blue

        if (args.length == 0) {
            // Hard-code it here, if you must...
            inputDir = "D:\\tiles\\csv3\\";
            outputDir = "D:\\tiles\\";
            svsFilePath = "D:\\tiles\\raw\\TCGA-44-A47B-01Z-00-DX1.177D0531-E037-435B-BFD4-382B2150B10D.svs";
            imgName = "TCGA-44-A47B-01Z-00-DX1.177D0531-E037-435B-BFD4-382B2150B10D";

        } else {
            inputDir = args[0];
            outputDir = args[1];
            svsFilePath = args[2];
            imgName = args[3];
            color = Integer.parseInt(args[4]);

        }

        System.out.println(imgName);
        System.out.println(color);
        System.out.println("line stroke 2");


        testPath(inputDir);
        testPath(outputDir);

        int tileSizeX = 256, tileSizeY = 256;
        CustomTiler csvtiler = new CustomTiler(inputDir, outputDir, imgName, tileSizeX, tileSizeY);
        csvtiler.setSvsFilePath(svsFilePath);
        csvtiler.setColor(color);
        try {
            csvtiler.cvstiler();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
