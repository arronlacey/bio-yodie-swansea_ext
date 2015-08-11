// A script that uses the Evaluation plugin API to find the 
// maximum possible recall obtainable from the candidate lists
// we have in the lookup list annotations.
// NOTE: this will limit the responses to just those which are overlapping
// with the key. If more than one list is overlapping with a key, we merge 
// the candidates of all of them into one list. The idea is that we want 
// to find the theoritical maximum recall, but in limiting the responses to
// just the key spots, we also want a rough estimate what the best achievable
// precision and f-measure for the max recall situation could be.
// The set with the filtered and merged lookup list annotations is EvalMaxRecalTmp

// This will also configure the Evaluation API so annotations are placed into 
// the outputAS that reflect correct, partial correct or incorrect/missing annotations. 

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
import gate.trendminer.lodie.utils.LodieUtils;

private static Set<String> featureSet = new HashSet<String>();
static {
  featureSet.add("inst");
}

private FeatureComparison fcmp = FeatureComparison.FEATURE_EQUALITY;

private EvalStatsTagging allStats;

private String evaluationId = "PleaseProvideAProperEvalId";

private AnnotationTypeSpecs annSpecs;

@Override
public void controllerStarted() {
  evaluationId = System.getProperty("maxRecall.evalId");
  if(evaluationId == null) {
    evaluationId = "Property_maxRecall.evalId_not_set";  
    //throw new GateRuntimeException("Property maxRecall.evalId needs to be set to the evaluation id!");
  }
  allStats = new EvalStatsTagging4Score();
  ArrayList<String> types = new ArrayList<String>();
  types.add("Lookup=LookupList");
  annSpecs = new AnnotationTypeSpecs(types);
}

@Override
public void controllerFinished() {
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
  AnnotationSet keys = doc.getAnnotations("Key").get("Mention");
  for(Annotation ann : keys) {
    // copy the annotation to the key4list set, but change the type from Mention to Lookup and
    // also change the inst feature value first
    FeatureMap fm = Utils.toFeatureMap(ann.getFeatures());
    String inst = (String)fm.get("inst");
    inst=inst.replaceAll("http://dbpedia\\.org/resource/","");
    inst=inst.replaceAll("http://[a-z]+\\.dbpedia\\.org/resource/","");
    fm.put("inst",inst);
    Utils.addAnn(key4list,ann,"Lookup",fm);
  }
  
  // Now that we have the Key4List set, create the temporary response set
  // EvalMaxRecallTmp: this will first be a copy of the original inputAS
  // LookupList annotations, then in an second step, 
  
  AnnotationSet listAnns = inputAS.get("LookupList");
  AnnotationSet evalAnns = doc.getAnnotations("EvalMaxRecall");
  for(Annotation keyAnn : keys) {
    // get all the overlapping list annotations
    AnnotationSet overlaps = Utils.getOverlappingAnnotations(listAnns,keyAnn);
    // copy them all to the temporary set, but if there is more than one,
    // immediately merge all others into the first
    int firstid = -1;
    Annotation firstAnn = null;
    for(Annotation overlap : overlaps) {
      int id = LodieUtils.copyListAnn(overlap,inputAS,evalAnns);
      if(firstAnn == null) {
        firstid = id;
        firstAnn = evalAnns.get(firstid);
      } else {
        // merge the annotation with id into the annotation with firstid
        Annotation otherAnn = evalAnns.get(id);
        LodieUtils.mergeListAnns(evalAnns, firstAnn, otherAnn, true, "inst");
      }
    }
  }
  
  AnnotationSet evalListAnns = evalAnns.get("LookupList");
  
  List<CandidateList> candList =
    AnnotationDifferTagging.createCandidateLists(
                      evalAnns, 
                      evalListAnns,
                      "ids", 
                      "relUriFreqByLabelInWp",
                      "Lookup",
                      false,
                      "",
                      "ids");
  System.out.println("candidate lists: "+candList.size());                     
  AnnotationDifferTagging differ = AnnotationDifferTagging.calculateEvalStatsTagging4List(
    key4list,
    evalListAnns,
    candList,
    featureSet,
    fcmp,
    "ids",
    "relUriFreqByLabelInWp",
    Double.NEGATIVE_INFINITY,
    null,
    annSpecs
    );
  outputAS.clear();
  differ.addIndicatorAnnotations(outputAS,"");
  //System.out.println(differ.getEvalStatsTagging());
  // add the evalstats for this document to the overall evalstats
  allStats.add(differ.getEvalStatsTagging());
}
