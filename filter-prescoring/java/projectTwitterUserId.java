// Check all annotations of type TwitterExpanderUserID_name and see of we have a candidate list
// there. Also check if the user id itself does NOT have a candidate list.
// If this is the case we will map back a lookup found on the expanded name to the user id. 
// Depending on the setting lodie.filter-prescoring.projectTwitterUserID.coextonly
// this will be done either for just a coextensive lookup or for the longest lookup found
// in the name. Default is false (do not limit to coextensive lookups only)

import gate.*;
import gate.util.GateRuntimeException;
import gate.trendminer.lodie.utils.LodieUtils;

@Override
public void init() {
  // get the setting for whether to use coextensive lookups only
  String setting = System.getProperty("lodie.filter-prescoring.projectTwitterUserID.coextonly");
  if(setting != null) {
    coextonly = Boolean.parseBoolean(setting);
  }
}

private boolean coextonly = false;

@Override
public void execute() {
  AnnotationSet defaultSet = doc.getAnnotations();
  AnnotationSet twNames = defaultSet.get("TwitterExpanderUserID_name");
  for(Annotation twName : twNames) {
    //logger.debug(doc.getName()+": found a twName: "+twName);
    // get the original id annotation and check if we have an overlapping LookupList 
    Annotation userId = defaultSet.get((int)twName.getFeatures().get("mentionId"));
    //logger.debug(doc.getName()+": refers to userid: "+userId);
    AnnotationSet userIdLLs = Utils.getOverlappingAnnotations(defaultSet,userId,"LookupList");
    // if there are no overlapping lists, check if there is a coextensive list for the name
    if(userIdLLs.size() == 0) {
      //logger.debug(doc.getName()+": no LL on the user id, good");
      Annotation nameLL = null;
      if(coextonly) {
        AnnotationSet nameLLs = Utils.getCoextensiveAnnotations(defaultSet,twName,"LookupList");
        // there should really be just at most 1 coextensive lookup list but we 
        // make sure to only take one anyway
        if(nameLLs.size() != 0) {
          nameLL = nameLLs.iterator().next(); // take the only or an arbitrary first one
          //logger.debug(doc.getName()+": found a LL on the twName: "+nameLL);
        }
      } else {         
        // check if there are contained lookup list annotations
        AnnotationSet nameLLs = Utils.getContainedAnnotations(defaultSet,twName,"LookupList");
        if(nameLLs.size() != 0) {
          doc.getFeatures().put("hasContainedLLinTEname",true);
          // now find the/one longest one
          Annotation longest = nameLLs.iterator().next();
          for(Annotation ann : nameLLs) {
            if(Utils.length(ann) > Utils.length(longest)) {
              longest = ann;
            }
          }
          nameLL = longest;
        }
      }
      if(nameLL != null) {
        // now clone that list onto the twName annotation
        int newLL = LodieUtils.cloneListAnn(defaultSet,nameLL,defaultSet,userId);
        defaultSet.get(newLL).getFeatures().put("isProjected",true);
        doc.getFeatures().put("hasProjectedUserID",true);        
      }
    }
  }
}
