
package gate.twitter.expander;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpetrak.gate.jdbclookup.JdbcLookupUtils;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Controller;
import gate.Corpus;
import gate.CorpusController;
import gate.LanguageResource;
import gate.Resource;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ControllerAwarePR;
import gate.creole.ExecutionException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fill a cache database with twitter user profile information.
 * 
 * This PR processes documents and collects user names in batches of 
 * 100 at a time, then sends the names to twitter and retrieves the 
 * user profile information for all of them. It then stores the profile
 * information in the cache LR provided.
 * This PR operates in two different ways: since we collect the user names
 * before sending a request to twitter, the PR needs to know when processing
 * has stopped in order to send the last batch. For normal corpora as used
 * in the GUI this can and should be done via the controller aware interface
 * so that any remaining batch is sent when the controller has finished.
 * But if the processing happens in a GCP-similar way, were a corpus is 
 * filled with a single document each time, the controller callback will be
 * received for each single document! We therefore check the size of the corpus
 * and if the corpus only contains 1 document, then we assume that this way
 * of processing is happening. In that case we will send the last batch when
 * cleanup() is called, it is as part of cleaning up the PR.
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "TwitterPreCacheUsers",
        comment = "Process documents and store user information in a cache database.")
public class TwitterPreCacheUsers extends AbstractLanguageAnalyser implements ControllerAwarePR {
  
  static final int MAX_RETRIES = 3;
  static final int RETRY_WAIT_SECONDS = 15 * 60; // 15 minutes
  
  private int checkedUserIds = 0;
  private int toRetrieveUserIds = 0;
  private int retrievedUserIds = 0;
  private int notFoundIds = 0;
  
  protected String twitterApiKey;
  @RunTime
  @Optional
  @CreoleParameter(comment="The Twitter application key")
  public void setTwitterApiKey(String key) { twitterApiKey = key; }
  public String getTwitterApiKey() { return twitterApiKey; }
  
  protected String twitterApiSecret;
  @RunTime
  @Optional
  @CreoleParameter(comment="The Twitter application secret")
  public void setTwitterApiSecret(String secret) { twitterApiSecret = secret; }
  public String getTwitterApiSecret() { return twitterApiSecret; }

  protected LanguageResource cache;
  @RunTime
  @CreoleParameter(comment="",defaultValue="")
  public void setCache(LanguageResource cacheLr) { cache = cacheLr; }
  public LanguageResource getCache() { return cache; }

  protected String inputAS;
  @Optional
  @RunTime
  @CreoleParameter(comment="The input annotation set",defaultValue="")
  public void setInputAS(String name) { inputAS=name; }
  public String getInputAS() { return inputAS; }
  
  protected String inputType;
  @Optional
  @RunTime
  @CreoleParameter(comment="The input annotation type",defaultValue="UserID")
  public void setInputType(String name) { inputType=name; }  
  public String getInputType() { return inputType; }
    
  String twitterApiToken;
  
  boolean finishOnCleanup = false;

  Set<String> toRetrieve;
  
  
  @Override
  public void execute() {
    if(getCache() == null) {
      throw new GateRuntimeException("The Cache LR is required!");
    }
    // if we are not logged in to twitter yet, lets do it!
    if(twitterApiToken == null) {
      twitterApiToken = TwitterApiUtils.twitterApiLogin(getTwitterApiKey(), getTwitterApiSecret());
      //System.out.println("Logged in, got auth token: "+twitterApiToken);
    }
    AnnotationSet inAS = document.getAnnotations(getInputAS());
    AnnotationSet toProcess = inAS.get(getInputType());
    
    for(Annotation ann : toProcess) {
      String user = Utils.cleanStringFor(document,ann);
      // TODO: how to normalize the user id? Is case relevant???
      user = user.trim().toLowerCase();
      if(user.length() < 2) {
        continue;
      }
      if(toRetrieve == null) {
        toRetrieve = new HashSet<String>();
      }
      String profileJson = null;
      profileJson = getCache(getCache(),user);
      //System.err.println("TwitterPreCacheUsers DEBUG: checked "+user+" response null:  "+(profileJson==null));
      checkedUserIds++;
      if(profileJson == null) {
        toRetrieveUserIds++;
        toRetrieve.add(user);
        //System.out.println("** Added user "+user);
        if(toRetrieve.size() == 100) {
          retrieveAndCache();
        }
      }
    }
  }
  
  @Override
  public void cleanup() {
    if(finishOnCleanup) {
      retrieveAndCache();
      System.err.println("TwitterPreCacheUsers INFO cleanup:  checked="+checkedUserIds+" to retrieve="+toRetrieveUserIds+" retrieved="+retrievedUserIds+" not found="+notFoundIds); 
    }
  }
  
  private static final String apiUrl = "https://api.twitter.com/1.1/users/lookup.json";
  // 
  public void retrieveAndCache() {
    if(toRetrieve == null || toRetrieve.isEmpty()) {
      //System.out.println("Batch is null or empty, not retrieving anything");
      return;
    } else {
      //System.out.println("Trying to retrieve "+toRetrieve.size());
    }
    System.err.println("TwitterPreCacheUsers DEBUG: running retrieveAndCache for users: "+toRetrieve.size());    
    try {
      retrieveAndCacheWorker(0);
    } catch (Exception ex) {
      toRetrieve = null;
      toRetrieveUserIds = 0;
      checkedUserIds = 0;
      retrievedUserIds = 0;
      notFoundIds = 0;
      throw new GateRuntimeException("Error when trying to retrieve the user profiles from Twitter",ex);
    }
  }
  private void retrieveAndCacheWorker(int retries) {
    if(retries > MAX_RETRIES) {
      throw new GateRuntimeException("Could not retrieve Twitter data after "+MAX_RETRIES+" giving up");
    }
    StringBuilder parm = new StringBuilder();
    String del = "";
    // the following hashset contains the trimmed and lower-cased versions of
    // all the user ids we are sending off to twitter. Later we will have to
    // check for which of these we actually got something back ... all the ones
    // that are missing in the response are invalid or not found and we should
    // cache an empty string for the missing ones!
    Set<String> allToCheck = new HashSet<String>();
    for(String user : toRetrieve) {
      parm.append(del);
      parm.append(user);
      allToCheck.add(user.trim().toLowerCase());
      if(del.isEmpty()) { del = ","; }
    }
    Map<String,String> parms = new HashMap<String,String>();
    parms.put("screen_name", parm.toString());
    String json = "";
    try {
      json = TwitterApiUtils.twitterPost(apiUrl, twitterApiToken,null,parms);
    } catch (TwitterApiException ex) {
      // check if what appears to be the problem: if we ran out of API calls,
      // wait and try again later, otherwise rethrow the exception and give up.
      // For the meaning of the status codes, see 
      // https://dev.twitter.com/docs/error-codes-responses
      if(ex.statusCode == 429) {
        try {
          Thread.sleep(RETRY_WAIT_SECONDS * 1000);
          retrieveAndCacheWorker(retries+1);
          return;
        } catch (InterruptedException ex1) {
          throw new GateRuntimeException("Interrupted while waiting until the next retry");
        }
      } else if(ex.statusCode == 404) {
        // None of the users in the list was found. In this case, set the json to
        // the empty list
        //System.err.println("TwitterPreCacheUsers DEBUG: got a 404 response, json="+json);
        json = "[]";
      } else {
        throw new GateRuntimeException("Got a TwitterApiException, code="+ex.statusCode+
                " phrase="+ex.reasonPhrase+" url="+apiUrl+" parms="+parms,ex);
      }
    }
    // parse the JSON 
    ObjectMapper mapper = new ObjectMapper();
    List<Map<String,Object>> profileMaps;
    try {
      profileMaps = mapper.readValue(json, List.class);
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem parsing the returned JSON as a List "+json,ex);
    }
    for(Map<String,Object> profileMap : profileMaps) {
      String user = (String)profileMap.get("screen_name");
      user = user.trim().toLowerCase();
      if(user == null) {
        throw new GateRuntimeException("Problem getting the screen_name from the parsed json "+json);
      }
      List<Map<String,Object>> single = new LinkedList<Map<String,Object>>();
      single.add(profileMap);
      String singleJson;
      try {
        // Convert the list with just one map back to JSON and cache
        singleJson = mapper.writeValueAsString(single);
      } catch (JsonProcessingException ex) {
        throw new GateRuntimeException("Could not convert single user profile back to JSON",ex);
      }
      //System.out.println("Caching user "+user+" with data: "+singleJson);
      putCache(getCache(), user, singleJson);
      allToCheck.remove(user);
      retrievedUserIds++;
    }
    // now the allToCheck set should only contain those user ids for which 
    // we did not get anything back from twitter. Count them and also cache 
    // and empty string for them
    notFoundIds += allToCheck.size();
    for(String user : allToCheck) {
      putCache(getCache(), user, "");      
    }
    toRetrieve = null;
  }
  
  
  @Override
  public void controllerExecutionStarted(Controller cntrlr) throws ExecutionException {
    if(cntrlr == null) {
      throw new GateRuntimeException("No controller!");
    }
    if(cntrlr instanceof CorpusController) {
      CorpusController c = (CorpusController)cntrlr;
      Corpus corpus = c.getCorpus();
      if(corpus.size() == 1 && 
              (java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance() ||
              !gate.gui.MainFrame.getInstance().isVisible())) {
        finishOnCleanup = true;
      } 
      //System.err.println("TwitterPreCacheUsers DEBUG: finishOnCleanup is "+finishOnCleanup);
    } else {
      throw new GateRuntimeException("Controller is not a corpus controller!");
    }
  }

  @Override
  public void controllerExecutionFinished(Controller cntrlr) throws ExecutionException {
    if(!finishOnCleanup) {
      retrieveAndCache();
      System.err.println("TwitterPreCacheUsers INFO controller finished:  checked="+checkedUserIds+" to retrieve="+toRetrieveUserIds+" retrieved="+retrievedUserIds+" not found="+notFoundIds); 
      toRetrieve = null;
      toRetrieveUserIds = 0;
      retrievedUserIds = 0;
      checkedUserIds = 0;
      notFoundIds = 0;
    }
  }

  @Override
  public void controllerExecutionAborted(Controller cntrlr, Throwable thrwbl) throws ExecutionException {
    if(!finishOnCleanup) {
      retrieveAndCache();
      System.err.println("TwitterPreCacheUsers INFO controller aborted:  checked="+checkedUserIds+" to retrieve="+toRetrieveUserIds+" retrieved="+retrievedUserIds+" notFound="+notFoundIds); 
      toRetrieve = null;
      toRetrieveUserIds = 0;
      retrievedUserIds = 0;
      checkedUserIds = 0;
      notFoundIds = 0;
    }
  }
  
  protected void putCache(Resource lr, String key, String value) {
    JdbcLookupUtils.put(lr, key, value);
  }
  
  protected String getCache(Resource lr, String key) {
    return JdbcLookupUtils.get(lr, key);
  }
  
}
