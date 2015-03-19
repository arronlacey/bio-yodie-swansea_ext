// This script check LookupList annotations if the candidates they contain refer
// to the same inst. This should never happen but seemed to happen with the 
// resources as of 2015-03-19. We check for duplicates and try to merge them into
// a single annotation. In order to avoid non-determinism, we always take the candidate
// where the sum of the hashCode()s of a number of features is higher.

import gate.*;
import gate.trendminer.lodie.utils.LodieUtils;
import java.util.*;

@Override
public void execute() {
  AnnotationSet lls = inputAS.get("LookupList");
  for(Annotation ll : lls) {
    AnnotationSet cands = LodieUtils.getCandidateAnns(inputAS,ll);
    HashMap<String,Annotation> byInst = new HashMap<String,Annotation>();
    for(Annotation cand : cands) {
      FeatureMap fm = cand.getFeatures();
      String inst = (String)fm.get("inst");
      if(byInst.containsKey(inst)) {
        Annotation other = byInst.get("inst");
        FeatureMap otherFm = other.getFeatures();
        System.out.println("Duplicate candidate inst: "+inst);
        System.out.println("Candidate 1: "+LodieUtils.toStringFeatureMap(otherFm));
        System.out.println("Candidate 2: "+LodieUtils.toStringFeatureMap(fm));
        // now decide which candidate to keep. For now we use the features
        // allOrigLabels, redirInfo and disambInfo to find a difference and use the hashCode
        // of these values to pick one deterministically (for the same machine)
        int h1 = otherFm.get("allOrigLabels").hashCode() + otherFm.get("redirInfo").hashCode() + otherFm.get("disambInfo").hashCode();
        int h2 = fm.get("allOrigLabels").hashCode() + fm.get("redirInfo").hashCode() + fm.get("disambInfo").hashCode();
        if(h1 > h2) {
          // delete cand 2 (cand)
          LodieUtils.removeCandidateAnn(inputAS,cand);
        } else {
          // delete cand 1 (other)
          LodieUtils.removeCandidateAnn(inputAS,other);
        }
      } else {
        byInst.put(inst,cand);
      }
    }
  }
}
