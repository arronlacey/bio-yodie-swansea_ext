import gate.trendminer.lodie.utils.LodieUtils;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.Set;
import java.util.HashSet;

@Override
public void execute() {
  AnnotationSet lls = inputAS.get("LookupList");
  Set<Annotation> toRemove = new HashSet<Annotation>();
  int nremoved = 0;
  // First, find annotations which are contained whithin another annotation
  // or which overlap with another annotation but start later. 
  // We should never get coextensive annotations here because we should have merged
  // those into single list annotations earlier! 
  for(Annotation ll : lls) {
    AnnotationSet containeds = Utils.getContainedAnnotations(inputAS,ll,"LookupList");
    for(Annotation contained : containeds) {
      if(!contained.coextensive(ll)) {
        toRemove.add(contained);
      } else {
        // just ignore if the contained ann is the same as which we used to start ...
        if(ll.getId() != contained.getId()) {
          throw new GateRuntimeException(doc.getName()+": Found coextensive list annotations: "+ll+" / "+contained);
        }
      }
    }
    AnnotationSet overlappings = Utils.getOverlappingAnnotations(inputAS,ll,"LookupList");
    for(Annotation overlapping : overlappings) {
      if(Utils.start(overlapping) > Utils.start(ll)) {
        // TODO: we should already previously filter stuff like
        // [the Name]
        //     [Name Thing]
        // or
        // [some Stuff]
        //      [Stuff Thing]
        // to remove the first!
        // For now we do this here by only checking a few special cases
        // First get the initial span String
        String prefix = Utils.cleanStringFor(doc,Utils.start(ll),Utils.start(overlapping));
        prefix = prefix.trim().toLowerCase();        
        if(prefix.isEmpty() || prefix.equals("the") || prefix.equals("a") || prefix.equals("an")) {
          toRemove.add(ll); 
        } else {
          toRemove.add(overlapping);      
        }
      }
    }
  }
  for(Annotation ann : toRemove) {
    nremoved += LodieUtils.removeListAnns(inputAS,ann);
  }
  System.out.println(doc.getName()+": filterOverlapping annotations filtered: "+nremoved);
}
