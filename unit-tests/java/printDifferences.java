import gate.Annotation;
import gate.AnnotationSet;
import gate.Utils;
import java.util.*;

@Override
public void execute() {
  AnnotationSet shef = doc.getAnnotations("Shef");
  AnnotationSet ref  = doc.getAnnotations("Ref");
  AnnotationSet lfRefs = doc.getAnnotations("Ref_LearningFramework");
  AnnotationSet lfShefs = doc.getAnnotations("LearningFramework");
  if(inputAS.size() == 0) {
    System.out.println("=== UNIT TEST: OK "+doc.getName());
  } else {
    int i = 0;
    for(Annotation ann : inputAS) {
      String t = ann.getType();
      String change = (String)ann.getFeatures().get("_eval.change");
      if(change==null) { change = "??"; }
      System.err.print("=== UNIT TEST: DIFFERENCE ("+change+") in document "+doc.getName()+" ");
      System.err.println("for >"+gate.Utils.cleanStringFor(doc,ann)+
          "< "+t+", ("+gate.Utils.start(ann)+
          ","+gate.Utils.end(ann)+") type="+ann.getType()+" inst="+ann.getFeatures().get("inst")+
          " conf="+ann.getFeatures().get("LF_confidence"));
      /*
      AnnotationSet shefAnns = Utils.getCoextensiveAnnotations(shef,ann);
      AnnotationSet refAnns  = Utils.getCoextensiveAnnotations(ref,ann);
      if(shefAnns.size() == 1 && refAnns.size() == 1) {
        Annotation shefAnn = Utils.getOnlyAnn(shefAnns); 
        Annotation refAnn = Utils.getOnlyAnn(refAnns);       
        for(Object key : shefAnn.getFeatures().keySet()) {
          System.err.println("=== UNIT TEST: Shef/Ref difference for "+key+
            " shef"+shefAnn.getFeatures().get(key)+refAnn.getFeatures().get(key));
        }
      } else {
        // cannot compare features unless exactly one annotation in each set
      }
      */
      AnnotationSet lfRefsCe = Utils.getCoextensiveAnnotations(lfRefs,ann);
      AnnotationSet lfShefsCe = Utils.getCoextensiveAnnotations(lfShefs,ann);
      NavigableMap<String,Annotation> i2asR = new TreeMap<String,Annotation>();
      for(Annotation tmp : lfRefsCe) {
        String inst = (String)tmp.getFeatures().get("inst");
        if(i2asR.containsKey(inst)) {
          System.err.println("Duplicate inst in Refs: "+inst+" off="+Utils.start(ann));
        }
        i2asR.put(inst,tmp);
      }
      NavigableMap<String,Annotation> i2asS = new TreeMap<String,Annotation>();
      for(Annotation tmp : lfShefsCe) {
        String inst = (String)tmp.getFeatures().get("inst");
        if(i2asS.containsKey(inst)) {
          System.err.println("Duplicate inst in Shefs: "+inst+" off="+Utils.start(ann));
        }
        i2asS.put(inst,tmp);
      }
      for(Map.Entry<String,Annotation> entry : i2asS.entrySet()) {
        Annotation s = entry.getValue();
        String inst = entry.getKey();
        System.err.println("== INST Shef inst="+inst+" off="+Utils.start(ann));
        if(i2asR.containsKey(inst)) {
          Annotation r = i2asR.get(inst);
          for(Object key : s.getFeatures().keySet()) {
            if(key.equals("llId")) { continue; }
            Object vs = s.getFeatures().get(key);
            Object vr = r.getFeatures().get(key);
            if((vs != null && vr != null && !vs.equals(vr))||(vs != null && vr == null)||(vs == null && vr != null)) {
              System.err.println("== Feature diff, feat="+key+" ref="+vr+" shef="+vs+" off="+Utils.start(s));
            }
          }
        } else {
          System.err.println("== !!Candidate in Shef but not in Ref: "+inst+" off="+Utils.start(ann));
        }
      }
      for(Map.Entry<String,Annotation> entry  : i2asR.entrySet()) {
        Annotation r = entry.getValue();
        String inst = entry.getKey();
        System.err.println("== INST Ref inst="+inst+" off="+Utils.start(r));
        if(!i2asS.containsKey(inst)) {
          System.err.println("== !!Candidate in Ref but not in Shef: "+inst+" off="+Utils.start(ann));          
        }
      }
      i++;
    }
    if(i==0) {
      System.out.println("=== UNIT TEST: OK "+doc.getName());
    }
  }
}


