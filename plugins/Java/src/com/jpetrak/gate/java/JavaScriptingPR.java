package com.jpetrak.gate.java;

import com.jpetrak.gate.java.gui.JavaEditorVR;
import java.net.URL;

import gate.Resource;
import gate.Controller;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;

import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ControllerAwarePR;
import gate.creole.CustomDuplication;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.metadata.Sharable;
import gate.util.GateClassLoader;
import gate.util.GateRuntimeException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

// NOTE: recompiling a script will re-set the flag for running initPr()
// only re-initializing the PR will re-set the flag for running initAll()


@CreoleResource(
        name = "Java Scripting PR",
        helpURL = "",
        comment = "Use a Java program as a processing resource")
public class JavaScriptingPR
        extends AbstractLanguageAnalyser
        implements ControllerAwarePR, JavaCodeDriven, CustomDuplication {

  // ********* Parameters
  @CreoleParameter(comment = "The URL of the Java program to run",suffixes = ".java")
  public void setJavaProgramUrl(URL surl) {
    javaProgramUrl = surl;
  }

  public URL getJavaProgramUrl() {
    return javaProgramUrl;
  }
  protected URL javaProgramUrl;

  @Optional
  @RunTime
  @CreoleParameter(comment = "The input annotation set", defaultValue = "")
  public void setInputAS(String asname) {
    inputAS = asname;
  }

  public String getInputAS() {
    return inputAS;
  }
  protected String inputAS;

  @Optional
  @RunTime
  @CreoleParameter(comment = "The output annotation set", defaultValue = "")
  public void setOutputAS(String asname) {
    outputAS = asname;
  }

  public String getOutputAS() {
    return outputAS;
  }
  protected String outputAS;

  @Optional
  @RunTime
  @CreoleParameter(comment = "The script parameters", defaultValue = "")
  public void setScriptParams(FeatureMap parms) {
    scriptParams = parms;
  }
  
  @Override
  @Optional
  @RunTime
  @CreoleParameter()
  public void setDocument(Document d) {
    document = d;
  }

  @Optional
  @RunTime
  @CreoleParameter()
  public void setResource1(Resource r) {
    resource1 = r;
  }
  public Resource getResource1() {
    return resource1;
  }
  protected Resource resource1;
  
  @Optional
  @RunTime
  @CreoleParameter()
  public void setResource2(Resource r) {
    resource2 = r;
  }
  public Resource getResource2() {
    return resource2;
  }
  protected Resource resource2;
  
  @Optional
  @RunTime
  @CreoleParameter()
  public void setResource3(Resource r) {
    resource3 = r;
  }
  public Resource getResource3() {
    return resource3;
  }
  protected Resource resource3;
  
  
  public FeatureMap getScriptParams() {
    if (scriptParams == null) {
      scriptParams = Factory.newFeatureMap();
    }
    return scriptParams;
  }
  
  
  protected URL libDirUrl = null;
  @Optional
  @RunTime
  @CreoleParameter()
  public void setLibDirUrl(URL url) {
    libDirUrl = url;
  }
  public URL getLibDirUrl() {
    return libDirUrl;
  }
          
  public org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(this.getClass());
  
  protected FeatureMap scriptParams;
  GateClassLoader classloader = null;
  Controller controller = null;
  File javaProgramFile = null;
  // this is used by the VR
  public File getJavaProgramFile() { return javaProgramFile; }
  List<String> javaProgramLines = null;
  JavaScripting javaProgramClass = null;

  protected File getPluginDir() {
    URL creoleURL = Gate.getCreoleRegister().get(this.getClass().getName()).getXmlFileUrl();
    File pluginDir = gate.util.Files.fileFromURL(creoleURL).getParentFile();
    return pluginDir;
  }
  String fileProlog = "package javascripting;";
  String fileImport = "import com.jpetrak.gate.java.JavaScripting;";
  String classProlog =
          "public class THECLASSNAME extends JavaScripting {";
  String classEpilog = "}";
  Pattern importPattern = Pattern.compile(
          "\\s*import\\s+([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*(?:[\\p{L}_$][\\p{L}\\p{N}_$]*|\\*)\\s*;\\s*(?://.*)?");

  @Sharable
  public void setLockForPr(Object what) {
    lockForPr = what;
  }
  public Object getLockForPr() {
    return lockForPr;
  }
  protected Object lockForPr;  
  
  @Sharable
  public void setInitializedForPr(Flag value) {
    initializedForPr = value;
  }
  public Flag getInitializedForPr() {
    return initializedForPr;
  }
  protected Flag initializedForPr;
  
  @Sharable
  public void setCleanedUpForPr(Flag value) {
    cleanedUpForPr = value;
  }
  public Flag getCleanedUpForPr() {
    return cleanedUpForPr;
  }
  protected Flag cleanedUpForPr;
  
  
  // the nrDuplicates counter will get shared between copies when this
  // PR is being duplicated. We will do a synchronized increment of the 
  // counter in our own duplication method.
  // NOTE: the first, initial PR will have NrDuplicates set to 0, the
  // actual duplicates will get numbers 1, 2, 3 ...
  // (so the first instance does NOT count as a duplicate)
  
  @Sharable
  public void setNrDuplicates(AtomicInteger value) {
    nrDuplicates = value;
  }
  public AtomicInteger getNrDuplicates() {
    return nrDuplicates;
  }
  protected AtomicInteger nrDuplicates;
  
  
  // This will try and compile the script. 
  // This is done 
  // = at init() time
  // = at reInit() time
  // = when the "Use"  button is pressed in the VR
  // If the script fails compilation, our script object is set to null
  // and the PR will throw an exception when an attempt is made to run it
  public void tryCompileScript() {
    String javaProgramSource;
    String className;
    if (classloader != null) {
      gate.Gate.getClassLoader().forgetClassLoader(classloader);
    }
    classloader =
            gate.Gate.getClassLoader().getDisposableClassLoader(
            javaProgramUrl.toExternalForm() + System.currentTimeMillis(),
            true);
    if(libDirUrl != null) {  
      File dirFile = gate.util.Files.fileFromURL(libDirUrl);
      File[] directoryListing = dirFile.listFiles();
      if (directoryListing != null) {
        for (File child : directoryListing) {          
          if(child.getName().toLowerCase().endsWith(".jar")) {
            try {
              Gate.getClassLoader().addURL(child.toURI().toURL());
              logger.info("Added to Gate classpath: "+child.toURI().toURL());
            } catch (MalformedURLException ex) {
              logger.error("Could not add file "+child+" to classpath",ex);
            }
          }
        }
      } else {
        logger.error("Not a directory: "+libDirUrl);
      }
    }
    
    try {
      className = "JavaScriptingClass" + getNextId();
      // need to try and reload and compile (if necessary) the script
      //javaProgramSource = FileUtils.readFileToString(Files.fileFromURL(javaProgramUrl),"UTF-8");
      StringBuilder sb = new StringBuilder();
      List<String> imports = new ArrayList<String>();
      javaProgramLines = new ArrayList<String>();
      List<String> scriptLines = new ArrayList<String>();
      LineIterator it = FileUtils.lineIterator(javaProgramFile, "UTF-8");
      try {
        while (it.hasNext()) {
          String line = it.nextLine();
          if (importPattern.matcher(line).matches()) {
            imports.add(line);
          } else {
            scriptLines.add(line);
          }
        }
      } finally {
        LineIterator.closeQuietly(it);
      }
      // paste the final full source text together
      javaProgramLines.add(fileProlog);
      javaProgramLines.add(fileImport);
      javaProgramLines.addAll(imports);
      javaProgramLines.add(classProlog.replaceAll("THECLASSNAME", className));
      javaProgramLines.addAll(scriptLines);
      javaProgramLines.add(classEpilog);
      for (String line : javaProgramLines) {
        sb.append(line);
        sb.append("\n");
      }
      javaProgramSource = sb.toString();
      //System.out.println("Program Source: " + javaProgramSource);
    } catch (IOException ex) {
      logger.error("Problem reading program from " + javaProgramUrl,ex);
      return;
    }
    //System.out.println("(Re-)Compiling program "+getJavaProgramUrl()+" ... ");
    Map<String, String> toCompile = new HashMap<String, String>();
    toCompile.put("javascripting." + className, javaProgramSource);
    try {
      gate.util.Javac.loadClasses(toCompile, classloader);
      javaProgramClass = (JavaScripting) classloader.
              loadClass("javascripting." + className).newInstance();
      javaProgramClass.globalsForPr = globalsForPr;
      javaProgramClass.lockForPr = lockForPr;
      javaProgramClass.initializedForPr = initializedForPr;
      if(registeredEditorVR != null) {
        registeredEditorVR.setCompilationOk();
      }
      javaProgramClass.resource1 = resource1;
      javaProgramClass.resource2 = resource2;
      javaProgramClass.resource3 = resource3;
      isCompileError = false;
    } catch (Exception ex) {
      logger.error("Problem compiling JavaScripting Class",ex);
      if(classloader != null) {
        Gate.getClassLoader().forgetClassLoader(classloader);
        classloader = null;
      }
      isCompileError = true;
      javaProgramClass = null;
      if(registeredEditorVR != null) {
        registeredEditorVR.setCompilationError();
      }
      return;
    }
  }
  
  // We need this so that the VR can determine if the latest compile was
  // an error or ok. This is necessary if the VR gets activated after the
  // compilation.
  public boolean isCompileError;
  
  private JavaEditorVR registeredEditorVR = null;

  public void registerEditorVR(JavaEditorVR vr) {
    registeredEditorVR = vr;
  }
  // TODO: make this atomic so it works better in a multithreaded setting
  private static int idNumber = 0;

  private static synchronized String getNextId() {
    idNumber++;
    return ("" + idNumber);
  }


  @Override
  public Resource init() throws ResourceInstantiationException {
    // This is a sharable field so it will only be null if this init() call
    // is not for a duplicated instance.
    if(lockForPr == null) {
      lockForPr = new Object();
      globalsForPr = new ConcurrentHashMap<String, Object>();
      initializedForPr = new Flag(false);
      cleanedUpForPr = new Flag(false);
      nrDuplicates = new AtomicInteger(0);
    }
    // This gets called for both the original PR and any copy created by
    // defaultDuplication. Each copy of the PR should get its own 
    // copy of the compiled script, so the first thing we always do is
    // try to compile (and thus store) the script.
    // All the relevant JavaScripting fields will get set in tryCompileScript,
    // if the compile was successful. 
    if (getJavaProgramUrl() == null) {
      throw new ResourceInstantiationException("The javaProgramUrl must not be empty");
    }
    javaProgramFile = gate.util.Files.fileFromURL(getJavaProgramUrl());
    try {
      // just check if we can read the script here ... what we read is not actually 
      // ever used
      String tmp = FileUtils.readFileToString(javaProgramFile, "UTF-8");
    } catch (IOException ex) {
      throw new ResourceInstantiationException("Could not read the java program from " + getJavaProgramUrl(), ex);
    }
    tryCompileScript();
    return this;
  }

  @Override
  public void reInit() throws ResourceInstantiationException {
    //System.out.println("JavaScriptingPR reinitializing ...");
    // We re-set the global initialization indicator so that re-init can be
    // used to test the global init method
    initializedForPr.set(false); // this will only work for non-duplicated things
    if(javaProgramClass != null) {
      javaProgramClass.cleanupPr();
      javaProgramClass.resetInitAll();
      javaProgramClass.initializedForPr = initializedForPr;
    }
    if(registeredEditorVR != null) {
      registeredEditorVR.setFile(getJavaProgramFile());
    }
    init();
  }

  @Override
  public void cleanup() {
    super.cleanup();
    // make sure the generated class does not hold any references
    if (javaProgramClass != null) {
      if(!cleanedUpForPr.get()) {
        javaProgramClass.cleanupPr();              
        cleanedUpForPr.set(true);
      }
      javaProgramClass.cleanup();
      javaProgramClass.doc = null;
      javaProgramClass.controller = null;
      javaProgramClass.corpus = null;
      javaProgramClass.inputASName = null;
      javaProgramClass.outputASName = null;
      javaProgramClass.inputAS = null;
      javaProgramClass.outputAS = null;
      javaProgramClass.parms = null;
      javaProgramClass.globalsForPr = null;
      javaProgramClass.lockForPr = null;
    }
    if (classloader != null) {
      Gate.getClassLoader().forgetClassLoader(classloader);
      classloader = null;
    }
  }

  @Override
  public void execute() {
    if(isInterrupted()) {
      throw new GateRuntimeException("Processing has been interrupted!");
    }
    if (javaProgramClass != null) {
      try {
        javaProgramClass.resource1 = getResource1();
        javaProgramClass.resource2 = getResource2();
        javaProgramClass.resource3 = getResource3();
        javaProgramClass.doc = document;
        javaProgramClass.controller = controller;
        javaProgramClass.corpus = corpus;
        javaProgramClass.inputASName = getInputAS();
        javaProgramClass.outputASName = getOutputAS();
        javaProgramClass.inputAS =
                (document != null && getInputAS() != null) ? document.getAnnotations(getInputAS()) : null;
        javaProgramClass.outputAS =
                (document != null && getOutputAS() != null) ? document.getAnnotations(getOutputAS()) : null;
        javaProgramClass.parms = getScriptParams();
        javaProgramClass.callExecute();
        javaProgramClass.doc = null;
        javaProgramClass.inputASName = null;
        javaProgramClass.outputASName = null;
        javaProgramClass.inputAS = null;
        javaProgramClass.outputAS = null;
      } catch (Exception ex) {
        printGeneratedProgram(System.err);
        throw new GateRuntimeException("Could not run program for script "+this.getName(), ex);
      }
    } else {
      throw new GateRuntimeException("Cannot run script, compilation failed: "+getJavaProgramUrl());
    }
  }

  private void printGeneratedProgram(PrintStream stream) {
    int linenr = 0;
    for(String line : javaProgramLines) {
      linenr++;
      stream.println(linenr+" "+line);
    }
  }
  
  
  
  @Override
  public void controllerExecutionStarted(Controller controller) {
    this.controller = controller;
    if (javaProgramClass != null) {
      javaProgramClass.resource1 = getResource1();
      javaProgramClass.resource2 = getResource2();
      javaProgramClass.resource3 = getResource3();
      javaProgramClass.controller = controller;
      javaProgramClass.parms = getScriptParams();
      try {
        javaProgramClass.controllerStarted();
      } catch (Exception ex) {
        System.err.println("Could not run controllerStarted method for script "+this.getName());
        printGeneratedProgram(System.err);
        ex.printStackTrace(System.err);
      }
    }
  }

  @Override
  public void controllerExecutionFinished(Controller controller) {
    this.controller = controller;
    if (javaProgramClass != null) {
      javaProgramClass.controller = controller;
      try {
        javaProgramClass.controllerFinished();
      } catch (Exception ex) {
        System.err.println("Could not run controlerFinished method for script "+this.getName());
        printGeneratedProgram(System.err);
        ex.printStackTrace(System.err);
      }
      javaProgramClass.controller = null;
      javaProgramClass.corpus = null;
      javaProgramClass.parms = null;
    }
  }

  @Override
  public void controllerExecutionAborted(Controller controller, Throwable throwable) {
    this.controller = controller;
    if (javaProgramClass != null) {
      javaProgramClass.controller = controller;
      try {
        javaProgramClass.controllerAborted(throwable);
      } catch (Exception ex) {
        System.err.println("Could not run controlerAborted method for script "+this.getName());
        printGeneratedProgram(System.err);
        ex.printStackTrace(System.err);
      }
      javaProgramClass.controller = null;
      javaProgramClass.corpus = null;
      javaProgramClass.parms = null;
    }
  }
  @Sharable
  public void setGlobalsForPr(ConcurrentMap<String,Object> it) {
    globalsForPr = it;
  }
  public ConcurrentMap<String,Object>  getGlobalsForPr() {
    return globalsForPr;
  }
  protected ConcurrentMap<String, Object> globalsForPr;
  
  @Override
  public Resource duplicate(Factory.DuplicationContext dc) throws ResourceInstantiationException {
    JavaScriptingPR res = (JavaScriptingPR) Factory.defaultDuplicate(this, dc);
    int nr = nrDuplicates.addAndGet(1);
    if(res.javaProgramClass != null) {
      res.javaProgramClass.duplicationId = nr;
    }
    return res;
  }
    
}
