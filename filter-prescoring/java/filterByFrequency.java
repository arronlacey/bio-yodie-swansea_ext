import gate.trendminer.lodie.utils.LodieUtils;
import gate.Utils;
import gate.*;
import gate.util.GateRuntimeException;
import java.util.List;
import java.util.Iterator;

// Filter the candidate lists so that only candidates which are most frequent or 
// exceed some minimum frequence (or both) are retained.
// We expect the number of most frequent and the minimum frequency values to 
// be present as configuration properties:
// lodie.filter-prescoring.filterByFrequency.maxCands  (default if not set: 5)
// lodie.filter-prescoring.filterByFrequency.minFreqInWp (default if not set: 10)
@Override
public void execute() {
  int minUriFreqInWp = 
    Integer.parseInt(System.getProperty("lodie.filter-prescoring.filterByFrequency.minUriFreqInWp"));
  int maxCands = 
    Integer.parseInt(System.getProperty("lodie.filter-prescoring.filterByFrequency.maxCands"));
  boolean keepCandsWithNullFreq = 
    Boolean.parseBoolean(System.getProperty("lodie.filter-prescoring.filterByFrequency.keepCandsWithNullFreq"));
  List<FeatureMap> cands;
  int removed = 0;
  for(Annotation ll : inputAS.get("LookupList")) {
    cands = LodieUtils.getCandidateList(inputAS,ll);
    // remove all the candidates where the scUriFreqInWp feature does not exist or is < minUriFreqInWp
    Iterator<FeatureMap> it = cands.iterator();
    while(it.hasNext()) {
      Integer freq = (Integer)it.next().get("scUriFreqInWp");
      if(freq == null || freq < minUriFreqInWp) {
        it.remove();
      }
    }
    // now only keep the maxCands most frequent in the remaining list
    if(cands.size() > maxCands) {
      cands = LodieUtils.sortCandidatesDescOn(cands,"scUriFreqInWp",maxCands,keepCandsWithNullFreq);
    }
    // finally delete all the annotations which do not match the remaining ones, but 
    // if we have an empty candidate list, just delete everything including the list annotation
    if(cands.size() == 0) {
      removed += LodieUtils.removeListAnns(inputAS,ll);
    } else {
      removed += LodieUtils.keepCandidateAnnsByCollection(inputAS,ll,cands,"inst");
    }
  }
  System.out.println(doc.getName()+": filterByFrequency filtered: "+removed);
}
