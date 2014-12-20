
// Correct the ids lists of list annotations: make sure that the 
// id lists only contain ids of annotations that still exist
import gate.*;
import java.util.List;
import java.util.Iterator;

@Override
public void execute() {
  AnnotationSet lls = inputAS.get("LookupList");
  int nremoved = 0;
  for(Annotation ll : lls) {
    FeatureMap fm = ll.getFeatures();
    List<?> ids = (List<?>)fm.get("ids");
    Iterator<?> it = ids.iterator();
    while(it.hasNext()) {
      Object idObj = it.next();
      if(idObj instanceof Integer) {
        int id = (Integer)idObj;
        Annotation ann = inputAS.get(id);
        if(ann == null) {
          it.remove();
          nremoved++;
        }
      } else {
        System.err.println("Not a numeric id in LookupList: "+ll);
      }
    }
  }
  System.out.println(doc.getName()+": Number of non-existing ids removed: "+nremoved);
  System.out.println(doc.getName()+": Total number of Lookups now "+inputAS.get("Lookup").size());
}