/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.twitter.expander;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import gate.Annotation;
import gate.FeatureMap;
import gate.Resource;
import gate.Utils;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Add the Twitter user profile information for Twitter users mentioned in the document to the 
 * end of the document text..
 * 
 * For each detected UserID annotation in the input document, a block of text
 * will be added to the document which is the main fields of the Twitter user profile
 * for that user. 
 * The added text will be covered by an annotation in the output annotation set
 * with the output annotation type given, and separated from the original document
 * text or previous added text by two new line characters covered by an
 * annotation with "Space" appended to the type name.
 * Each individual field added to the document will also be covered by its 
 * own annotation with the name of the field appended to the output annotation
 * type. Each field is separated from the previous one by a new line character
 * which is part of the field annotation.
 * 
 * If the user was not found, Twitter will respond with empty information or
 * e.g. a 404 http response. In this case we do not expand anything but if 
 * caching is enabled or the cache is used, a user which is not found is 
 * represented by the empty string. 
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "TwitterExpanderUser",
        comment = "Append text from twitter user names mention in the original document.")
public class TwitterExpanderUser extends TwitterExpanderAnnotatorBase {
  
  @Optional
  @RunTime
  @CreoleParameter(comment="The input annotation type",defaultValue="UserID")
  public void setInputType(String name) { inputType=name; }
  
  @Optional
  @RunTime
  @CreoleParameter(comment="The output annotation type",defaultValue="TwitterExpanderUserID")
  public void setOutputType(String name) { outputType=name; }
  
  protected String twitterApiKey;
  @RunTime
  @CreoleParameter(comment="The Twitter application key")
  public void setTwitterApiKey(String key) { twitterApiKey = key; }
  public String getTwitterApiKey() { return twitterApiKey; }
  
  protected String twitterApiSecret;
  @RunTime
  @CreoleParameter(comment="The Twitter application secret")
  public void setTwitterApiSecret(String secret) { twitterApiSecret = secret; }
  public String getTwitterApiSecret() { return twitterApiSecret; }
  
  String twitterApiToken;  
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    super.init();
    logger = Logger.getLogger(this.getClass());
    return this;
  }

  @Override
  public void do_execute() {
    for(Annotation ann : toProcess) {
      logger.debug("DEBUG TwitterExpanderUser: Processing annotation: "+ann);

      String user = Utils.cleanStringFor(document,ann);
      user = user.trim().toLowerCase();
      String profileJson = null;
      if(user.length() < 2) {
        nrIgnored++;
        continue;
      }
      nrChecked++;
      boolean needToCache = false;
      if(getCache() != null) {
        logger.debug("DEBUG TwitterExpanderUser Got a cache: "+getCache()+" has type "+getCache().getClass());
        // this will return null if nothing was found, and an empty string
        // if the user is known to not be known (twitter does not know it
        // or did return an error)
        profileJson = getFromCache(getCache(),user);
        logger.debug("DEBUG TwitterExpanderUser Got profile from cache: "+profileJson);
        if(profileJson == null) {
          nrNotInCache++;
        }
      }
      if(profileJson == null) {
        if(!getFromCacheOnly()) {
          // before we try to fetch from twitter, log in unless we already did so
          if(twitterApiToken == null) {
            twitterApiToken = TwitterApiUtils.twitterApiLogin(getTwitterApiKey(), getTwitterApiSecret());
            logger.debug("DEBUG TwitterExpanderUser: Logged in, got auth token: "+twitterApiToken);
          }        
          // the following could in theory return null to indicate that we
          // did not get information, but do not want to cache the lack of
          // information either (i.e. we want to retry next time). This is
          // not used at the moment.
          nrQueries++;
          profileJson = getUserProfileJson(user);
          logger.debug("DEBUG TwitterExpanderUser Got json from twitter: "+profileJson);
          if(profileJson != null) {
            needToCache = true;
          }
        }
      } else {
        if(profileJson.isEmpty()) {
          nrInCacheNotExisting++;
        } else {
          nrInCacheExisting++;
        }
      }
      if(profileJson != null) {
        // if the json string we get is the empty string this indicates that
        // the user is unknown. We do not expand that user but we still cache
        // it so we do not ask Twitter for the info again later.
        if(!profileJson.isEmpty()) {
          nrExpanded++;
          ObjectMapper mapper = new ObjectMapper();
          Map<String, Object> profileMap;
          List<Map<String, Object>> profileMaps;
          try {
            profileMaps = mapper.readValue(profileJson, List.class);
            if (profileMaps.size() != 1) {
              throw new GateRuntimeException("Got a strage JSON string from Twitter, number of user maps is not 1: " + profileJson);
            }
            profileMap = profileMaps.get(0);
          } catch (Exception ex) {
            throw new GateRuntimeException("Problem parsing the returned JSON as a List " + profileJson, ex);
          }
          // Now try and access those parts of the profile information which we
          // either want to add to the document as text or as features to the 
          // overall output annotation. Also, we will cover each individual
          // part of the profile by its own annotation where the type name is 
          // made up from the output annotation type with some suffix appended.

          // Create the feature map for the overall annotation and store the 
          // id of the userId annotation.
          int mentionId = ann.getId();
          FeatureMap fm = Utils.featureMap("mentionId", mentionId);
          // add the separator 
          appendAnnotatedText(document, "\n\n", outAS, getOutputType() + "Space", Utils.featureMap());
          // remember the start offset of where the rest gets appended
          long startOffset = document.getContent().size();

          // Append the fields that need to get appended as text and cover each
          // with its own annotation
          for (String fieldName : profileStringFields) {
            String value = (String) profileMap.get(fieldName);
            if (value != null && !value.isEmpty()) {
              appendAnnotatedText(document, value, outAS, getOutputType() + "_" + fieldName, Utils.featureMap("mentionId", mentionId));
              appendText(document, "\n");
            }
          }
          // add the interesting fields to the main annotation and annotate everything with the
          // main annotation
          for (String fieldName : profileInfoFields) {
            Object value = profileMap.get(fieldName);
            if (value != null) {
              fm.put(fieldName, value);
            }
          }
          // create the main annotation
          long endOffset = document.getContent().size();
          int fullId = Utils.addAnn(outAS, startOffset, endOffset, getOutputType(), fm);
          ann.getFeatures().put("expansionId", fullId);
        }
        if(getCache() != null && needToCache && !getFromCacheOnly()) {
          //System.out.println("Got a cache: "+getFromCache()+" has type "+getFromCache().getClass());
          putIntoCache(getCache(),user,profileJson);
          if(profileJson.isEmpty()) {
            nrAddedToCacheNotExisting++;
          } else {
            nrAddedToCacheExisting++;
          }
        }
      } // if profileJson != null
    }
  }
  
  private static final String urlPrefixGetUser = "https://api.twitter.com/1.1/users/lookup.json?screen_name=";
  
  protected String getUserProfileJson(String userid) {
    //System.out.println("Trying to get info for user: "+userid);
    String json = "";
    try {
      TwitterApiUtils.twitterGet(urlPrefixGetUser+userid, twitterApiToken);
    } catch (Exception ex) {
      ex.printStackTrace(System.err);
    }
    return json;
  }
  
  
  // We originally wanted to use this to convert the JSON, but we will
  // rather convert to an opaque map and just use the few fields we actually need.
  // This has too many things we do not actually need at all ....
  private static class TwitterUserInfo {
    public long id;
    public String id_str;
    public String name;
    public String screen_name;
    public String location;
    public String description;
    public String url;
    public Map<String,Object> entities; // wtf is this?
    @JsonProperty("protected")
    public boolean isProtected;
    public int followers_count;
    public int friends_count;
    public int listed_count;
    public String created_at;
    public int favourites_count;
    public int utc_offset;
    public String time_zone;
    public boolean geo_enabled;
    public boolean verified;
    public int statuses_count;
    public String lang;
    public Map<String,Object> status;
    public boolean contributors_enabled;
    public boolean is_translator;
    public String profile_background_color;
    public String profile_background_image_url;
    public String profile_background_image_url_https;
    public String profile_background_tile;
    public String profile_image_url;
    public String profile_image_url_https;
    public String profile_banner_url;
    public String profile_sidebar_border_color;
    public String profile_sidebar_fill_color;
    public String profile_text_color;
    public boolean profile_use_background_image;
    public boolean default_profile;
    public boolean default_profile_image;
    public boolean following;
    public boolean follow_request_sent;
    public boolean notifications;
  }

  // This list contains the twitter field name of all the String-valued fields
  // we want to access and add to the document text. 
  private static List<String> profileStringFields = new LinkedList<String>();
  private static List<String> profileInfoFields = new LinkedList<String>();
  static { 
    profileStringFields.add("name");
    profileStringFields.add("screen_name");
    profileStringFields.add("location");
    profileStringFields.add("description");
    profileStringFields.add("url");
    profileInfoFields.add("name");
    profileInfoFields.add("screen_name");
    profileInfoFields.add("location");
    profileInfoFields.add("description");
    profileInfoFields.add("url");
    profileInfoFields.add("verified");
  }
  
  
}
