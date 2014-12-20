package gate.twitter.expander;

import com.fasterxml.jackson.databind.ObjectMapper;
import gate.Annotation;
import gate.Factory;
import gate.FeatureMap;
import gate.Resource;
import gate.Utils;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import java.util.List;
import java.util.Map;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.log4j.Logger;


/**
 * Add possible meanings for hashtags to the end of the document text.
 * 
 * Eeach possible meaning will be covered by an annotation as specified 
 * by the output annotation set and output type parameters. To better separate
 * the added text from the original text and from previous added text, for 
 * each possible meaning the PR will also add two new line characters first
 * which will be covered by an annotation with the output annotation type 
 * name with "Space" appended. 
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "TwitterExpanderHashtag",
        comment = "Expand Twitter Hashtags to appended document text")
public class TwitterExpanderHashtag extends TwitterExpanderAnnotatorBase {
  @Optional
  @RunTime
  @CreoleParameter(comment="The input annotation type",defaultValue="Hashtag")
  public void setInputType(String name) { inputType=name; }
  
  @Optional
  @RunTime
  @CreoleParameter(comment="The output annotation type",defaultValue="TwitterExpanderHashtag")
  public void setOutputType(String name) { outputType=name; }
  
  

  
  @Override
  public Resource init() throws ResourceInstantiationException {
    //System.out.println("Running sub init");
    super.init();
    logger = Logger.getLogger(this.getClass());
    return this;
  }

  @Override
  public void do_execute() {
    for(Annotation ann : toProcess) {
      //System.out.println("Processing hashtag: "+ann);
      // lookup the hashtag:
      // if we have a cache, try the cache first, then from the website
      // if something is found, expand the document, and link between the 
      // hashtag and the added block, otherwise indicate that we did not find
      // anything.
      String hashTag = Utils.cleanStringFor(document,ann).substring(1);
      // if the hashtag does not contain at least one character in addition
      // to the hash, ignore this.
      if(hashTag.length() < 2) {
        nrIgnored++;
        continue;
      }
      nrChecked++;
      String hashTagInfoJson = null;
      boolean needToCache = false;
      if(getCache() != null) {
        //System.out.println("Got a cache: "+getFromCache()+" has type "+getFromCache().getClass());
        hashTagInfoJson = getFromCache(getCache(),hashTag);
        //System.out.println("Got json from cache: "+hashTagInfoJson);
        if(hashTagInfoJson==null) {
          nrNotInCache++;
        } else if(hashTagInfoJson.isEmpty()) {
          nrInCacheNotExisting++;
        } else {
          nrInCacheExisting++;
        }
      }
      if(hashTagInfoJson == null && !getFromCacheOnly()) {
        nrQueries++;
        hashTagInfoJson = retrieveServerResponse(hashTag);
        //System.out.println("Got json from server: "+hashTagInfoJson);
        if(hashTagInfoJson != null) {
          needToCache = true;
        }
      }
      if(hashTagInfoJson != null) {
        if(!hashTagInfoJson.isEmpty()) {
          nrExpanded++;
          HashTagInfoAll all = convertStringToInfoAll(hashTagInfoJson);
          // process the list of entries
          long startOffset = document.getContent().size();
          int i = 0;
          for (Map<String, HashTagInfo> el : all.defs) {
            HashTagInfo info = el.get("def");
            if (info != null) {
              String text = info.text;
              FeatureMap fm = Utils.featureMap("mentionId", ann.getId(), "candidate", i,
                      "upvotes", info.upvotes, "downvotes", info.downvotes);
              appendAnnotatedText(document, "\n\n", outAS, getOutputType() + "Space", Utils.featureMap());
              int id = appendAnnotatedText(document, text, outAS, getOutputType(), fm);
              addId2ListFeature(ann.getFeatures(), "expansionId", id);
            } else {
              throw new GateRuntimeException("Odd JSON retrieved from tagdef.com: " + hashTagInfoJson);
            }
            i++;
          }
          long endOffset = document.getContent().size();
          int lid = Utils.addAnn(outAS, startOffset, endOffset, getOutputType()+"List", Factory.newFeatureMap());
          ann.getFeatures().put("expansionId",lid);
        }
        if(getCache() != null && needToCache && !getFromCacheOnly()) {
          //System.out.println("Got a cache: "+getFromCache()+" has type "+getFromCache().getClass());
          putIntoCache(getCache(),hashTag,hashTagInfoJson);
          if(hashTagInfoJson.isEmpty()) {
            nrAddedToCacheNotExisting++;
          } else {
            nrAddedToCacheExisting++;
          }
        }
      }
    }
  }
  
  protected HashTagInfoAll getHashTagInfoAll(String tagStr) {
    String response = retrieveServerResponse(tagStr);
    return convertStringToInfoAll(response);
  }
  
  protected HashTagInfoAll convertStringToInfoAll(String json) {
    ObjectMapper mapper = new ObjectMapper();
    HashTagInfoAll all = null;
    try {
      all = mapper.readValue(json, HashTagInfoAll.class);
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem parsing the returned JSON as HashTagInfoAll "+json,ex);
    }
    return all;
  }
    
  protected static class HashTagInfoAll {
    public String num_defs;
    public List<Map<String,HashTagInfo>> defs;
  }
  
  protected static class HashTagInfo {
    public String text;
    public String time;
    public String upvotes;
    public String downvotes;
    public String uri;
    public String hashtag;
  }
  
  protected String retrieveServerResponse(String tagStr) {
    // TODO: this forces english definitions for now!
    // See https://api.tagdef.com/
    String requestUrl = "http://api.tagdef.com/" + tagStr + ".json"+"?lang=en";
    //System.out.println("Trying to get: "+requestUrl);
    String result;
    try {
      byte[] ret = Request.Get(requestUrl).
            connectTimeout(1000).
            socketTimeout(1000).
            execute().
            returnContent().
            asBytes();
      result = new String(ret,"UTF-8");
    } catch (Exception ex) {
      if(ex instanceof HttpResponseException && ex.getMessage().equals("not found")) {
        return "";
      }
      ex.printStackTrace(System.err);
      return null;
    }
    
    return result;
  }
  
}
