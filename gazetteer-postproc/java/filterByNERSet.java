import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collections;


// Simple filter which removes all Lookup annotations from the input set 
// (and adds a copy to the output set)
// which do not overlap/coextend with one of the NER annotations in the annotation set "NER".
// If there is an overlap we set the "type" feature of the Lookup annotation to the 
// value of the type feature of the NER annotation. 


@Override
public void initPr() {
}


@Override
public void execute() {
  AnnotationSet ners = doc.getAnnotations("NER").get("NER");
  ArrayList<Annotation> toDelete = new ArrayList<Annotation>();
  for(Annotation lookup : inputAS.get("Lookup")) {
    // check if the current annotation overlaps with any of the NERs
    AnnotationSet overlaps = gate.Utils.getOverlappingAnnotations(ners,lookup);
    if(overlaps.size() == 0) {
      // no overlaps, remove but also add to the output set a copy!
      outputAS.get(gate.Utils.addAnn(outputAS,lookup,lookup.getType(),lookup.getFeatures())).getFeatures().put("deletedBecause","notOverlapsWithNEfromSet");
      toDelete.add(lookup);
    } else {
      Annotation ner = overlaps.iterator().next();
      String type = (String)ner.getFeatures().get("type");
      if(type != null) {
        lookup.getFeatures().put("type",type);
      }
    }
  }
  inputAS.removeAll(toDelete);
}