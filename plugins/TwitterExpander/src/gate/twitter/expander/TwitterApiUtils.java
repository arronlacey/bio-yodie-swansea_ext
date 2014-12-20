package gate.twitter.expander;

import com.fasterxml.jackson.databind.ObjectMapper;
import gate.util.GateRuntimeException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

/**
 * A class with utility methods to make working with the Twitter API easier.
 * 
 * TODO: find a convenient way how to signal to the client that all 
 * requests have been used up of how to automatically wait until a new
 * set of requests becomes available.
 * 
 * @author Johann Petrak
 */
public class TwitterApiUtils {
  
  /**
   * Request an authorization token for a registered application.
   * 
   * This will make an attempt to retrieve an authorization token for a registered
   * application. The token can then be used subsequently to retrieve data
   * using the twitter API. This requires the application key and application
   * secret of the registered application.
   * <p>
   * If anything goes wrong, this method throws a GateRuntimeException.
   * 
   * @param appKey 
   * @param appSecret
   * @return 
   */
  public static String twitterApiLogin(String appKey, String appSecret) {
    // First encode the key and secret and concatenate with ":" between them
    String encodedKey = null;
    try {
      encodedKey = 
            URLEncoder.encode(appKey, "UTF-8")+":"+URLEncoder.encode(appSecret, "UTF-8");
      encodedKey = new String(Base64.encodeBase64(encodedKey.getBytes()));
    } catch (Exception ex) {
      throw new GateRuntimeException("Could not encode the keys",ex);
    }
    //System.out.println("Encoded key is "+encodedKey);
    Request rq = Request.Post("https://api.twitter.com/oauth2/token").
            addHeader("Authorization","Basic "+encodedKey).
            addHeader("Content-Type","application/x-www-form-urlencoded;charset=UTF-8").
            //addHeader("User-Agent","lodie01").
            bodyString("grant_type=client_credentials", ContentType.DEFAULT_TEXT);
    String result;
    try {
      result = rq.execute().returnContent().asString();
    } catch (Exception ex) {
      throw new GateRuntimeException("Could not retrieve bearer token from Twitter",ex);
    } 
    // the result is a JSON map object with two String fields: token_type and access_token
    // we just check that we have values for these two and that the value for 
    // token_type is "bearer" and if that is fine, we have the bearer token
    ObjectMapper mapper = new ObjectMapper();
    Map<String,String> bearerMap = null;
    try {
      bearerMap = mapper.readValue(result, Map.class);
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem parsing the returned JSON as a map "+result,ex);
    }
    String tokenType = bearerMap.get("token_type");
    String accessToken = bearerMap.get("access_token");
    if(tokenType.equals("bearer") && accessToken != null && !accessToken.isEmpty()) {
      return accessToken;
    } else {
      throw new GateRuntimeException("Did not get a proper bearer token: "+result);
    }
  }
  
  /**
   * Access the Twitter API using the Https GET method.
   * 
   * This retrieves data using the Twitter API using a https get request.
   * The authToken must have been previously obtained using the twitterApiLogin
   * method. To use this method, the Twitter API request must be part of 
   * the URL that is being passed. 
   * <p>
   * If anything goes wrong, this will throw a GateRuntimeException.
   * 
   * @param url
   * @param authToken
   * @return 
   */
  public static String twitterGet(String url, String authToken) {
    Request rq = Request.
            Get(url).
            addHeader("Authorization","Bearer "+authToken);
    String result;
    try {
      result = rq.execute().returnContent().asString();
    } catch (Exception ex) {
      throw new GateRuntimeException("Could not retrieve result from Twitter for URL "+url,ex);
    } 
    return result;
  }
  
  /**
   * Access the Twitter API using the Https POST method.
   * 
   * This will access the Twitter API and retrieve information using a 
   * Https POST request. The authToken must have been previously obtained using
   * the twitterApiLogin method.This method should be used if the request
   * contains a large amount of data and the API method does support the POST
   * method. 
   * <p>
   * If anything goes wrong this method will throw a GateRuntimeException.
   * @param url
   * @param authToken
   * @param headers
   * @param fields
   * @return 
   */
  public static String twitterPost(String url, String authToken, Map<String,String> headers, Map<String,String> fields) {
    Request rq = Request.
            Post(url).
            addHeader("Authorization","Bearer "+authToken);
    if(headers != null) {
      for(String key : headers.keySet()) {
        rq.addHeader(key,headers.get(key));
      }
    }
    if(fields != null) {
      Form form = Form.form();
      for(String key : fields.keySet()) {
        form.add(key, fields.get(key));
      }
      rq.bodyForm(form.build());
    }
    String result = "";
    Response response;
    try {
      response = rq.execute();
    } catch (Exception ex) {
      throw new GateRuntimeException("Could not retrieve result from Twitter for URL "+url,ex);
    } 
    HttpResponse httpresponse = null;
    try {
      httpresponse = response.returnResponse();
    } catch (IOException ex) {
      throw new GateRuntimeException("Could not extract http response from returned result from Twitter for URL "+url,ex);
    }
    StatusLine statusline = httpresponse.getStatusLine();
    int code = statusline.getStatusCode();
    String phrase = statusline.getReasonPhrase();
    
    try {
      result = EntityUtils.toString(httpresponse.getEntity());
    } catch (Exception ex) {
      throw new GateRuntimeException("Could not extract data from returned result from Twitter for URL "+url,ex);
    }
    //System.err.println("TwitterApiUtils DEBUG: got result="+result);
    if(code != 200) {
      TwitterApiException ex = new TwitterApiException("HTTP response code from Twitter was not 200");
      ex.statusCode = code;
      ex.reasonPhrase = phrase;
      throw ex;
    }
    return result;
  }
  
  public static void main(String[] args) {
    if(args.length != 2) {
      System.err.println("Need the api key and secret as parameters!");
      System.exit(1);
    }
    String token = twitterApiLogin(args[0],args[1]);    
    String url =
      "https://api.twitter.com/1.1/users/lookup.json?screen_name=encoffeedrinker,GateAcUK,StephenFry,Lord_Sugar,ThatKevinSmith,KevinSmithRB";
    String result = twitterGet(url,token);
    System.out.println("Got the result via get: "+result.length());
    Map<String,String> fields = new HashMap<String,String>();
    fields.put("screen_name","encoffeedrinker,StephenFry");
    result = twitterPost("https://api.twitter.com/1.1/users/lookup.json",token,null,fields);
    System.out.println("Got the result via post "+result);
  }
  
  
}
