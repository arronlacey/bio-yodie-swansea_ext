import gate.Annotation;

@Override
public void execute() {
  if(inputAS.size() == 0) {
    System.out.println("=== UNIT TEST: OK "+doc.getName());
  } else {
    int i = 0;
    for(Annotation ann : inputAS) {
      String t = ann.getType();
      if(t.startsWith("Mention_Res") || t.startsWith("Mention_Ref")) {
        // ignore those, these are indicator annotations for the response and reference sets
      } else {
        // but the rest are indicator annotations indicating change
        String change = (String)ann.getFeatures().get("_eval.change");
        if(change==null) { change = "??"; }
        System.err.print("=== UNIT TEST: DIFFERENCE ("+change+") in document "+doc.getName()+" ");
        System.err.println("for >"+gate.Utils.cleanStringFor(doc,ann)+
          "< "+t+", ("+gate.Utils.start(ann)+
          ","+gate.Utils.end(ann)+" inst="+ann.getFeatures().get("inst"));
        i++;
      }
    }
    if(i==0) {
      System.out.println("=== UNIT TEST: OK "+doc.getName());
    }
  }
}


