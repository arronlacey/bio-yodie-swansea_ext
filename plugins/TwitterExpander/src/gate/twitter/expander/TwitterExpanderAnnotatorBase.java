/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.twitter.expander;

import gate.AnnotationSet;
import gate.Document;
import gate.FeatureMap;
import gate.Gate;
import gate.GateConstants;
import gate.LanguageResource;
import gate.Resource;
import gate.Utils;
import gate.corpora.DocumentContentImpl;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ExecutionInterruptedException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import gate.util.InvalidOffsetException;
import com.jpetrak.gate.jdbclookup.JdbcLookupUtils;
import com.jpetrak.gate.jdbclookup.JdbcString2StringLR;
import gate.Controller;
import gate.Corpus;
import gate.CorpusController;
import gate.creole.ControllerAwarePR;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

/**
 *
 * @author Johann Petrak
 */
public class TwitterExpanderAnnotatorBase extends AbstractLanguageAnalyser
  implements ControllerAwarePR {
  
  /// COMMON PR PARAMETERS (all runtime)
  
  protected LanguageResource cache;
  @Optional
  @RunTime
  @CreoleParameter(comment="",defaultValue="")
  public void setCache(LanguageResource cacheLr) { 
    cache = cacheLr;
    if(cacheLr == null) { 
      return;
    }
    if(cacheLr instanceof JdbcString2StringLR) {
      cache = cacheLr; 
      JdbcString2StringLR lr = (JdbcString2StringLR)cache;
      // Make sure that whenever either the connection is readonly or the
      // LR is set to behave read-only, we also set our own readonly parameter
      // to true.
      boolean connectionIsReadOnly = false;
      try {
        connectionIsReadOnly = lr.getConnection().isReadOnly();
      } catch (SQLException ex) {
        throw new GateRuntimeException("Could not determine if Cache LR represent read-only connection: "+cacheLr.getName());
      }
      if(lr.getReadOnly()) {
        connectionIsReadOnly = true;        
      }
      if(connectionIsReadOnly) {
        setCacheIsReadOnly(true);
      }
    } else {
      throw new GateRuntimeException("The cache resource is not a JdbcString2StringLR from the JdbcLookup plugin: "+cacheLr.getName());
    }
  }
  public LanguageResource getCache() { return cache; }
  
  protected String inputAS;
  @Optional
  @RunTime
  @CreoleParameter(comment="The input annotation set",defaultValue="")
  public void setInputAS(String name) { inputAS=name; }
  public String getInputAS() { return inputAS; }
  
  protected String outputAS;
  @Optional
  @RunTime
  @CreoleParameter(comment="The output annotation set",defaultValue="")
  public void setOutputAS(String name) { outputAS=name; }
  public String getOutputAS() { return outputAS; }

  protected boolean cacheIsReadOnly = false;
  @Optional
  @RunTime
  @CreoleParameter(comment="If the cache should be used for reading only",defaultValue="false")
  public void setCacheIsReadOnly(Boolean flag)  { cacheIsReadOnly = flag; }
  public Boolean getCacheIsReadOnly() { return cacheIsReadOnly; }
  
  protected boolean fromCacheOnly = false;
  @Optional
  @RunTime
  @CreoleParameter(comment="If true, only use data from the cache, not from the lookup source",defaultValue="false")
  public void setFromCacheOnly(Boolean flag) { fromCacheOnly = flag; }
  public Boolean getFromCacheOnly() {return fromCacheOnly; }
  
  
  // COMMON PR Parameters which will be overridden by each PR implementation
  // because of specific comment and default values
  // We still declare the fields and the base getters here so that we can 
  // use a common execute() method for the basic things.
  
  protected String inputType;
  public String getInputType() { return inputType; }
  
  protected String outputType;
  public String getOutputType() { return outputType; }
  
  
  // For execute() we prepare the following fields
  protected AnnotationSet inAS;
  protected AnnotationSet outAS;
  protected AnnotationSet toProcess;
  
  protected Logger logger;
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    if(getInputType() == null || getInputType().isEmpty()) {
      throw new ResourceInstantiationException("No input annotation type specified!");
    }
    if(getOutputType() == null || getOutputType().isEmpty()) {
      throw new ResourceInstantiationException("No output annotation type specified!");
    }
    if(getFromCacheOnly() && getCache() == null) {
      throw new ResourceInstantiationException("No cache LR and fromCacheOnly=true does not make much sense");
    }
    resetCounters();
    return this;
  }
  
  @Override
  public void cleanup() {
    System.out.println("Cleanup for "+this.getClass().getSimpleName()+" finishOnCleanup is "+finishOnCleanup);
    if(finishOnCleanup) {
      logCounters(this.getClass().getSimpleName());
    }
  }
  
  @Override
  public void execute() throws ExecutionException {
    if(isInterrupted()) {
      throw new ExecutionInterruptedException();
    }
    //fireStatusChanged("TwitterExpander running for " + document.getName() + "...");
    inAS = document.getAnnotations(getInputAS());
    outAS = document.getAnnotations(getOutputAS());
    toProcess = inAS.get(getInputType());
    ensureOriginalTextInfo(document);
    do_execute();
    //fireProcessFinished();
    //fireStatusChanged("TwitterExpander complete!");

  }
  
  // the actual implementation of everything that is different between the 
  // individual PRs.
  protected void do_execute() throws ExecutionException {
    
  }
  
  
  protected int nrChecked = 0;
  protected int nrInCacheExisting = 0;
  protected int nrInCacheNotExisting = 0;
  protected int nrNotInCache = 0;
  protected int nrAddedToCacheExisting = 0;
  protected int nrAddedToCacheNotExisting = 0;
  protected int nrQueries = 0;
  protected int nrIgnored = 0;
  protected int nrExpanded = 0;
  protected void resetCounters() {
    nrChecked = 0;
    nrInCacheExisting = 0;
    nrInCacheNotExisting = 0;
    nrNotInCache = 0;
    nrAddedToCacheExisting = 0;
    nrAddedToCacheNotExisting = 0;    
    nrQueries = 0;
    nrIgnored = 0;
    nrExpanded = 0;
  }
  protected void logCounters(String what) {
    // TODO: this should really use logger.info, but last time we tried this
    // did not produce any output so we are using System.out.println for now ..
    System.out.println(what+" checked, not ignored: "+nrChecked);
    System.out.println(what+" checked, ignored: "+nrIgnored);
    System.out.println(what+" expanded: "+nrExpanded);
    System.out.println(what+" queried/retrieved: "+nrQueries);
    System.out.println(what+" in cache, existing: "+nrInCacheExisting);
    System.out.println(what+" in cache, not existing/error: "+nrInCacheNotExisting);
    System.out.println(what+" not in cache: "+nrNotInCache);
    System.out.println(what+" added to cache, existing: "+nrAddedToCacheExisting);
    System.out.println(what+" added to cache, not existing: "+nrAddedToCacheNotExisting);
  }
  
  
  
  /// These will be used in more than one concrete implementation ...
  
  protected int appendAnnotatedText(Document doc, String text, 
          AnnotationSet as, String type, FeatureMap features) {
    if(text == null || text.isEmpty()) {
      throw new GateRuntimeException("Text to append to the document must not be null or empty!");
    }
    long size = doc.getContent().size();
    try {
      // add the text to the end of the document 
      // First make sure that annotations at the end will not get expanded into
      // the new text. 
      Gate.getUserConfig().put(GateConstants.DOCEDIT_INSERT_PREPEND,true);
      doc.edit(size, size, new DocumentContentImpl(text));
    } catch (InvalidOffsetException ex) {
      throw new GateRuntimeException("Could not edit the document to append additional text",ex);
    }
    long newsize = doc.getContent().size();
    int id = Utils.addAnn(as,size,newsize,type,features);
    return id;
  }
  
  protected void appendText(Document doc, String text) {
    if(text == null || text.isEmpty()) {
      throw new GateRuntimeException("Text to append to the document must not be null or empty!");
    }
    long size = doc.getContent().size();
    try {
      Gate.getUserConfig().put(GateConstants.DOCEDIT_INSERT_PREPEND,true);
      doc.edit(size, size, new DocumentContentImpl(text));    
    } catch (InvalidOffsetException ex) {
      throw new GateRuntimeException("Could not edit the document to append additional text",ex);
    }
  }
  
  protected void putIntoCache(Resource lr, String key, String value) {
    if(getCacheIsReadOnly() == null || !getCacheIsReadOnly()) {
      JdbcLookupUtils.put(lr, key, value);
    }
  }
  
  protected String getFromCache(Resource lr, String key) {
    return JdbcLookupUtils.get(lr, key);
  }
  
  /**
   * Unless we already have done so, set the document feature 
   * TwitterExpanderOriginalTextSize to the size of the original document.
   * @param doc 
   */
  protected void ensureOriginalTextInfo(Document doc) {
    FeatureMap docfm = doc.getFeatures();
    if(docfm.get("TwitterExpanderOriginalTextSize") == null) {
      docfm.put("TwitterExpanderOriginalTextSize", doc.getContent().size());
    }
  }
  
  protected void addId2ListFeature(FeatureMap fm, String name, int id) {
    Object currentValue = fm.get(name);
    List<Integer> idList;
    if(currentValue == null) {
      idList = new ArrayList<Integer>();
    } else if(currentValue instanceof List) {
      idList = (List<Integer>)currentValue;
    } else {
      throw new GateRuntimeException("Cannot add to id list for feature "+
              name+" feature is of type "+currentValue.getClass());
    }
    idList.add(id);
  }

  protected boolean finishOnCleanup = true;
  @Override
  public void controllerExecutionStarted(Controller cntrlr) throws ExecutionException {
    if(cntrlr == null) {
      throw new GateRuntimeException("No controller!");
    }
    if(cntrlr instanceof CorpusController) {
      CorpusController c = (CorpusController)cntrlr;
      Corpus corpus = c.getCorpus();     
      // TODO/BUG: for some reason, gate.gui.MainFrame.getInstance().isVisible() started 
      // to a) make standard output and standard error to get "swallowed" and b) 
      // the gate program to hang on exit, so we cannot use this trick anymore to 
      // find out if we are running in the GUI.
      // For now we just always set finishOnCleanup to true and thus output the 
      // statistics only when the resource is getting cleaned up.
      /*
      if(corpus.size() == 1 && 
              (java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance() ||
              !gate.gui.MainFrame.getInstance().isVisible())) {
        finishOnCleanup = true;        
      } else {
        resetCounters();
      }
      */
      finishOnCleanup = true;
    } else {
      throw new GateRuntimeException("Controller is not a corpus controller!");
    }
  }

  @Override
  public void controllerExecutionFinished(Controller cntrlr) throws ExecutionException {
    if(!finishOnCleanup) {
      logCounters(this.getClass().getSimpleName());
      resetCounters();
    }
  }

  @Override
  public void controllerExecutionAborted(Controller cntrlr, Throwable thrwbl) throws ExecutionException {
    if(!finishOnCleanup) {
      logCounters(this.getClass().getSimpleName());
      resetCounters();
    }
  }
}
