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
// which do not overlap with one of the NER annotation types 
// (either from ANNIE or StanfordNER or any of the two) in the default set.
// TODO This should be a config some time, but at the moment we always check
// for either!

HashSet<String> types = new HashSet<String>();

@Override
public void initPr() {
  types.add("Person");
  types.add("PERSON");
  types.add("Location");
  types.add("LOCATION");
  types.add("Organization");
  types.add("ORGANIZATION");
  types.add("MISC");
}


@Override
public void execute() {
  AnnotationSet ners = doc.getAnnotations().get(types);
  ArrayList<Annotation> toDelete = new ArrayList<Annotation>();
  for(Annotation lookup : inputAS.get("Lookup")) {
    // check if the current annotation overlaps with any of the NERs
    AnnotationSet overlaps = gate.Utils.getOverlappingAnnotations(ners,lookup);
    if(overlaps.size() == 0) {
      // no overlaps, remove but also add to the output set a copy!
      outputAS.get(gate.Utils.addAnn(outputAS,lookup,lookup.getType(),lookup.getFeatures())).getFeatures().put("deletedBecause","notOverlapsWithNE");
      toDelete.add(lookup);
    }
  }
  inputAS.removeAll(toDelete);
}