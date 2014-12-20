package gate.twitter.expander;

import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.extractors.DefaultExtractor;
import de.l3s.boilerpipe.extractors.KeepEverythingExtractor;
import gate.Annotation;
import gate.FeatureMap;
import gate.Resource;
import gate.Utils;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import java.io.FileNotFoundException;
import java.net.URL;
import org.apache.log4j.Logger;

/**
 * Add the text of a web-page referenced by an URL to the end of the document.
 * 
 * For each detected URL annotation in the input document, a block of text
 * will be added to the document which is the content of the web page
 * for that URL. If a boilerpipe processing method other than NONE is chosen,
 * the web page text is first filtered by the boilerpipe algorithm.
 * The added text will be covered by an annotation in the output annotation set
 * with the output annotation type given, and separated from the original document
 * text or previous added text by two new line characters covered by an
 * annotation with "Space" appended to the type name.
 * The appended text can be limited by the maxchars parameter: if this is 
 * set to a value larger than 0, then the appended text will be limited to 
 * the beginning of the extracted text up to the last space before the maxchars
 * offset. If no space is found, no text will be appended at all;
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "TwitterExpanderUrl",
        comment = "Append text from URLs mentioned in the document to the end of the document.")
public class TwitterExpanderUrl extends TwitterExpanderAnnotatorBase {
  @Optional
  @RunTime
  @CreoleParameter(comment="The input annotation type",defaultValue="URL")
  public void setInputType(String name) { inputType=name; }
  
  @Optional
  @RunTime
  @CreoleParameter(comment="The output annotation type",defaultValue="TwitterExpanderURL")
  public void setOutputType(String name) { outputType=name; }
  
  protected BoilerPipeProcessing boilerPipeFilter = BoilerPipeProcessing.ARTICLE;
  @RunTime
  @CreoleParameter(comment="How to filter the web page content",defaultValue="ARTICLE")
  public void setBoilerPipeFilter(BoilerPipeProcessing filter) { boilerPipeFilter = filter; }
  public BoilerPipeProcessing getBoilerPipeFilter() { return boilerPipeFilter; }
  
  protected int maxChars = 0;
  @RunTime
  @CreoleParameter(comment="Maximum number of characters to use from the filtered web page test",
          defaultValue="0")
  public void setMaxChars(Integer val) { maxChars = val; }
  public Integer getMaxChars() { return maxChars; }
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    super.init();
    logger = Logger.getLogger(this.getClass());
    return this;
  }
 
  
  @Override
  public void do_execute() {
    for(Annotation ann : toProcess) {
      //System.out.println("Processing url: "+ann);

      String url = Utils.cleanStringFor(document,ann);
      String text = null;
      if(url.length() < 4) {
        nrIgnored++;
        continue;
      }
      nrChecked++;
      boolean needToCache = false;
      if(getCache() != null) {
        //System.out.println("Got a cache: "+getFromCache()+" has type "+getFromCache().getClass());
        text = getFromCache(getCache(),url);
        //System.out.println("Got text from cache: "+text);
        if(text == null) {
          nrNotInCache++;
        } else if(text.isEmpty()) {
          nrInCacheNotExisting++;
        } else {
          nrInCacheExisting++;
        }
      }
      if(text == null && !getFromCacheOnly()) {
        nrQueries++;
        text = getUrlContent(url);
        //System.out.println("Got text from web page: "+text);
        if(text != null) {
          needToCache = true;
        }
      } 
      if(text != null) {
        if(!text.isEmpty()) {
          nrExpanded++;
          FeatureMap fm = Utils.featureMap("mentionId",ann.getId());
          appendAnnotatedText(document,"\n\n",outAS,getOutputType()+"Space",Utils.featureMap());
          int id = appendAnnotatedText(document, text, outAS, getOutputType(), fm);
          ann.getFeatures().put("expansionId",id);
        }
        if(getCache() != null && needToCache && !getFromCacheOnly()) {
          //System.out.println("Got a cache: "+getFromCache()+" has type "+getFromCache().getClass());
          putIntoCache(getCache(),url,text);
          if(text.isEmpty()) {
            nrAddedToCacheNotExisting++;
          } else {
            nrAddedToCacheExisting++;
          }
        }
      }
    }
  }
  
  public String getUrlContent(String urlStr) {
    try {
      URL url = new URL(urlStr);
      String text = null;
      if(getBoilerPipeFilter().equals(BoilerPipeProcessing.ARTICLE)) {
        text = ArticleExtractor.INSTANCE.getText(url);
      } else if(getBoilerPipeFilter().equals(BoilerPipeProcessing.DEFAULT)) {
        text = DefaultExtractor.INSTANCE.getText(url);
      } else if(getBoilerPipeFilter().equals(BoilerPipeProcessing.EVERYTHING)) {
        text = KeepEverythingExtractor.INSTANCE.getText(url);
      }
      text = text.trim();
      if(maxChars > 0) {
        text = text.substring(0,maxChars);
        int index = text.lastIndexOf(" ");
        if(index > 0) {
          text = text.substring(0,index);
        } else {
          text = null;
        }
      }
      return text;
    } catch (Exception e) {
      Throwable cause = e.getCause();
      if(cause instanceof FileNotFoundException) { // we should get this sometimes if not found
        return ""; 
      }
      System.err.println("In document "+document.getName()+": Exception when trying to retrieve URL "+urlStr);
      e.printStackTrace(System.err);
      return "";
    }

  }
  
  public enum BoilerPipeProcessing {
    EVERYTHING,
    ARTICLE,
    DEFAULT
  }
  
}
