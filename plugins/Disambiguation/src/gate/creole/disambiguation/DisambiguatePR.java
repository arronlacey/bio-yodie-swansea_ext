package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
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
import gate.util.InvalidOffsetException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Disambiguate PR", comment = "Removes annotations with the non-maximum score")
public class DisambiguatePR extends AbstractLanguageAnalyser implements
  ProcessingResource {
  /**
	 * 
	 */
  private static final long serialVersionUID = -1361077197819629194L;

  /**
   * weight for the string similarity
   */
  private Float stringSimilarityWeight;

  /**
   * weight for the contextual similarity
   */
  private Float contextualSimilarityWeight;

  /**
   * weight for the popularity
   */
  private Float popularitySimilarityWeight;

  /**
   * weight for the strucrual similarity
   */
  private Float structuralSimilarityWeight;

  /**
   * name of the input AS
   */
  private String inputASName;

  /**
   * name of the output AS
   */
  private String outputASName;

  /**
   * annotation types to consider for disambiguation
   */
  private List<String> annotationTypes;

  /**
   * type of the output annotation
   */
  private String outputAnnotationType;

  /**
   * preserves only the top N candidates
   */
  private String preserveTopNCandidates = "1";

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
    init();
  } // reInit()

  @Override
  public void execute() throws ExecutionException {
    long start = System.currentTimeMillis();
    // input and output AS
    AnnotationSet inputAS = document.getAnnotations(getInputASName());
    AnnotationSet outputAS = document.getAnnotations(getOutputASName());
    List<Annotation> annotationsToBeDisambiguated = new ArrayList<Annotation>();
    for(String aType : annotationTypes) {
      annotationsToBeDisambiguated.addAll(inputAS.get(aType));
    }
    // check inside the key set and only preserve the annotations that overlap
    // with the
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
    /** scores */
    Map<Integer, Float> scores = new HashMap<Integer, Float>();
    // generate the score
    for(Annotation annot : annotationsToBeDisambiguated) {
      FeatureMap map = annot.getFeatures();
      Float stringScore = (Float)map.get(SemanticConstants.STRING_SIMILARITY);
      if(stringScore == null) stringScore = 0.0F;
      Float structuralScore =
        (Float)map.get(SemanticConstants.STRUCTURAL_SIMILARITY);
      if(structuralScore == null) structuralScore = 0.0F;
      Float contextualScore =
        (Float)map.get(SemanticConstants.CONTEXTUAL_SIMILARITY);
      if(contextualScore == null) contextualScore = 0.0F;
      Float popularityScore =
        (Float)map.get(SemanticConstants.POPULARITY_SIMILARITY);
      if(popularityScore == null) popularityScore = 0.0F;
      float score =
        stringScore * getStringSimilarityWeight() + structuralScore *
          getStructuralSimilarityWeight() + contextualScore *
          getContextualSimilarityWeight() + popularityScore *
          getPopularitySimilarityWeight();
      scores.put(annot.getId(), score);
      annot.getFeatures().put("overallScore", score);
    }
    // all the visited annotations
    Set<Annotation> visited = new HashSet<Annotation>();
    outer: for(Annotation annot : annotationsToBeDisambiguated) {
      // if already visited, no need to look at it again
      if(visited.contains(annot))
        continue;
      else visited.add(annot);
      // annotation to disambiguate
      List<Annotation> toDisambiguate = new ArrayList<Annotation>();
      for(String aType : annotationTypes) {
        toDisambiguate.addAll(inputAS.getCovering(aType, Utils.start(annot),
          Utils.end(annot)));
        toDisambiguate.addAll(inputAS.getContained(Utils.start(annot),
          Utils.end(annot)).get(aType));
      }
      toDisambiguate =
        new ArrayList<Annotation>(new HashSet<Annotation>(toDisambiguate));
      // mark all annotations as visited
      visited.addAll(toDisambiguate);
      int topNCands = Integer.parseInt(this.preserveTopNCandidates);
      // choose the one with highest score
      float maxScore = 0.0F;
      if(toDisambiguate.size() > 1) {
        // get the Nth highest score
        maxScore = getNthHighestScore(toDisambiguate, scores, topNCands);
        
        // delete the annotations with score lesser than the max score
        for(int i = 0; i < toDisambiguate.size(); i++) {
          Annotation ann = toDisambiguate.get(i);
          Float score = scores.get(ann.getId());
          if(score == null || score < maxScore) {
            toDisambiguate.remove(i);
            i--;
          }
        }
      }
      
      System.out.println("Max score:" + maxScore);
      System.out.println("Annotations to choose from:" + toDisambiguate.size());
      System.out.println("Number of annots to choose:" + topNCands);
      /*
      if(toDisambiguate.size() > topNCands) {
        // sort annotations based on their popularity score
        Collections.sort(toDisambiguate, new Comparator<Annotation>() {
          @Override
          public int compare(Annotation o1, Annotation o2) {
            Float score1 =
              (Float)o1.getFeatures().get(
                SemanticConstants.POPULARITY_SIMILARITY);
            if(score1 == null) score1 = 0.0F;
            Float score2 =
              (Float)o2.getFeatures().get(
                SemanticConstants.POPULARITY_SIMILARITY);
            if(score2 == null) score2 = 0.0F;
            if(score1 < score2)
              return -1;
            else if(score1 > score2)
              return 1;
            else return 0;
          }
        });
        Float scoreThreshold =
          (Float)toDisambiguate.get(topNCands - 1).getFeatures()
            .get(SemanticConstants.POPULARITY_SIMILARITY);
        for(int i = topNCands; i < toDisambiguate.size(); i++) {
          float sc1 = (Float) toDisambiguate.get(i).getFeatures().get(SemanticConstants.POPULARITY_SIMILARITY);
          if(sc1 < scoreThreshold) {
            toDisambiguate.remove(i);
            i--;
            continue;
          }
        }
      }

      // now consider string sim
      if(toDisambiguate.size() > topNCands) {
        // sort annotations based on their popularity score
        Collections.sort(toDisambiguate, new Comparator<Annotation>() {
          @Override
          public int compare(Annotation o1, Annotation o2) {
            Float score1 =
              (Float)o1.getFeatures().get(
                SemanticConstants.STRING_SIMILARITY);
            if(score1 == null) score1 = 0.0F;
            Float score2 =
              (Float)o2.getFeatures().get(
                SemanticConstants.STRING_SIMILARITY);
            if(score2 == null) score2 = 0.0F;
            if(score1 < score2)
              return -1;
            else if(score1 > score2)
              return 1;
            else return 0;
          }
        });
        Float scoreThreshold =
          (Float)toDisambiguate.get(topNCands - 1).getFeatures()
            .get(SemanticConstants.STRING_SIMILARITY);
        for(int i = topNCands; i < toDisambiguate.size(); i++) {
          float sc1 = (Float) toDisambiguate.get(i).getFeatures().get(SemanticConstants.STRING_SIMILARITY);
          if(sc1 < scoreThreshold) {
            toDisambiguate.remove(i);
            i--;
            continue;
          }
        }
      }
      
      
      // now consider structural sim
      if(toDisambiguate.size() > topNCands) {
        // sort annotations based on their popularity score
        Collections.sort(toDisambiguate, new Comparator<Annotation>() {
          @Override
          public int compare(Annotation o1, Annotation o2) {
            Float score1 =
              (Float)o1.getFeatures().get(
                SemanticConstants.STRUCTURAL_SIMILARITY);
            if(score1 == null) score1 = 0.0F;
            Float score2 =
              (Float)o2.getFeatures().get(
                SemanticConstants.STRUCTURAL_SIMILARITY);
            if(score2 == null) score2 = 0.0F;
            if(score1 < score2)
              return -1;
            else if(score1 > score2)
              return 1;
            else return 0;
          }
        });
        Float scoreThreshold =
          (Float)toDisambiguate.get(topNCands - 1).getFeatures()
            .get(SemanticConstants.STRUCTURAL_SIMILARITY);
        for(int i = topNCands; i < toDisambiguate.size(); i++) {
          float sc1 = (Float) toDisambiguate.get(i).getFeatures().get(SemanticConstants.STRUCTURAL_SIMILARITY);
          if(sc1 < scoreThreshold) {
            toDisambiguate.remove(i);
            i--;
            continue;
          }
        }
      }
      */
      
      Set<String> alreadyAdded = new HashSet<String>();
      
      // add the remaining as Mention annotations
      for(Annotation ann : toDisambiguate) {
        try {
          FeatureMap map = Factory.newFeatureMap();
          map.putAll(ann.getFeatures());
          String i = (String)ann.getFeatures().get("inst");
          String c = (String)ann.getFeatures().get("class");
          if(i != null) {
            String decodedInst = URLDecoder.decode(i, "UTF-8");
            if(alreadyAdded.contains(decodedInst)) continue;
            alreadyAdded.add(decodedInst);
            map.put("inst", decodedInst);
          }
          if(c != null) map.put("class", URLDecoder.decode(c, "UTF-8"));
          outputAS.add(Utils.start(ann), Utils.end(ann),
            getOutputAnnotationType(), map);
          continue outer;
        } catch(InvalidOffsetException e) {
          throw new ExecutionException(e);
        } catch(UnsupportedEncodingException e) {
          throw new ExecutionException(e);
        }
      }
    }
    long end = System.currentTimeMillis();
    System.out.println("time for disambiguate pr:" + (end - start));
  }

  private float getNthHighestScore(List<Annotation> toDisambiguate,
                                   Map<Integer, Float> scores, int i) {
    // user may say, I want the 8th highest
    // but it means we're looking for a score at the index 7
    i = i - 1;
    // put all scores in a score list
    List<Float> scoresList = new ArrayList<Float>();
    for(Annotation annot : toDisambiguate) {
      Float score = scores.get(annot.getId());
      if(score == null) continue;
      scoresList.add(score);
    }
    // sort them
    Collections.sort(scoresList);
    // descending order
    Collections.reverse(scoresList);
    // obtain the score and return it
    if(scoresList.size() <= i) {
      return scoresList.get(scoresList.size() - 1);
    } else {
      return scoresList.get(i);
    }
  }

  public Float getStringSimilarityWeight() {
    return stringSimilarityWeight;
  }

  @CreoleParameter(defaultValue = "0.25")
  @RunTime
  public void setStringSimilarityWeight(Float stringSimilarityWeight) {
    this.stringSimilarityWeight = stringSimilarityWeight;
  }

  public Float getContextualSimilarityWeight() {
    return contextualSimilarityWeight;
  }

  @CreoleParameter(defaultValue = "0.25")
  @RunTime
  public void setContextualSimilarityWeight(Float contextualSimilarityWeight) {
    this.contextualSimilarityWeight = contextualSimilarityWeight;
  }

  public Float getPopularitySimilarityWeight() {
    return popularitySimilarityWeight;
  }

  @CreoleParameter(defaultValue = "0.25")
  @RunTime
  public void setPopularitySimilarityWeight(Float popularitySimilarityWeight) {
    this.popularitySimilarityWeight = popularitySimilarityWeight;
  }

  public String getPreserveTopNCandidates() {
    return preserveTopNCandidates;
  }

  @CreoleParameter(defaultValue = "8")
  @RunTime
  public void setPreserveTopNCandidates(String preserveTopNCandidates) {
    this.preserveTopNCandidates = preserveTopNCandidates;
  }

  public Float getStructuralSimilarityWeight() {
    return structuralSimilarityWeight;
  }

  @CreoleParameter(defaultValue = "0.25")
  @RunTime
  public void setStructuralSimilarityWeight(Float structuralSimilarityWeight) {
    this.structuralSimilarityWeight = structuralSimilarityWeight;
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

  public String getOutputASName() {
    return outputASName;
  }

  @CreoleParameter
  @RunTime
  @Optional
  public void setOutputASName(String outputASName) {
    this.outputASName = outputASName;
  }

  public List<String> getAnnotationTypes() {
    return annotationTypes;
  }

  @CreoleParameter(defaultValue = "Lookup")
  @RunTime
  public void setAnnotationTypes(List<String> annotationTypes) {
    this.annotationTypes = annotationTypes;
  }

  public String getOutputAnnotationType() {
    return outputAnnotationType;
  }

  @CreoleParameter(defaultValue = "Mention")
  @RunTime
  public void setOutputAnnotationType(String outputAnnotationType) {
    this.outputAnnotationType = outputAnnotationType;
  }
} // class Disambiguate PR
