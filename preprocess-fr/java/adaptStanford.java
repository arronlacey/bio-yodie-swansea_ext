import java.util.*;
import gate.*;


@Override
public void execute() {
  // first map all the POS tags we are interested in from STTS to Penn  
  // this is a hack and not really possible properly!!! 
  // SSTS does not care about singular plural whie penn does but ssts is much more
  // detailed about verbs than penn

  for(Annotation token : inputAS.get("Token")) {
    FeatureMap fm = token.getFeatures();
    String cat = (String)fm.get("category");
    if(cat != null) {
      // normal noun
      if(cat.startsWith("nc")) {
        // use PENN
        cat = "NN";
      } else if(cat.startsWith("np")) {
        cat = "NNP";
      } 
      // TODO: add as needed or change in some other way!
    }
    fm.put("category",cat);
  }
  Set<Annotation> toDelete = new HashSet<Annotation>();
  for(Annotation a : inputAS.get("PERS")) { 
    toDelete.add(a);
    Utils.addAnn(outputAS,a,"PERSON",Utils.toFeatureMap(a.getFeatures()));
  }
  for(Annotation a : inputAS.get("LUG")) { 
    toDelete.add(a);
    Utils.addAnn(outputAS,a,"LOCATION",Utils.toFeatureMap(a.getFeatures()));
  }
  for(Annotation a : inputAS.get("ORG")) { 
    toDelete.add(a);
    Utils.addAnn(outputAS,a,"ORGANIZATION",Utils.toFeatureMap(a.getFeatures()));
  }
  for(Annotation a : inputAS.get("OTROS")) { 
    toDelete.add(a);
    Utils.addAnn(outputAS,a,"MISC",Utils.toFeatureMap(a.getFeatures()));
  }
  inputAS.removeAll(toDelete);
}
