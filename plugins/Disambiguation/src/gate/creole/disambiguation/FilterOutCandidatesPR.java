package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.ProcessingResource;
import gate.Resource;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.ontology.OConstants;
import gate.creole.ontology.Ontology;
import gate.creole.ontology.OntologyBooleanQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Filter Out Candidates PR", comment = "a PR to remove less likely candidates")
public class FilterOutCandidatesPR extends AbstractLanguageAnalyser implements
  ProcessingResource {
  /**
	 * 
	 */
  private static final long serialVersionUID = -7269327797657000009L;

  /**
   * name of the input annotation set
   */
  private String inputASName;

  /**
   * The ontology
   */
  protected Ontology ontology;

  /** subclasses key is URI that is subClassOf value which is the other URI */
  static Map<String, Boolean> specificitiesCache =
    new HashMap<String, Boolean>();

  /** Initialise this resource, and return it. */
  public Resource init() throws ResourceInstantiationException {
    if(ontology == null) { throw new ResourceInstantiationException(
      "No ontology provided!"); }
    return super.init();
  } // init()

  /**
   * Reinitialises the processing resource. After calling this method the
   * resource should be in the state it is after calling init. If the resource
   * depends on external resources (such as rules files) then the resource will
   * re-read those resources. If the data used to create the resource has
   * changed since the resource has been created then the resource will change
   * too after calling reInit().
   */
  public void reInit() throws ResourceInstantiationException {
    cleanup();
    init();
  } // reInit()

  @Override
  public void execute() throws ExecutionException {
    // record the start time
    long start = System.currentTimeMillis();
    // input AS
    AnnotationSet inputAS = document.getAnnotations(getInputASName());
    List<String> lookupList = new ArrayList<String>();
    lookupList.add("Lookup");
    Set<Annotation> deletedAnnots = new HashSet<Annotation>();
    AnnotationSet lookupSet = inputAS.get("Lookup");
    // for every look annot, obtain its covering annot
    // if the covering annot has fullString, delete the current annot
    outer: for(Annotation lookup : lookupSet) {
      if(deletedAnnots.contains(lookup)) continue;
      String currentAnnotLookupRule =
        (String)lookup.getFeatures().get("lookupRule");
      // so this is fullString lookup
      // we delete all the covering and contained lookup with value of
      // lookupRule to less than fullString
      Set<Annotation> candidates =
        lookupSet.getCovering("Lookup", Utils.start(lookup), Utils.end(lookup));
      for(Annotation cand : candidates) {
        if(cand == null) continue;
        // if the offsets of the current lookup and the candidate are
        // same and the current match is already fullString, we
        // do not want to delete the current lookup
        if(Utils.start(cand).longValue() == Utils.start(lookup).longValue() &&
          Utils.end(cand).longValue() == Utils.end(lookup).longValue()) {
          if(currentAnnotLookupRule == null ||
            currentAnnotLookupRule.equals("fullString")) continue;
        }
        String lookupRule = (String)cand.getFeatures().get("lookupRule");
        // if it is null, assuming it was obtained using gazetteer
        // lookups)
        if(lookupRule == null) {
          // it means the cand annotation was obtained using gazetteer
          // lookup
          // but we can't be sure. delete the current lookup only if
          // it is not full string
          if(currentAnnotLookupRule != null &&
            !currentAnnotLookupRule.equals("fullString")) {
            inputAS.remove(lookup);
            deletedAnnots.add(lookup);
            continue outer;
          }
        } else if(lookupRule.equals("fullString")) {
          inputAS.remove(lookup);
          deletedAnnots.add(lookup);
          continue outer;
        }
      }
    }
    lookupSet = inputAS.get("Lookup");
    // first we keep only those annotations which are full string match
    for(Annotation lookup : lookupSet) {
      if(deletedAnnots.contains(lookup)) continue;
      String lookupRule = (String)lookup.getFeatures().get("lookupRule");
      if(lookupRule == null) continue;
      if(!lookupRule.equals("fullString")) continue;
      // so this is fullString lookup
      // we delete all the covering and contained lookup with value of
      // lookupRule to less than fullString
      Set<Annotation> candidates =
        CommonUtils.getContainedCoveringAnnots(lookup, lookupSet, lookupList);
      for(Annotation cand : candidates) {
        if(deletedAnnots.contains(cand)) continue;
        lookupRule = (String)lookup.getFeatures().get("lookupRule");
        if(lookupRule == null) continue;
        if(!lookupRule.equals("fullString")) {
          inputAS.remove(cand);
          deletedAnnots.add(cand);
        }
      }
    }
    System.out.println(document.getName() + ": Filtered out:" +
      deletedAnnots.size());
    long end = System.currentTimeMillis();
  }

  /**
   * if inst rdfs:subClassOf overInst return true, false otherwise
   * 
   * @param inst
   * @param overInst
   * @return
   */
  boolean isMoreSpecific(String instClass, String overInstClass) {
    Boolean val = specificitiesCache.get(instClass + "-" + overInstClass);
    if(val != null) return val;
    boolean iMoreSpecific = false;
    if(instClass != null && overInstClass != null &&
      instClass.equals(overInstClass)) { return false; }
    String query =
      "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " + "ASK {<" +
        instClass + "> rdfs:subClassOf <" + overInstClass + "> }";
    OntologyBooleanQuery booleanQuery =
      ontology.createBooleanQuery(query, OConstants.QueryLanguage.SPARQL);
    iMoreSpecific = booleanQuery.evaluate();
    specificitiesCache.put(instClass + "-" + overInstClass, iMoreSpecific);
    specificitiesCache.put(overInstClass + "-" + instClass, !iMoreSpecific);
    return iMoreSpecific;
  }

  /**
   * The cleanup method
   */
  @Override
  public void cleanup() {
    super.cleanup();
  }

  public String getInputASName() {
    return inputASName;
  }

  @CreoleParameter
  @RunTime
  @Optional
  public void setInputASName(String inputASName) {
    this.inputASName = inputASName;
  }

  public Ontology getOntology() {
    return ontology;
  }

  @CreoleParameter
  public void setOntology(Ontology ontology) {
    this.ontology = ontology;
  }
} // class DisambiguatePR
