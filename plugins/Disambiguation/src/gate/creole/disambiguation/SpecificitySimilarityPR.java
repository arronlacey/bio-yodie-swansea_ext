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
import java.util.List;
import java.util.Map;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Specificity Similarity PR", comment = "scores higher the more specific classes")
public class SpecificitySimilarityPR extends AbstractLanguageAnalyser implements
  ProcessingResource {
  /**
   * List of the annotation types to be used for lookup.
   */
  private List<String> annotationTypes;

  /**
   * name of the input annotation set
   */
  private String inputASName;

  /**
   * The ontology
   */
  protected Ontology ontology;

  /** subclasses key is URI that is subClassOf value which is the other URI */
  Map<String, Boolean> specificitiesCache = new HashMap<String, Boolean>();

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
    /** subclasses key is URI that is subClassOf value which is the other URI */
    specificitiesCache.clear();
    // input AS
    AnnotationSet inputAS = document.getAnnotations(getInputASName());
    // annotations to be disambiguated
    List<Annotation> annotationsToBeDisambiguated = new ArrayList<Annotation>();
    for(String aType : annotationTypes) {
      annotationsToBeDisambiguated.addAll(inputAS.get(aType));
    }
    // no annotations found so just return
    if(annotationsToBeDisambiguated.isEmpty()) return;
    // one sem annotation at a time
    for(Annotation ann : annotationsToBeDisambiguated) {
      if(interrupted) return;
      // now obtain all the annotationTypes that overlap the current
      // annotation
      List<Annotation> overlappingAnnotations = new ArrayList<Annotation>();
      for(String aType : annotationTypes) {
        overlappingAnnotations.addAll(inputAS.getCovering(aType,
          Utils.start(ann), Utils.end(ann)));
      }
      float score = findSpecificitySimilarity(ann, overlappingAnnotations);
      ann.getFeatures().put(SemanticConstants.SPECIFICITY_SIMILARITY, score);
    }
    long end = System.currentTimeMillis();
    System.out.println("Specificity Sim:" + ((end - start) / 1000));
  }

  /**
   * if lower in hierarchy get the higher score
   * 
   * @param ann
   * @param overlappedAnnotations
   * @return
   */
  float findSpecificitySimilarity(Annotation ann,
                                  List<Annotation> overlappedAnnotations) {
    float score = new Float(0.0);
    String instClass =
      (String)ann.getFeatures().get(SemanticConstants.LKB_FEATURE_CLASS);
    if(instClass == null) return score;
    // check if any of the overlapped annotations is redundant: if there are
    // more specific classes with the same inst URI then add score to them
    for(Annotation overAnn : overlappedAnnotations) {
      String overInstClass =
        (String)overAnn.getFeatures().get(SemanticConstants.LKB_FEATURE_CLASS);
      // if no over inst class information availble
      if(overInstClass == null) continue;
      if(instClass != null && overInstClass != null &&
        instClass.equals(overInstClass)) {
        continue;
      }
      Boolean isMoreSpecific =
        specificitiesCache.get(instClass + overInstClass);
      if(isMoreSpecific == null) {
        isMoreSpecific = specificitiesCache.get(overInstClass + instClass);
        // because subclassof goes in one direction
        if(isMoreSpecific != null && isMoreSpecific.booleanValue() == true)
          isMoreSpecific = false;
      }
      if(isMoreSpecific == null) {
        isMoreSpecific = isMoreSpecific(instClass, overInstClass);
        specificitiesCache.put(instClass + overInstClass, isMoreSpecific);
        if(isMoreSpecific.booleanValue() == true)
          specificitiesCache.put(overInstClass + instClass, new Boolean(false));
        else specificitiesCache.put(overInstClass + instClass,
          new Boolean(true));
      }
      if(isMoreSpecific.booleanValue() == true) {
        // more specific is more important
        float specificityScore = new Float(1.0);
        score = score + specificityScore;
      }
    }
    if(overlappedAnnotations != null && overlappedAnnotations.size() > 0)
      score = score / overlappedAnnotations.size();
    return score;
  }

  /**
   * if inst rdfs:subClassOf overInst return true, false otherwise
   * 
   * @param inst
   * @param overInst
   * @return
   */
  boolean isMoreSpecific(String instClass, String overInstClass) {
    boolean iMoreSpecific = false;
    if(instClass != null && overInstClass != null &&
      instClass.equals(overInstClass)) { return false; }
    String query =
      "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " + "ASK {<" +
        instClass + "> rdfs:subClassOf <" + overInstClass + "> }";
    OntologyBooleanQuery booleanQuery =
      ontology.createBooleanQuery(query, OConstants.QueryLanguage.SPARQL);
    iMoreSpecific = booleanQuery.evaluate();
    return iMoreSpecific;
  }

  /**
   * The cleanup method
   */
  @Override
  public void cleanup() {
    super.cleanup();
  }

  public List<String> getAnnotationTypes() {
    return annotationTypes;
  }

  @RunTime
  @CreoleParameter(defaultValue = "Lookup")
  public void setAnnotationTypes(List<String> annotationTypes) {
    this.annotationTypes = annotationTypes;
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
