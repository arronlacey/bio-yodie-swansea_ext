import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collections;
import gate.trendminer.lodie.utils.LodieUtils;
import gate.trendminer.lodie.utils.Pair;

// For each LookupList annotation in the inputAS, find all coextensive annotations
// and merge them into a single annotation

@Override
public void execute() {
  AnnotationSet lls = inputAS.get("LookupList");
  Set<Annotation> seen = new HashSet<Annotation>();
  List<Pair<Annotation,Annotation>> toDo = new ArrayList<Pair<Annotation,Annotation>>();
  for(Annotation ll : lls) {
    //logger.info("Checking: "+ll);
    if(seen.contains(ll)) {
      continue;
    }
    AnnotationSet coexts = gate.Utils.getCoextensiveAnnotations(lls,ll);
    for(Annotation coext : coexts) {
      //logger.info("Coext: "+coext);
      if(coext.getId() != ll.getId()) {
        //logger.info("Other: "+coext);
        seen.add(coext);
        toDo.add(new Pair(ll,coext));
      }
    }
  }
  for(Pair<Annotation,Annotation> pair : toDo) {
    //logger.info("Merge: "+pair.value1+"/"+pair.value2);
    LodieUtils.mergeListAnns(inputAS,pair.value1,pair.value2,true,"inst");
    //logger.info("Merge after: "+pair.value1);
  }
}
