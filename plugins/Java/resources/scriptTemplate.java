// Template for the Java "script"

// First import all the packages you need, anything
// on the plugin's class path should worl
import gate.Utils;

// everything that should get executed when a document is 
// processed must be implented in the following method. 
// If the PR is being run in a non-corpus controller,
// the doc and corpus fields may be null!
public void execute() {
  System.out.println("We are processing ...");
  // you can access doc, corpus, controller, inputAS, inputASName,
  // outputAS, outputASName, and parms.
  // In addition globalsForScript is a Map<String,Object> for 
  // storing data that is shared between duplicated instances of the 
  // same script and globalsForAll is a map shared between all 
  // scipts.
  System.out.println("Document is: "+nameOrNull(doc));
  System.out.println("Controller is: "+nameOrNull(controller));
  System.out.println("Corpus is: "+nameOrNull(corpus));
  System.out.println("inputASName is: "+inputASName);
  System.out.println("outputASName is: "+outputASName);
  System.out.println("parms is: "+parms);
}


// if necessary, the following methods can be used to execute
// code when a controller starts, finishes or aborts:
public void controllerStarted() { }
public void controllerFinished() {}
public void controllerAborted(Throwable thr) { }


// you can implement any number of addition methods ...
private String nameOrNull(Resource res) {
  if(res == null) {
    return null;
  } else {
    return res.getName();
  }
}
