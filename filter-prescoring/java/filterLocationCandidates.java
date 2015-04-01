
import gate.trendminer.lodie.utils.LodieUtils;
import gate.trendminer.lodie.utils.Pair;
import gate.creole.disambiguation.fastgraph.FastGraphLR;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import com.jpetrak.gate.jdbclookup.JdbcLR;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Array;

// TODO
// = filter co-references: for any pair where we do some filtering, remember for 
//   both the first and second part of the pair the lookuplist with the reduced candidate list.
//   If there are other mentions in the document, reduce those lists too.
//   UNSURE: there may be mentions of the second part of the pair which are not identical,
//   e.g. by using an abbreviation for a US state instead of the full name. In those cases
//   it may be useful if we could use the reduced candidate list for those too, but this
//   is not done at the moment
// = once we have processed the pairs and also the co-references, we should be 
//   able to remove all other contained or overlapping LookupLists

private FastGraphLR graph;

@Override
public void execute() {
  // get all the LookupIn
  List<Annotation> lls = inputAS.get("LookupListPairTwo").inDocumentOrder(); 
  // before we do the actual processing, remove any LookupListPairTwo annotations which
  // are:
  // = fully contained and shorter than the one we have
  // = overlapping with this one, but starting after we start
  Set<Annotation> toRemove = new HashSet<Annotation>();
  for(Annotation ll : lls) {
    AnnotationSet overlappings = gate.Utils.getOverlappingAnnotations(inputAS,ll,"LookupListPairTwo");
    for(Annotation overlapping : overlappings) {
      long start = gate.Utils.start(ll);
      int len = gate.Utils.length(ll);
      if(gate.Utils.start(overlapping) > start) {
        toRemove.add(overlapping);
      } else if(gate.Utils.start(overlapping) == start && gate.Utils.length(overlapping) < len) {
        toRemove.add(overlapping);
      }
    }
  }
  lls = null;
  inputAS.removeAll(toRemove);
  AnnotationSet llpts = inputAS.get("LookupListPairTwo");
  AnnotationSet llpos = inputAS.get("LookupListPairOne");
  
  int nfiltered = 0;
  Map<String,Integer> filteredStrings = new HashMap<String,Integer>();
  for(Annotation ll : llpts) {
    FeatureMap fm = ll.getFeatures();
    Annotation ann1 = inputAS.get((Integer)fm.get("firstId"));
    Annotation ann2 = inputAS.get((Integer)fm.get("secondId"));
    // check if there is an overlapping LookupListPairOne annotation
    AnnotationSet overlappings = gate.Utils.getOverlappingAnnotations(llpos,ll);
    if(overlappings.isEmpty()) {
      //System.out.println("Have a NON overlapping one: "+gate.Utils.stringFor(doc,ll));
      // no overlapping annotations
      // lets see if we can find pairs of candidates which are directly related. 
      List<FeatureMap> cands1 = LodieUtils.getCandidateList(inputAS,ann1);
      List<FeatureMap> cands2 = LodieUtils.getCandidateList(inputAS,ann2);
      // filter the cands: only if there is a airp or dbp interesting class of dbpo:PupulatedPlace
      List<FeatureMap> filtered1 = LodieUtils.filterByInterestingClass(cands1,"dbpo:PopulatedPlace");
      List<FeatureMap> filtered2 = LodieUtils.filterByInterestingClass(cands2,"dbpo:PopulatedPlace");
      // the rest is tried only  if there is at least one candidate in each filtered list      
      if(filtered1.size() > 0 && filtered2.size() > 0) {
        Pair<Set<FeatureMap>,Set<FeatureMap>> ret = keepRelatedCandidates(filtered1,filtered2);
        Set<FeatureMap> inrel1 = ret.value1;
        Set<FeatureMap> inrel2 = ret.value2;
        // if we have uris left which are in a relation for both lists, reduce the lists
        // to just those uris
        if(inrel1.size() > 0 && inrel2.size() > 0) {
          nfiltered += LodieUtils.keepCandidateAnnsByCollection(inputAS,ann1,inrel1,"inst");
          nfiltered += LodieUtils.keepCandidateAnnsByCollection(inputAS,ann2,inrel2,"inst");
          filteredStrings.put(Utils.stringFor(doc,ann1),ann1.getId());
          filteredStrings.put(Utils.stringFor(doc,ann2),ann2.getId());
          ann1.getFeatures().put("filterLocationCandidates",true);
          ann2.getFeatures().put("filterLocationCandidates",true);
        } else {
          //System.err.println("No related location candidates for both pairs of PairTwo, ann1,1nn2:");
          //LodieUtils.logListAnn(System.out,"Annotation ann1:",inputAS,ann1,"inst","airpInterestingClass","dbpInterestingClass");
          //LodieUtils.logListAnn(System.out,"Annotation ann2:",inputAS,ann2,"inst","airpInterestingClass","dbpInterestingClass");
        }
      } else {
        // otherwise leave everything as is but log a message
        //System.err.println("No location candidates for both pairs of PairTwo: ann1,1nn2");
        //LodieUtils.logListAnn(System.out,"Annotation ann1:",inputAS,ann1,"inst","airpInterestingClass","dbpInterestingClass");
        //LodieUtils.logListAnn(System.out,"Annotation ann2:",inputAS,ann2,"inst","airpInterestingClass","dbpInterestingClass");
      }
    } else { // this is a LookupListPairTwo without an overlapping LookupListPairOne
      //System.out.println("Have a overlapping one: "+gate.Utils.stringFor(doc,ll));
      //System.out.println("  number of overlapping:"+overlappings.size());
      // check if there is just one and if that one is coextensive
      // if not, just ignore the whole situation and leave everything as it is, but log a message
      if(overlappings.size() == 1 && gate.Utils.getOnlyAnn(overlappings).coextensive(ll)) {
        //System.out.println("Have a single overlapping and coextensive one: "+gate.Utils.stringFor(doc,ll));
        // get all the location URIs from the overlapping PairOne annotation
        Annotation one = gate.Utils.getOnlyAnn(overlappings); 
        Integer llid = (Integer)one.getFeatures().get("singleId"); 
        one = inputAS.get(llid); // get the LookupList annotation
        List<FeatureMap> cands = LodieUtils.getCandidateList(inputAS,one);
        List<FeatureMap> filtered = LodieUtils.filterByInterestingClass(cands,"dbpo:PopulatedPlace");
        if(filtered.size() > 0) {
          //System.out.println("Found location candidates: "+gate.Utils.stringFor(doc,ll));
          // Now try and filter the candidates from the first contained annotation to the filtered
          // candidates from the One annotation. 
          List<FeatureMap> ann1Cands = LodieUtils.getCandidateList(inputAS,ann1);
          LodieUtils.keepCandidatesByCollection(ann1Cands,filtered,"inst");
          if(ann1Cands.size() > 0) {
            //System.out.println("Have candidates shared with single: "+gate.Utils.stringFor(doc,ll));
            // once we are here, we will remove the longer ann and reduce the first pair candidates
            // to the filtered ones
            //LodieUtils.logListAnn(System.out,"Should be removing: ",inputAS,one,"inst");
            LodieUtils.removeListAnns(inputAS,one);
            nfiltered += LodieUtils.keepCandidateAnnsByCollection(inputAS,ann1,ann1Cands,"inst");
            filteredStrings.put(Utils.stringFor(doc,ann1),ann1.getId());
            ann1.getFeatures().put("filterLocationCandidates",true);
            // now do something about the second annotation: first reduce the candidate list 
            // to just the locations. If this list is non-empty, try to reduce it further by using
            // the relations.
            List<FeatureMap> ann2Cands = LodieUtils.getCandidateList(inputAS,ann2);
            ann2Cands = LodieUtils.filterByInterestingClass(ann2Cands,"dbpo:PopulatedPlace");
            if(ann2Cands.size() > 0) {
              // try to further reduce by relations
              Pair<Set<FeatureMap>,Set<FeatureMap>> ret = keepRelatedCandidates(ann1Cands,ann2Cands);
              Set<FeatureMap> inrel1 = ret.value1;
              Set<FeatureMap> inrel2 = ret.value2;
              // if we have uris left which are in a relation for both lists, reduce the lists
              // to just those uris
              if(inrel1.size() > 0 && inrel2.size() > 0) {
                nfiltered += LodieUtils.keepCandidateAnnsByCollection(inputAS,ann1,inrel1,"inst");
                nfiltered += LodieUtils.keepCandidateAnnsByCollection(inputAS,ann2,inrel2,"inst");
                filteredStrings.put(Utils.stringFor(doc,ann2),ann2.getId());
                ann2.getFeatures().put("filterLocationCandidates",true);
              } else {
                System.err.println("Overlapping, but no common locations: ann1,1nn2");
                LodieUtils.logListAnn(System.out,"Annotation ann1:",inputAS,ann1,"inst","airpInterestingClass","dbpInterestingClass");
                LodieUtils.logListAnn(System.out,"Annotation ann2:",inputAS,ann2,"inst","airpInterestingClass","dbpInterestingClass");
              }
            } else {
              // If it is empty, WHAT DO? For now we leave the original list and log this
              System.err.println("Overlapping, but no location in second ann: ann2");
              LodieUtils.logListAnn(System.out,"Annotation ann1:",inputAS,ann2,"inst","airpInterestingClass","dbpInterestingClass");
            }
          } else {
            System.err.println("Overlapping, but no match in first ann: one, ann1"); 
            LodieUtils.logListAnn(System.out,"Annotation ann1:",inputAS,one,"inst","airpInterestingClass","dbpInterestingClass");
            LodieUtils.logListAnn(System.out,"Annotation ann2:",inputAS,ann1,"inst","airpInterestingClass","dbpInterestingClass");
          }
        } else {
          System.err.println("Overlapping, but no locations in PairOne: one");        
          LodieUtils.logListAnn(System.out,"Annotation ann1:",inputAS,one,"inst","airpInterestingClass","dbpInterestingClass");
        }
      } else {
        //System.err.println("Overlapping, but not coextensive or multi. ann1,ann2 for doc/ll:"+doc.getName()+"/"+ll);
        //LodieUtils.logListAnn(System.out,"Annotation ann1:",inputAS,ann1,"inst","airpInterestingClass","dbpInterestingClass");
        //LodieUtils.logListAnn(System.out,"Annotation ann2:",inputAS,ann2,"inst","airpInterestingClass","dbpInterestingClass");
      }
    }
  }
  // Now try an co-reference the filtered annotations: if we find another LookupList with the string
  // identical to something we have filtered, filter that list. 
  for(Annotation ll : inputAS.get("LookupList")) {
    Boolean isProcessed = (Boolean)ll.getFeatures().get("filterLocationCandidates");
    if(isProcessed == null || isProcessed == false) {
      String mention = Utils.stringFor(doc,ll);
      Integer theId = filteredStrings.get(mention);
      if(theId != null) {
        System.out.println("Find co-referenced location: "+mention+" filtering by id: "+theId);
        nfiltered += LodieUtils.keepCandidateAnnsByListAnn(inputAS,ll,inputAS.get(theId),"inst");
      }
    }
  }
  System.out.println(doc.getName()+": filterLocationCandidates filtered: "+nfiltered);
}

public boolean isInRelation(String uri1, String uri2) {
  int n = graph.getDirectRelationCount(uri1,uri2);
  if(n == 0) {
    n = graph.getDirectRelationCount(uri2,uri1);
  }
  boolean ret = (n>0);
  //System.out.println("DEBUG: checked "+uri1+" / "+uri2+" result: "+ret);
  return ret;
}

public Pair<Set<FeatureMap>,Set<FeatureMap>> keepRelatedCandidates(List<FeatureMap> cands1, List<FeatureMap> cands2) {
  Set<FeatureMap> inrel1 = new HashSet<FeatureMap>();
  Set<FeatureMap> inrel2 = new HashSet<FeatureMap>();
  for(FeatureMap fm1 : cands1) {
    for(FeatureMap fm2 : cands2) {
      String inst1 = (String)fm1.get("inst");
      String inst2 = (String)fm2.get("inst");
      if(inst1 == null) {
        System.err.println("filterLocationCandidates, doc="+doc.getName()+": inst1 is null! fm="+fm1);
      } else if(inst2 == null) {
        System.err.println("filterLocationCandidates, doc="+doc.getName()+": inst2 is null! fm="+fm2);
      } else if(isInRelation(inst1,inst2)) {
        /*
        System.out.println("Related: "+
          LodieUtils.toStringFeatureMap(fm1,"inst","airpInterestingClass","dbpInterestingClass")+
          " and "+
          LodieUtils.toStringFeatureMap(fm2,"inst","airpInterestingClass","dbpInterestingClass"));
        */
        inrel1.add(fm1);
        inrel2.add(fm2);
      }
    }
  }
  return new Pair<Set<FeatureMap>,Set<FeatureMap>>(inrel1,inrel2);
  /*
  ArrayList<Set<FeatureMap>> ret = new ArrayList<Set<FeatureMap>>(2);
  ret.add(inrel1);
  ret.add(inrel2);  
  return ret;
  */
}


@Override
public void init() {
  graph = (FastGraphLR)resource1;
  System.out.println("Have the fastgraph resource: "+graph);
}

