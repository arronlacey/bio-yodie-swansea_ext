// Simple approach to resolve situations where a possessive form 
// overlaps with another shorter form which does not contain the possessive suffix,
// e.g. "Japan's" overlapping with "Japan". In this cases, we merge the two candidate
// lists into the candidate list fro the longer lookup list. Each list still refers
// to Lookup annotations of different lengths, so if one is picked from the merged
// list it could still be for the longer or shorter spot. 
// NOTE: this will only work properly if all later stages will handle scoring and
// selections based on the id list of the lookuplist and  not based on finding 
// coextensive lookup annotations. 


import gate.trendminer.lodie.utils.LodieUtils;
import gate.trendminer.lodie.utils.Pair;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

@Override
public void execute() {
  AnnotationSet lls = inputAS.get("LookupList");
  List<Pair<Annotation,Annotation>> toDo = new ArrayList<Pair<Annotation,Annotation>>();  
  int nmerged = 0;
  for(Annotation ll : lls) {
    String string = Utils.stringFor(doc,ll);
    // TODO: it may be better to replace this with code that checks if the ll annotations
    // ends with a token that has a pos tag that indicates a possessive (POS). 
    if(string.endsWith("'s")) {      
      AnnotationSet containeds = Utils.getContainedAnnotations(lls,ll);
      // go through all the contained ones. At this point we may have the following cases:
      // = coextensive: unless it is the same annotation, this should not happen, but we ignore this
      // = shorter and starting at the same offset: this is the case were we want to merge
      //   the shorter one into the longer one. Since doing this right now would modify the 
      //   set we iterate over we have to remember the pair and do this later.
      // = shorter and not starting at the same offset: we ignore this here, but this will later
      //   get filtered out anyway
      for(Annotation contained : containeds) {
        logger.info(doc.getName()+": found a contained for a possessive "+contained+" / "+ll);
        if(Utils.end(contained) <= (Utils.end(ll)-2) && Utils.start(contained) == Utils.start(ll)) {
          logger.info(doc.getName()+": is shorter, adding for merge");
          toDo.add(new Pair(ll,contained));
        }
      }
    }
  }
  for(Pair<Annotation,Annotation> pair : toDo) {
    logger.info("Merge: "+pair.value1+"/"+pair.value2);
    LodieUtils.mergeListAnns(inputAS,pair.value1,pair.value2,true,"inst");     nmerged += 1;
    //logger.info("Merge after: "+pair.value1);
  }
  logger.info(doc.getName()+": filterPossessives annotations merged: "+nmerged);
}
