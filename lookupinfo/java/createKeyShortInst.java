import java.util.*;
import gate.*;
import gate.util.*;


@Override
public void execute() {
  AnnotationSet keyshort = doc.getAnnotations("KeyShortInst");
  keyshort.clear();
  AnnotationSet keys = doc.getAnnotations("Key").get("Mention");
  for(Annotation ann : keys) {
    // copy the annotation to the key4list set, and change the inst feature value first
    FeatureMap fm = Utils.toFeatureMap(ann.getFeatures());
    String inst = (String)fm.get("inst");
    inst=inst.replaceAll("http://dbpedia\\.org/resource/","");
    inst=inst.replaceAll("http://[a-z]+\\.dbpedia\\.org/resource/","");
    fm.put("inst",inst);
    Utils.addAnn(keyshort,ann,"Mention",fm);
  }
}
