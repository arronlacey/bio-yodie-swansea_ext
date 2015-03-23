// A script that uses the Evaluation plugin API to find the 
// maximum possible recall obtainable from the candidate lists
// we have in the lookup list annotations.

// NOTE!!!!!
// Since the Key annotation set contains annotations of type Mention with inst features
// that contain the full URI, but the input AS contains annotations of type Lookup with
// inst features that contain just the resource name, we first create a set "Key4List"
// which has copies of the key annotations with the type and inst feature adjusted. Any
// old annotations in that set get removed.
// CAUTION!!!
// This will NOT work properly once we support different base URIs e.g. to support
// non-english URIs - in that case we cannot just always remove the part before the 
// resource name.

import gate.plugin.evaluation.api.*;
import gate.plugin.evaluation.api.AnnotationDifferTagging.CandidateList;
import gate.plugin.evaluation.resources.*;
import java.util.*;
import gate.*;
import gate.util.*;

private static Set<String> featureSet = new HashSet<String>();
static {
  featureSet.add("inst");
}

private FeatureComparison fcmp = FeatureComparison.FEATURE_EQUALITY;

private EvalStatsTagging allStats;

private String evaluationId = "PleaseProvideAProperEvalId";

@Override
public void controllerStarted() {
  evaluationId = System.getProperty("maxRecall.evalId");
  if(evaluationId == null) {
    evaluationId = "Property_maxRecall.evalId_not_set";  
    //throw new GateRuntimeException("Property maxRecall.evalId needs to be set to the evaluation id!");
  }
  allStats = new EvalStatsTagging();
}

@Override
public void controllerFinished() {
  /*
  EvaluateTagging.outputEvalStatsForType(
    System.out, 
    allStats, 
    "Mention", 
    "Shef", 
    evaluationId);
  */
  System.out.println(evaluationId+" MaxRecall Recall Strict: "+allStats.getRecallStrict());
  System.out.println(evaluationId+" MaxRecall Recall Lenient: "+allStats.getRecallLenient());
  System.out.println(evaluationId+" MaxRecall Precision Strict: "+allStats.getPrecisionStrict());
  System.out.println(evaluationId+" MaxRecall Precision Lenient: "+allStats.getPrecisionLenient());
  System.out.println(evaluationId+" MaxRecall F1.0 Strict: "+allStats.getFMeasureStrict(1.0));
  System.out.println(evaluationId+" MaxRecall F1.0 Lenient: "+allStats.getFMeasureLenient(1.0));
}

@Override
public void execute() {

  // Adapt the key annotations
  AnnotationSet key4list = doc.getAnnotations("Key4List");
  key4list.clear();
  AnnotationSet key = doc.getAnnotations("Key").get("Mention");
  for(Annotation ann : key) {
    // copy the annotation to the key4list set, but change the type from Mention to Lookup and
    // also change the inst feature value first
    FeatureMap fm = Utils.toFeatureMap(ann.getFeatures());
    String inst = (String)fm.get("inst");
    inst=inst.replaceAll("http://dbpedia\\.org/resource/","");
    inst=inst.replaceAll("http://[a-z]+\\.dbpedia\\.org/resource/","");
    fm.put("inst",inst);
    Utils.addAnn(key4list,ann,"Lookup",fm);
  }
  
  AnnotationSet listAnns = inputAS.get("LookupList");
  System.out.println("list anns: "+listAnns.size());
  List<CandidateList> candList =
    AnnotationDifferTagging.createCandidateLists(
                      inputAS, 
                      listAnns,
                      "ids", 
                      "relUriFreqByLabelInWp");
  System.out.println("candidate lists: "+candList.size());                     
  AnnotationDifferTagging differ = AnnotationDifferTagging.calculateEvalStatsTagging4List(
    key4list,
    listAnns,
    candList,
    featureSet,
    fcmp,
    "ids",
    "relUriFreqByLabelInWp",
    //Double.NEGATIVE_INFINITY
    Double.NEGATIVE_INFINITY
    );
  outputAS.clear();
  differ.addIndicatorAnnotations(outputAS,"");
  //System.out.println(differ.getEvalStatsTagging());
  // add the evalstats for this document to the overall evalstats
  allStats.add(differ.getEvalStatsTagging());
}
