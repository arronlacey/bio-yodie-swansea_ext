import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.*;

@Override
public void execute() {
  // clear all the document features
  doc.getFeatures().clear();
  // clear all annotation sets apart from "Shef" and "Key"
  doc.getAnnotations().clear();
  List<String> setnames = new ArrayList<String>();
  setnames.addAll(doc.getAnnotationSetNames());
  for(String setname : setnames) {
    if(!(setname.equals("Shef") || setname.equals("Key") || setname.equals("Ref_LearningFramework"))) {
      doc.getAnnotations(setname).clear();
      doc.removeAnnotationSet(setname);
    }
  }
  // create copies of the annotations in Shef in Ref, using the same id
  AnnotationSet ref = doc.getAnnotations("Ref");
  for(Annotation shef : doc.getAnnotations("Shef")) {
    int id = shef.getId();
    try {
      ref.add(id,Utils.start(shef),Utils.end(shef),shef.getType(),Utils.toFeatureMap(shef.getFeatures()));    
    } catch (Exception ex) {
      throw new GateRuntimeException("Could not add annotation: "+shef,ex);
    }
  }
  // now remove the Shef set
  doc.getAnnotations("Shef").clear();
  doc.removeAnnotationSet("Shef");  
}
