import gate.trendminer.lodie.utils.LodieUtils;
import gate.trendminer.lodie.utils.Pair;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


// Check all the LookupListAbbrv annotations: there are two types, list and string. 
// If we have a list type, reduce the candidate lists of both the definition list
// and the abbreviation lists to just the shared candidates
// If we have a string type, reduce the candidate list of the the abbreviation list
// to those candidates where the label list matches the abbreviation definition.

@Override
public void execute() {
  AnnotationSet llAbbrvs = inputAS.get("LookupListAbbrv");
  // this maps the abbreviation string to the Integer id of the processed LookupList
  // annotation if filtering was successful, and to -1 if the LookupList has been
  // completely removed
  Map<String,Integer> filtered = new HashMap<String,Integer>();
  int nfiltered = 0;
  for(Annotation llAbbrv : llAbbrvs) {
    //System.out.println("Processing abbreviation: "+llAbbrv+"/"+gate.Utils.stringFor(doc,llAbbrv));
    FeatureMap fm_llAbbrv = llAbbrv.getFeatures();
    String type = (String)fm_llAbbrv.get("type");
    if(type.equals("list")) {
      Annotation ll4Def = inputAS.get((Integer)fm_llAbbrv.get("llDefId"));
      //System.out.println("ll4Def is "+ll4Def);
      Annotation ll4Abbrv = inputAS.get((Integer)fm_llAbbrv.get("llId"));    
      //System.out.println("ll4Abbrv is "+ll4Abbrv);
      // create the list of common candidates
      List<FeatureMap> common = LodieUtils.intersectCandidates(inputAS,ll4Def,ll4Abbrv,"inst");
      if(common.size() == 0) {
        // nothing left: remove and log
        System.out.println("No common candidates for Abbreviation and definition: "+doc.getName()+"/"+ll4Abbrv+"/"+ll4Def);
        filtered.put(Utils.stringFor(doc,ll4Abbrv),ll4Abbrv.getId());        
        LodieUtils.logListAnn(System.out,"Abbreviation",inputAS,ll4Abbrv,"inst");
        LodieUtils.logListAnn(System.out,"Definition",inputAS,ll4Def,"inst");
        nfiltered += LodieUtils.removeListAnns(inputAS,ll4Def);
        nfiltered += LodieUtils.removeListAnns(inputAS,ll4Abbrv);
      } else {
        // reduce the candidate lists
        filtered.put(Utils.stringFor(doc,ll4Abbrv),ll4Abbrv.getId());
        nfiltered += LodieUtils.keepCandidateAnnsByCollection(inputAS,ll4Def,common,"inst");
        nfiltered += LodieUtils.keepCandidateAnnsByCollection(inputAS,ll4Abbrv,common,"inst");
      }
    } else {
      // must be type string
      // TODO: at the moment this gets ignored. If the definition would be in dbpedia,
      // we would have annotated it and found it. So the only way how we can filter
      // this more is to find alternate strings which are in dbpedia and somewhat
      // match the definition we have.
      
      Annotation ll4Abbrv = inputAS.get((Integer)fm_llAbbrv.get("llId")); 
      Annotation ann4Def = inputAS.get((Integer)fm_llAbbrv.get("defId"));
      System.err.println("TBD: process abbreviation with non-list definition: "+
        doc.getName()+"/"+
        gate.Utils.cleanStringFor(doc,ll4Abbrv)+"/"+
        gate.Utils.cleanStringFor(doc,ann4Def));
      //System.err.println("TBD: process abbreviation with non-list definition: "+doc.getName()+"/"+ll4Abbrv+"/"+ann4Def);
      //System.err.println("Definition string: "+gate.Utils.stringFor(doc,ann4Def));
      //LodieUtils.logListAnn(System.out,"Abbreviation",inputAS,ll4Abbrv,"inst");     
    }
  } // for all LookupListAbbrv ...
  // now move the reduced candidate lists or the removed lookuplists over to other mentions
  // of the filtered annotations. This is done by looking at all the TmpPossibleAbbrv and 
  // adjusting/deleting their corresponding LookupList annotations if they are not something we 
  // already looked at and if they correspond to something we have looked at. The whole processing
  // is only done if we have found anything to filter or delete at all
  if(filtered.size() > 0) {
    for(Annotation tmpAbbrv : inputAS.get("TmpPossibleAbbrv")) {
      // first check if this tmpAbbrv is overlapping with a LookupListAbbrv: if yes,
      // we already have processed it and it gets ignored
      AnnotationSet tmp = Utils.getOverlappingAnnotations(inputAS,tmpAbbrv,"LookupListAbbrv");
      // if there is no overlap, see if we have filtered/deleted candidates for this abbreviation 
      if(tmp.size() == 0) {
        String abbrvString = Utils.stringFor(doc,tmpAbbrv);
        Integer llId = filtered.get(abbrvString);
        System.out.println("Trying to co-reference abbreviation: "+abbrvString+" found id "+llId);
        if(llId != null) {
          // get the LookupList annotation for our tmpAbbrv
          Annotation llTmpAbbrv = inputAS.get((Integer)tmpAbbrv.getFeatures().get("llId"));
          if(llId == -1) {  // we have deleted the whole LookupList, delete here too!
            nfiltered += LodieUtils.removeListAnns(inputAS,llTmpAbbrv);
          } else {
            // otherwise just filter the candidate list to match the one at the definition
            nfiltered += LodieUtils.keepCandidateAnnsByListAnn(inputAS,llTmpAbbrv,inputAS.get(llId),"inst");
          }
        }
      }  
    }
  }
  System.out.println(doc.getName()+": filterAbbreviations filtered: "+nfiltered);
}