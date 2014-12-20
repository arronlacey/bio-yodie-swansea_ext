import gate.Utils;
import gate.util.GateRuntimeException;
import gate.Annotation;
import gate.AnnotationSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

// Small demonstration program. It implements a simple term frequency
// counting algorithm that should also work in a multi-threaded mode
// e.g. when GCP is used.

// What it does:
// = it expects the documents to be tokenized and have Token annotations in 
//   the default annotation set
// = each thread has its own map that maps token strings to a count
// = at init time each thread stores a reference to its own map in the 
//   globalForPr map
// = each thread increments the counts for each token found in its own map
// = when all documents have been processed in cleanupPr (which is always the
//   first cleanup routine to be called), all the maps are added up into the 
//   first one. 

private Map<String,Integer> tokenCounts;

@Override
public void execute() {
  System.out.println("Running for document "+doc.getName()+" started in copy "+duplicationId);
  AnnotationSet tokens = inputAS.get("Token");
  for(Annotation token : tokens) {
    String tokenString = Utils.cleanStringFor(doc,token);
    if(tokenCounts.containsKey(tokenString)) {
      tokenCounts.put(tokenString,tokenCounts.get(tokenString)+1);
    } else {
      tokenCounts.put(tokenString,1);
    }
  }
  System.out.println("Running for document "+doc.getName()+" finished in copy "+duplicationId);
}

@Override
public void initPr() {
  System.out.println("Running initPr() started "+this.getClass()+" in copy "+duplicationId);
  try { Thread.sleep(3000); } catch(Exception ex) { } 
  System.out.println("Running initPr() finished "+this.getClass()+" in copy "+duplicationId);
}

public void init() {
  System.out.println("Running init() started "+this.getClass()+" duplicationId is "+duplicationId);
  tokenCounts = new HashMap<String,Integer>();
  globalsForPr.put("tokenCounts"+duplicationId,tokenCounts);
  try { Thread.sleep(3000); } catch(Exception ex) { } 
  System.out.println("Running init() finished "+this.getClass()+" duplicationId is "+duplicationId);
}

public void cleanup() {
  System.out.println("Running cleanup() started "+this.getClass()+" duplicationId is "+duplicationId);
  try { Thread.sleep(3000); } catch(Exception ex) { } 
  System.out.println("Running cleanup() finished "+this.getClass()+" duplicationId is "+duplicationId);
}

public void cleanupPr() {
  System.out.println("Running cleanupPr started "+this.getClass()+" duplicationId is "+duplicationId);
  // get all the maps: if it is from duplication id 0, use this for accumulation,
  // all others go into a list
  Map<String,Integer> accumulate = null;
  List<Map<String,Integer>> others = new ArrayList<Map<String,Integer>>();
  for(String key : globalsForPr.keySet()) {
    if(key.equals("tokenCounts0")) {
      accumulate = (Map<String,Integer>)globalsForPr.get(key);
    } else if(key.startsWith("tokenCounts")) {
      others.add((Map<String,Integer>)globalsForPr.get(key));
    }
  }
  if(accumulate == null) {
    throw new GateRuntimeException("Could not find tokenCounts0 map");
  }
  for(Map<String,Integer> aMap : others) {
    // add all the counts from aMap to accumulate
    for(String token : aMap.keySet()) {
      if(accumulate.containsKey(token)) {
        accumulate.put(token,accumulate.get(token)+1);
      } else {
        accumulate.put(token,1);
      }
    }
  }
  // log all counts
  for(String token : accumulate.keySet()) {
    logger.info(token+"\t"+accumulate.get(token));
  }
  try { Thread.sleep(3000); } catch(Exception ex) { } 
  System.out.println("Running cleanupPr finished "+this.getClass()+" duplicationId is "+duplicationId);
}


