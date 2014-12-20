import gate.trendminer.lodie.utils.LodieUtils;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.List;
import java.util.ArrayList;

// Name of the feature to use for ranking and choosing the best
// candidate. Can be overwritten by setting the java property
// lodie.disambiguation-simple.simple-disamb.featureName
// The minimum score for the feature to generate a non-nil
// mention is 0.05 but configurable with the java property
// lodie.disambiguation-simple.simple-disamb.minScore
// This will not create explicit NIL Mention annotations (if the 
// score is below the threshold or does not exist) by default, but 
// creating NIL Mentions can be enabled using the java property
// lodie.disambiguation-simple.simple-disamb.createNilMentions: true
// If NIL Mention annotations are not created, we create annotations of
// type NilMention instead so we can still see which Mention annotations
// with an empty inst would have been created.
String featureName = "relUriFreqByLabelInWp";
Double minScore = 0.05;
boolean createNilMentions = false;

@Override
public void init() {
  System.out.println("init() called");
  // Override the feature name from the configuration property, 
  // if the property exists;
  String conf = System.getProperty("lodie.disambiguation-simple.simple-disamb.featureName");
  if(conf != null) {
    featureName = conf;
  }
  String minScoreString = System.getProperty("lodie.disambiguation-simple.simple-disamb.minScore");
  if(minScoreString != null) {
    try {
      minScore = Double.parseDouble(minScoreString);
    } catch (Exception ex) {
      throw new GateRuntimeException("Problem converting the minScore setting: "+minScoreString,ex);
    }
  }
  String createNilMentionsString = System.getProperty("lodie.disambiguation-simple.simple-disamb.createNilMentions");
  if(createNilMentionsString != null) {
    if(createNilMentionsString.trim().toLowerCase().equals("false")) {
      createNilMentions = false;
    } else if(createNilMentionsString.trim().toLowerCase().equals("true")) {
      createNilMentions = true;      
    } else {
      throw new GateRuntimeException("Property createNilMentions is neither 'true' nor 'false': "+createNilMentionsString);
    }
  }
}

@Override
public void execute() {
  // go through all the annotations of type "LookupList"  in the input annotation set
  // get the candidates
  // sort the candidates by the feature, descending, and take the best
  for(Annotation listAnn : inputAS.get("LookupList")) {
    List<FeatureMap> cands = LodieUtils.getCandidateList(inputAS,listAnn);
    // last parameter=false: do not include candidates with null values for the feature
    List<FeatureMap> best = new ArrayList<FeatureMap>(); 
    try {
      best = LodieUtils.sortCandidatesDescOn(cands,featureName,1,false);
    } catch (Exception ex) {
      System.err.println("simple-disamb.java: got an exception when trying to sort candidates on "+featureName);
      System.err.println("Candidates: \n");
      for(FeatureMap fmtmp : cands) { 
        System.err.println("CAND: "+LodieUtils.toStringFeatureMap(fmtmp,featureName)+"\n");
      }
    }
    FeatureMap bestFm = null;
    boolean isNull = false;
    if(best.size() >= 1) {
      // get the 0-th one, if there is more than one, this will be a random one
      bestFm = best.get(0);
      // if the feature value of the best one is less than the minScore, set isNull
      Number val = (Number)bestFm.get(featureName);
      // this val MUST be non-null because we prevented null values from getting included earlier!
      if(val.doubleValue() < minScore) {
        isNull = true;
      }
    }
    // we create a NIL if we have to and if isNull is true or bestFm is null.
    if(isNull || bestFm == null) {
      // we have to create a Mention annotation which is a Nil or 
      // a NilMention annotation (if we do not want explicit NIL Mention annotations)
      String type="Mention";
      if(!createNilMentions) {
        type = "NilMention";
      }
      FeatureMap newFm = Factory.newFeatureMap();
      if(bestFm == null) {
        newFm.put("inst","");
        newFm.put("nilReason","nothing found"); 
      } else {
        newFm.putAll(bestFm);
        newFm.put("bestInst",newFm.get("inst"));
        newFm.put("inst","");
        newFm.put("nilThreshold",minScore);
        newFm.put("nilReason","score<minscore");
      }
      Utils.addAnn(outputAS,listAnn,type,newFm);
    } else {
      Utils.addAnn(outputAS,listAnn,"Mention",Utils.toFeatureMap(bestFm));
    }
  }
}
