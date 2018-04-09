/*
 * Software by Erich Bremer
 * ALL RIGHTS RESERVED
 */

package edu.stonybrook.bmi.quiputils;

import com.adobe.xmp.impl.Base64;
import java.io.IOException;
import java.math.BigDecimal;
import javax.json.Json;
import javax.json.JsonObject;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 *
 * @author Erich Bremer
 */
public class ImageInfo {
        
    public static void main(String[] args) throws DependencyException, ServiceException, FormatException, IOException, Exception {
        loci.common.DebugTools.setRootLevel("WARN");
        ImageReader reader = new ImageReader();
        String fpath = args[1].replace("/system/files/", "/www/private/");
        System.out.println("fpath = "+fpath);
        reader.setId(fpath);
        System.out.println("size : "+reader.getSizeX()+" "+reader.getSizeY());
        System.out.println(reader.getOptimalTileWidth()+"x"+reader.getOptimalTileHeight());
        System.out.println(reader.getGlobalMetadata());
        SslContextFactory sslContextFactory = new SslContextFactory();
        HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.setFollowRedirects(false);
        httpClient.start();
        ContentResponse response = httpClient.newRequest("http://vinculum.bmi.stonybrookmedicine.edu/rest/session/token").method(HttpMethod.GET).agent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:17.0) Gecko/20100101 Firefox/17.0").send();
        String XCSRFToken = response.getContentAsString();
        System.out.println("X-CSRF-Token : "+XCSRFToken);
        Request request = httpClient.newRequest("http://vinculum.bmi.stonybrookmedicine.edu/node/"+args[0]+"?_format=json").method("PATCH").header(HttpHeader.CONTENT_TYPE, "application/json").header("x-csrf-token", XCSRFToken).header(HttpHeader.AUTHORIZATION, "Basic "+Base64.encode("archon:bluecheese"));
        JsonObject json = Json.createObjectBuilder()
            .add("field_width", Json.createArrayBuilder().add(Json.createObjectBuilder().add("value", BigDecimal.valueOf(reader.getSizeX()))))
            .add("field_height", Json.createArrayBuilder().add(Json.createObjectBuilder().add("value", BigDecimal.valueOf(reader.getSizeY()))))
            .add("type", Json.createArrayBuilder().add(Json.createObjectBuilder().add("target_id", "wsi"))).build();
        request.content(new StringContentProvider(json.toString()));
        //request.content(new StringContentProvider("{\"title\": [{\"value\": \"I AM!!!\"}],\"field_height\": [{\"value\": 7800}],\"field_width\": [{\"value\": 43111}],\"type\": [{\"target_id\": \"wsi\"}]}"));
        response = request.send();
        String res = new String(response.getContent());
        System.out.println(res);
        httpClient.stop();
    }
}