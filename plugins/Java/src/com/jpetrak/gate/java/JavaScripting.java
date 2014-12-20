// This is the class from which all the objects generated
// for a script inherit
package com.jpetrak.gate.java;

import gate.Document;
import gate.Controller;
import gate.AnnotationSet;
import gate.Corpus;
import gate.FeatureMap;
import gate.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.log4j.Logger;


public class JavaScripting  {
  // we create one logger per effective JavaScripting class (which will be
  // a subclass of this class)
  public Logger logger = Logger.getLogger(this.getClass());
  
  // This is a global datastructure for all instances of JavaScripting, i.e.
  // all the different scripting PRs and also the duplicates of one scripting PR.
  public static ConcurrentMap<String,Object> globalsForAll = new ConcurrentHashMap<String,Object>();
  
  // This datastructure is always set by the PR which compiles/creates the 
  // scripting instance. For instances which have been created for duplicates
  // of the same script, the initial datastructure will be shared. That way
  // all duplicated copies will access the same data here.
  public ConcurrentMap<String,Object> globalsForPr;  // will be set by JavaSriptingPR

  // This will get set to a value which is shared between the duplication copies
  // of the generating PR.
  public Flag initializedForPr;
  
  
  // These are set each time execute() is called and thus get whatever the
  // scriptingPR or its duplicate gets. In other words, e.g. the controller
  // will be a different controller for each duplicate.
  public Document doc = null;
  public Controller controller = null;
  public Corpus corpus = null;
  public AnnotationSet inputAS = null;
  public String inputASName = null;
  public String outputASName = null;
  public AnnotationSet outputAS = null;
  public FeatureMap parms = null;
  public Resource resource1 = null;
  public Resource resource2 = null;
  public Resource resource3 = null;
  
  // The duplicationId will be different for each duplicate copy of the
  // original.
  public int duplicationId = 0;
  
  public void execute() { 
  }
  public void controllerStarted()  { }
  public void controllerFinished()  { }
  public void controllerAborted( Throwable throwable)  { }
  
  
  /** 
   * This gets called once for each script and each copy.
   * It will get called before the first document is processed and 
   * only if a document is processed.
   * 
   */
  public void init() { }
  
  /** 
   * This gets called once for all duplicated copies of a script.
   * It will get called before the first document is processed and 
   * only if a document is processed.
   */
  public void initPr() { }
  
  public void initAll() { }
  
  
  public void cleanupPr() { }
  
  public void cleanup() { }
  
  
  
  void callExecute() {
    callInitAll();
    callInitPr();
    callInit();
    execute();
  }

  
  public Object lockForPr = null;
  private void callInitPr() { 
    // TODO: it should maybe be the calling PR's job to make sure this is 
    // only called once?
    if(initializedForPr.get()) return;
    synchronized(lockForPr) {
      if(!initializedForPr.get()) {
        initPr();
        initializedForPr.set(true);
      }
    }
  }
  
  public void callCleanupPr() {
    // NOTE: the calling PR makes sure this is only called once!
    synchronized(lockForPr) {
      cleanupPr();
    }
  }
  
  private boolean initialized = false;
  private void callInit() { 
    if(initialized) return;
    if(!initialized) {
      init();
      initialized = true;
    }
  }
  
  
  // This is an object that loows to serialize code so that only one instance
  // of all JavaScripting instances in the VM will run. 
  public static Object lockForAll = new Object();
  public static boolean initializedForAll = false;
  private void callInitAll() { 
    if(initializedForAll) return;
    synchronized(lockForAll) {
      if(!initializedForAll) {
        initAll();
        initializedForAll = true;
      }
    }
  }
  
  public static void resetInitAll() {
    synchronized(lockForAll) {
      initializedForAll = false;
      globalsForAll = new ConcurrentHashMap<String,Object>();
    }
  }
  
}
