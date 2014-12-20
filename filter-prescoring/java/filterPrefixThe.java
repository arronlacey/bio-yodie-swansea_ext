import gate.trendminer.lodie.utils.LodieUtils;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.List;
import java.util.Iterator;

@Override
public void execute() {
  AnnotationSet withThes = inputAS.get("LookupListWithPrefixThe");
  int nremoved = 0;
  for(Annotation withThe : withThes) {
    // get the outher LL
    FeatureMap fmWithThe = withThe.getFeatures();
    int llOuterId = (Integer)fmWithThe.get("llOuter");
    int llInnerId = (Integer)fmWithThe.get("llInner");
    Annotation llOuter = inputAS.get(llOuterId);
    Annotation llInner = inputAS.get(llInnerId);
    // for now, we do the idiot's thing: if we have an overlap of candidates,
    // remove the outer annotation and reduce the inner one to the common
    // candidates.
    List<FeatureMap> common = LodieUtils.intersectCandidates(inputAS,llOuter,llInner,"inst");
    if(common.size() > 0) {
      nremoved += LodieUtils.removeListAnns(inputAS,llOuter);
      nremoved += LodieUtils.keepCandidateAnnsByCollection(inputAS,llInner,common,"inst");
    }
  }
  System.out.println(doc.getName()+": filterPrefixThe annotations filtered: "+nremoved);
}
