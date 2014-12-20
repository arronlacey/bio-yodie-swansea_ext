package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.ProcessingResource;
import gate.Resource;
import gate.Utils;
import gate.creole.ANNIEConstants;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.ontology.URI;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "String Similarity PR")
public class StringSimilarityPR extends AbstractLanguageAnalyser implements
  ProcessingResource {
  /**
	 * 
	 */
  private static final long serialVersionUID = 4836781098219171384L;

  /**
   * List of the annotation types to be used for lookup.
   */
  private List<String> annotationTypes;

  /**
   * name of the input annotation set
   */
  private String inputASName;

  /** Initialise this resource, and return it. */
  public Resource init() throws ResourceInstantiationException {
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
    strCache.clear();
    // input AS
    AnnotationSet inputAS = document.getAnnotations(getInputASName());
    Map<String, Float> scoreCache = new HashMap<String, Float>();
    Map<String, String> contextCache = new HashMap<String, String>();
    AnnotationSet docTokenSet = inputAS.get("Token");

    // collect annotations to be disambiguated
    Set<Annotation> annotSet = new HashSet<Annotation>();
    for(String aType : annotationTypes) {
      annotSet.addAll(inputAS.get(aType));
    }
    // no annotation to disambiguate?
    if(annotSet.isEmpty()) return;


    List<Annotation> annotationsToBeDisambiguated = new ArrayList<Annotation>(annotSet);
    // check inside the key set and only preserve the annotations that
    // overlap with the
    // annotations inside the key annotation
    String disambKeyMentions = System.getProperty("disamb.key.mentions");
    if(disambKeyMentions != null && disambKeyMentions.equals("true")) {
      AnnotationSet keyMentionsSet =
        document.getAnnotations("Key").get("Mention");
      outer: for(int i = 0; i < annotationsToBeDisambiguated.size(); i++) {
        Annotation atbd = annotationsToBeDisambiguated.get(i);
        for(Annotation mention : keyMentionsSet) {
          if(atbd.overlaps(mention)) {
            continue outer;
          }
        }
        annotationsToBeDisambiguated.remove(i);
        i--;
      }
    }


    Set<String> nounFeatures = new HashSet<String>();
    nounFeatures.add("NNP");
    nounFeatures.add("NNPS");
    nounFeatures.add("NPS");

    // one sem annotation at a time
    for(Annotation ann : annotationsToBeDisambiguated) {
      if(interrupted) return;
      if(!ann.getFeatures().containsKey("inst") ||
        ann.getFeatures().get("inst") == null) continue;

      // if it doesn't have a string feature, just skip it
      if(!ann.getFeatures().containsKey("string")) continue;

      String cacheKey = Utils.start(ann) + "-" + Utils.end(ann);
      Float score =
        scoreCache.get(cacheKey + "-" + ann.getFeatures().get("inst"));
      if(score != null) {
        ann.getFeatures().put(SemanticConstants.STRING_SIMILARITY, score);
        continue;
      }
      String contextString = contextCache.get(cacheKey);
      if(contextString == null) {
        // System.out.print("Obtaining context annotations");
        List<Annotation> contextAnnotations =
          CommonUtils.getContextAnnotations(ann, inputAS, "Token", 10, true);
        // System.out.println("Done:" + contextAnnotations.size());
        Set<String> nerAnnotTypes = new HashSet<String>();
        nerAnnotTypes.add("Person");
        nerAnnotTypes.add("Location");
        nerAnnotTypes.add("Organization");
        // System.out.print("Obtaining NER annotations");
        AnnotationSet nerSet = inputAS.get(nerAnnotTypes);
        // System.out.print("Deleting overlapping annotations");
        for(Annotation nerAnnot : nerSet) {
          AnnotationSet tokenSet =
            docTokenSet
              .getContained(Utils.start(nerAnnot), Utils.end(nerAnnot));
          contextAnnotations.addAll(tokenSet);
          contextAnnotations.removeAll(docTokenSet.getContained(
            Utils.start(ann), Utils.end(ann)));
        }
        contextAnnotations =
          CommonUtils.filterAnnotations(contextAnnotations, "category",
            nounFeatures);
        // System.out.print("Generate context sting");
        StringBuffer context = new StringBuffer("");
        Set<String> contextWords = new HashSet<String>();
        for(Annotation conAnn : contextAnnotations) {
          String s =
            (String)conAnn.getFeatures().get(
              ANNIEConstants.TOKEN_STRING_FEATURE_NAME);
          if(contextWords.contains(s.toLowerCase())) continue;
          contextWords.add(s.toLowerCase());
          context.append(CommonUtils.normaliseAccentedChars(s)).append(" ");

          // for the hindi plugin, where tokens are assigned english translations
          String english = (String) conAnn.getFeatures().get("english");
          if(english != null && english.trim().length() > 0) {
	    String [] words = english.split(";");
            for(String aWord : words) {
              if(contextWords.contains(aWord.toLowerCase())) continue;
              contextWords.add(aWord.toLowerCase());
              context.append(CommonUtils.normaliseAccentedChars(aWord)).append(" ");
            }
          }
        }
        // System.out.println("done");
        contextCache.put(cacheKey, context.toString());
        contextString = context.toString();
      }
      score =
        findStringSimilarity(ann.getFeatures().get("string").toString(), ann, contextString);
      ann.getFeatures().put(SemanticConstants.STRING_SIMILARITY, score);
      scoreCache.put(cacheKey + "-" + ann.getFeatures().get("inst"), score);
    }
    long end = System.currentTimeMillis();
    System.out.println("String Sim:" + ((end - start) / 1000));
  }

  /**
   * string comparison cache maintained document wise
   */
  Map<String, Float> strCache = new HashMap<String, Float>();

  /**
   * calculates similarity based on string similarity of the ambiguous term and
   * context where ambiguous term is represented by 'term string', localName of
   * the URI, or localName of the class URI and then averaged
   * 
   * @param ann
   * @param contextAnnotations
   * @return
   */
  private float findStringSimilarity(String str, Annotation ann, String context) {
    float simBtwStrings = 0;
    String orig = str;
    String origUriString = (String)ann.getFeatures().get("resourceName");

    float score1 = 0.0F;
    if(origUriString != null) {
      Float cachedScore = strCache.get(orig + "-" + origUriString);
      if(cachedScore == null) {
        score1 = CommonUtils.match(orig, origUriString, true);
        strCache.put(orig + "-" + origUriString, score1);
      } else {
        score1 = cachedScore;
      }
    }

    float score2 = 0.0F;
    if(origUriString != null) {
      Float cachedScore =
        strCache.get(origUriString + "-" + context);
      if(cachedScore == null) {
        score2 =
          CommonUtils.match(
            CommonUtils.normaliseAccentedChars(origUriString),
            context, false);
        strCache.put(origUriString + "-" + context, score2);
      } else {
        score2 = cachedScore;
      }
    }
    
    ann.getFeatures().put(SemanticConstants.RESOURCE_NAME_STRING_SIMILARITY, score1);
    ann.getFeatures().put(SemanticConstants.CONTEXT_STRING_SIMILARITY, score2);
    
    if(score1 + score2 > 0.0F)
      simBtwStrings = (score1 + score2) / 2;
    else simBtwStrings = 0.0F;
    return simBtwStrings;
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
} // class StringSimilarityPR
