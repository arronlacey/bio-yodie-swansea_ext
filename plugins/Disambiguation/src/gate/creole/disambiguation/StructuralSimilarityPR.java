package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.ProcessingResource;
import gate.Resource;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.gazetteer.lucene.Hit;
import gate.creole.gazetteer.lucene.IndexException;
import gate.creole.gazetteer.lucene.Searcher;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.ontology.OConstants;
import gate.creole.ontology.Ontology;
import gate.creole.ontology.Literal;
import gate.creole.ontology.OntologyBooleanQuery;
import gate.creole.ontology.OntologyTupleQuery;
import gate.util.Benchmark;
import gate.util.Benchmarkable;
import gate.util.GateRuntimeException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
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
@CreoleResource(name = "Structural Similarity PR", comment = "finds relations between the sem annotations")
public class StructuralSimilarityPR extends AbstractLanguageAnalyser implements
  ProcessingResource, Benchmarkable {
  private static final double AVAOID_ZERO_SCORE_CONSTANT = 0.001;

  private static final String MAX_CHAR_DISTANCE = "structure_maxCharDistance";

  private static final String TOTAL_NUM_OF_RELATIONS =
    "structure_totalRelations";

  private static final String COUNT_FOR_INDIRECT_RELATION =
    "structure_countForIndirectRelation";

  private static final String DISTANCE_IN_NUM_OF_CHARS =
    "structure_characterDistance";

  private static final String RELATION_WITH = "structure_relationWith";

  private static final String RELATION_COUNT = "structure_relationCount";

  private static final String COUNT_FOR_DIRECT_RELATION =
    "structure_countForDirectRelation";

  /**
   * serial version id
   */
  private static final long serialVersionUID = 1632318208705298081L;

  /**
   * List of the annotation types to be used for lookup.
   */
  private List<String> annotationTypes;

  /**
   * name of the input annotation set
   */
  private String inputASName;

  /**
   * url of the lucene index
   */
  private URL luceneIndexDirURL;

  /**
   * The searcher object
   */
  private Searcher searcher;

  /**
   * The ontology
   */
  protected Ontology ontology;

  /**
   * If this parameter is set to true, and if there is annotation found with at
   * least one directRelation or indirectRelations, all toerh annotatos for
   * which no relation is found are deleted.
   */
  private Boolean deleteAnnotationsWithNoRelation = true;

  /** key is one URI1+URI2, value is true if relation exist false otherwise */
  private Map<String, Integer> directRelationsCache =
    new HashMap<String, Integer>();

  private Map<String, Integer> inDirectRelationsCache =
    new HashMap<String, Integer>();

  private final String askIndirectQueryString = 
    "ASK { { ?from ?r1 ?x . ?x ?r2 ?to . } UNION { ?to ?r3 ?y . ?y ?r4 ?from . } } ";
  private OntologyBooleanQuery askIndirectQuery;
  
  /** Initialise this resource, and return it. */
  public Resource init() throws ResourceInstantiationException {
    if(ontology == null) { throw new ResourceInstantiationException(
      "No ontology provided!"); }
    askIndirectQuery = ontology.createBooleanQuery(askIndirectQueryString, OConstants.QueryLanguage.SPARQL);
    // check that luceneIndexDirURL is provided
    if(luceneIndexDirURL == null) {
      System.err.println("LuceneIndexDirURL is not set!!!!!!!!!!!!!!!");
    } else {
      // initialize searcher
      try {
        searcher = new Searcher(new File(luceneIndexDirURL.toURI()));
      } catch(IndexException e) {
        throw new ResourceInstantiationException(
          "Error occurred when initializing searcher", e);
      } catch(URISyntaxException e) {
        throw new ResourceInstantiationException(
          "lucene index directory could not be converted to a file object", e);
      }
    }
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
    Map<String, List<Annotation>> contextCache =
      new HashMap<String, List<Annotation>>();
    if(directRelationsCache.size() > 200000) directRelationsCache.clear();
    if(inDirectRelationsCache.size() > 200000) inDirectRelationsCache.clear();
    // input AS
    final AnnotationSet inputAS = document.getAnnotations(getInputASName());
    // annotations to be disambiguated
    List<Annotation> annotationsToBeDisambiguated = new ArrayList<Annotation>();
    for(String aType : annotationTypes) {
      annotationsToBeDisambiguated.addAll(inputAS.get(aType));
    }
    // remove duplicates
    annotationsToBeDisambiguated =
      new ArrayList<Annotation>(new HashSet<Annotation>(
        annotationsToBeDisambiguated));
    // no annotations found so just return
    if(annotationsToBeDisambiguated.isEmpty()) return;
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
    // sort annotations
    Collections.sort(annotationsToBeDisambiguated,
      new gate.util.OffsetComparator());
    // one sem annotation at a time
    for(Annotation ann : annotationsToBeDisambiguated) {
      // start and end offsets, we use this to calculate distance between the
      // two annotations where relation is found
      long annStart = Utils.start(ann);
      long annEnd = Utils.end(ann);
      // obtain the list of context annotations
      List<Annotation> contextAnnotations =
        contextCache.get(gate.Utils.start(ann) + "-" + gate.Utils.end(ann));
      // no, okay so lets search for them
      if(contextAnnotations == null) {
        contextAnnotations =
          CommonUtils.getContextAnnotations(ann, inputAS, new HashSet<String>(
            annotationTypes), 10, true);
        for(int i=0;i<contextAnnotations.size();i++) {
          Annotation cAnnot = contextAnnotations.get(i);
          String inst = (String)cAnnot.getFeatures().get(SemanticConstants.LKB_FEATURE_INST);
          if(inst == null) {
            contextAnnotations.remove(i);
            i--;
            continue;
          }
        }
        
        if(!sortAnnotations(ann, inputAS, contextAnnotations)) {
          // something is wrong with this annotation
          continue;
        }
        // context Lookup annotations
        contextCache.put(gate.Utils.start(ann) + "-" + gate.Utils.end(ann),
          contextAnnotations);
      }
      
      // for the current annotation obtain its inst feature
      String inst =
        (String)ann.getFeatures().get(SemanticConstants.LKB_FEATURE_INST);
      // first we check for direct relation only
      boolean directRelationFound = false;
      int maxDirectRelationsCount = 0;
      int lastDistance = -1;
      // obtain the count for relations between this annotation and the context
      // annotation
      for(Annotation conAnn : contextAnnotations) {
        String contextInst =
          (String)conAnn.getFeatures().get(SemanticConstants.LKB_FEATURE_INST);
        if(inst.equals(contextInst)) continue;
        // start and end offsets of the context annotation
        // find out the distance between two entities
        long cAnnStart = Utils.start(conAnn);
        long cAnnEnd = Utils.end(conAnn);
        long charDistance = 0;
        if(cAnnStart >= annEnd) {
          charDistance = cAnnStart - annEnd;
        } else if(cAnnEnd <= annStart) {
          charDistance = annStart - cAnnEnd;
        }
        // if we've already found our best candidate, break
        if(lastDistance != -1 && charDistance != lastDistance) break;
        // try both combinations to obtain the count from cache
        Integer directRelationCount =
          directRelationsCache.get(inst + contextInst);
        if(directRelationCount == null) {
          directRelationCount = directRelationsCache.get(contextInst + inst);
        }
        // not found in the cache, now fire sparql
        if(directRelationCount == null) {
          directRelationCount = directRelationCount(inst, contextInst);
          directRelationsCache.put(inst + contextInst, directRelationCount);
          directRelationsCache.put(contextInst + inst, directRelationCount);
        }
        if(directRelationCount > 0) {
          if(directRelationCount > maxDirectRelationsCount) {
            // update the local counter
            maxDirectRelationsCount = directRelationCount;
            directRelationFound = true;
            lastDistance = (int)charDistance;
            // add information to the annotation
            ann.getFeatures().put(COUNT_FOR_DIRECT_RELATION,
              directRelationCount);
            ann.getFeatures().put(RELATION_COUNT, directRelationCount);
            ann.getFeatures().put(RELATION_WITH, contextInst);
            ann.getFeatures().put(DISTANCE_IN_NUM_OF_CHARS, (int)charDistance);
          }
        }
      }
      // we calculate indirect relation only if no directRelation was found
      if(!directRelationFound) {
        int maxIndirectRelationsCount = 0;
        lastDistance = -1;
        // obtain the count for relations between this annotation and the
        // context annotation
        // now we check for indirect relation only
        for(Annotation conAnn : contextAnnotations) {
          String contextInst =
            (String)conAnn.getFeatures()
              .get(SemanticConstants.LKB_FEATURE_INST);
          if(inst.equals(contextInst)) continue;
          // start and end offsets
          long cAnnStart = Utils.start(conAnn);
          long cAnnEnd = Utils.end(conAnn);
          long charDistance = 0;
          if(cAnnStart >= annEnd) {
            charDistance = cAnnStart - annEnd;
          } else if(cAnnEnd <= annStart) {
            charDistance = annStart - cAnnEnd;
          }
          // if we've already found our best candidate, break
          if(lastDistance != -1 && charDistance != lastDistance) break;
          Integer indirectRelationCount =
            inDirectRelationsCache.get(inst + contextInst);
          if(indirectRelationCount == null) {
            indirectRelationCount =
              inDirectRelationsCache.get(contextInst + inst);
          }
          if(indirectRelationCount == null) {
            indirectRelationCount = indirectRelationCount(inst, contextInst);
            inDirectRelationsCache.put(inst + contextInst,
              indirectRelationCount);
            inDirectRelationsCache.put(contextInst + inst,
              indirectRelationCount);
          }
          if(indirectRelationCount > 0) {
            if(indirectRelationCount > maxIndirectRelationsCount) {
              // update the local variables
              maxIndirectRelationsCount = indirectRelationCount;
              lastDistance = (int)charDistance;
              // add information to the annotation
              ann.getFeatures().put(COUNT_FOR_INDIRECT_RELATION,
                indirectRelationCount);
              ann.getFeatures().put(RELATION_COUNT, indirectRelationCount);
              ann.getFeatures().put(RELATION_WITH, contextInst);
              ann.getFeatures()
                .put(DISTANCE_IN_NUM_OF_CHARS, (int)charDistance);
            }
          }
        }
      }
    }
    // build a cache of overlapping annotations
    // this is needed as now we will be normalising values
    // over a set of overlapping annotations
    Map<Annotation, Set<Annotation>> overlappingMap =
      new HashMap<Annotation, Set<Annotation>>();
    
    for(Annotation ann : annotationsToBeDisambiguated) {
      if(overlappingMap.containsKey(ann)) continue;
      Set<Annotation> overlappingAnnots =
        CommonUtils.getContainedCoveringAnnots(ann, inputAS, annotationTypes);
      overlappingAnnots.add(ann);
      for(Annotation ann1 : overlappingAnnots) {
        overlappingMap.put(ann1, overlappingAnnots);
      }
    }
    
    // prefer annotations with not all upper case if similar URI is available
    
    
    // lets go through sets of overlapping annots
    // and delete annotations with no relations found
    // but if do this only if there's at least
    // one annotation found with some relation
    if(deleteAnnotationsWithNoRelation) {
      Set<Annotation> toDeleteAnnots = new HashSet<Annotation>();
      for(Set<Annotation> overlappingAnnots : overlappingMap.values()) {
        
        Set<String> toDeleteURIs = new HashSet<String>();
        for(Annotation annot : overlappingAnnots) {
          String instFeat = (String) annot.getFeatures().get("inst");
          String instName = instFeat.substring(instFeat.lastIndexOf('/')+1);
          boolean allUpperCase = true;
          for(char ch : instName.toCharArray()) {
            if(!Character.isUpperCase(ch)) {
              allUpperCase = false;
              break;
            }
          }
          
          if(!allUpperCase) {
           toDeleteURIs.add(instFeat.toUpperCase());
          }
        }
        
        
        // is there any annotation with relationCount > 0?
        boolean anyAnnotationWithRelations = false;
        Set<Annotation> toDelete = new HashSet<Annotation>();
        for(Annotation annot : overlappingAnnots) {
          String instFeat = (String) annot.getFeatures().get("inst");
          if(toDeleteURIs.contains(instFeat)) {
            System.out.println("deleting:" + instFeat);
            toDelete.add(annot); 
            continue;
          }
          
          Integer relationCount =
            (Integer)annot.getFeatures().get(RELATION_COUNT);
          if(relationCount != null && relationCount > 0) {
            anyAnnotationWithRelations = true;
          } else {
            toDelete.add(annot);
          }
        }
        // if there was at least one annotation found with relationCount > 0
        if(anyAnnotationWithRelations) {
          overlappingAnnots.removeAll(toDelete);
          toDeleteAnnots.addAll(toDelete);
        }
      }
      // are there annotations to be deleted?
      if(!toDeleteAnnots.isEmpty()) {
        for(Annotation toDelAnnot : toDeleteAnnots) {
          inputAS.remove(toDelAnnot);
          annotationsToBeDisambiguated.remove(toDelAnnot);
        }
      }
      System.out.println("Deleted annotations without relations:" +
        toDeleteAnnots.size());
    }
    
    // if there's an annotation found with a direct relation within
    // a set of overlapping annotations, we do not consider annotations
    // with no relation or indirect relation
    for(Set<Annotation> overlappingAnnots : overlappingMap.values()) {
      boolean hasCountForDirectRelatonFeature = false;
      for(Annotation ann1 : overlappingAnnots) {
        hasCountForDirectRelatonFeature =
          ann1.getFeatures().containsKey(COUNT_FOR_DIRECT_RELATION);
        if(hasCountForDirectRelatonFeature) break;
      }
      // delete all annotations from the overlappingAnnots
      // which do not have the countForDirectRelationFeature
      if(hasCountForDirectRelatonFeature) {
        List<Annotation> tempList =
          new ArrayList<Annotation>(overlappingAnnots);
        for(Annotation annot : tempList) {
          if(!annot.getFeatures().containsKey(COUNT_FOR_DIRECT_RELATION)) {
            overlappingAnnots.remove(annot);
          }
        }
      }
    }
    // normalise their scores
    for(Annotation ann : annotationsToBeDisambiguated) {
      Integer relationCount = (Integer)ann.getFeatures().get(RELATION_COUNT);
      // annotation with no relation, ignore it
      if(relationCount == null || relationCount == 0) {
        ann.getFeatures().put(SemanticConstants.STRUCTURAL_SIMILARITY, 0F);
        continue;
      }
      // obtain from the cache all the overlapping annotations
      Set<Annotation> overlappingAnnots = overlappingMap.get(ann);
      // calculating total number of relations
      // that exists for this set of overlapping annotations
      int totalRelations = 0;
      // also keeping track of longest distance between two entities
      int maxCharDistance = 0;
      for(Annotation a : overlappingAnnots) {
        Integer overlappRelationCount =
          (Integer)a.getFeatures().get(RELATION_COUNT);
        if(overlappRelationCount == null) continue;
        totalRelations += overlappRelationCount;
        // char distance as well
        Integer charDistance =
          (Integer)a.getFeatures().get(DISTANCE_IN_NUM_OF_CHARS);
        if(charDistance == null) continue;
        if(charDistance > maxCharDistance) {
          maxCharDistance = charDistance;
        }
      }
      // add both values to the annotation
      ann.getFeatures().put(TOTAL_NUM_OF_RELATIONS, totalRelations);
      ann.getFeatures().put(MAX_CHAR_DISTANCE, maxCharDistance);
      // how far it is
      // number of characters
      Integer characterDistance =
        (Integer)ann.getFeatures().get(DISTANCE_IN_NUM_OF_CHARS);
      // obtain the chara distance score
      float charDistanceScore = 0.0F;
      if(maxCharDistance > 0 && characterDistance > 0) {
        charDistanceScore = (float)((float)characterDistance / maxCharDistance);
        charDistanceScore -= AVAOID_ZERO_SCORE_CONSTANT;
        // inverse it.. more the number of characters in between, lower the
        // score should be
        charDistanceScore = (float)1 - charDistanceScore;
      }
      // higher the number of relations, higher would be the score
      float relationCountScore = (float)((float)relationCount / totalRelations);
      // divide it by half
      relationCountScore = (float)((float)relationCountScore / 2);
      if(ann.getFeatures().containsKey(COUNT_FOR_DIRECT_RELATION)) {
        relationCountScore += 0.5;
      }
      // calculate the weighted linear score
      float score =
        (float)((float)relationCountScore * 0.50F + charDistanceScore * 0.50F);
      // for debugging purpose.. add the score as features to the annotation
      ann.getFeatures().put(SemanticConstants.CHAR_DIST_STRUCTURAL_SIMILARITY,
        charDistanceScore);
      ann.getFeatures().put(SemanticConstants.RELATION_STRUCTURAL_SIMILARITY,
        relationCountScore);
      ann.getFeatures().put(SemanticConstants.STRUCTURAL_SIMILARITY, score);
    }
    long end = System.currentTimeMillis();
    System.out.println("Structure Sim:" + ((end - start) / 1000));
  }

  private boolean sortAnnotations(Annotation ann, AnnotationSet inputAS,
                                  List<Annotation> contextAnnotations) {
    AnnotationSet sentCoveringAnnots =
      inputAS.getCovering("Sentence", ann.getStartNode().getOffset(), ann
        .getEndNode().getOffset());
    if(sentCoveringAnnots == null || sentCoveringAnnots.isEmpty())
      return false;
    Annotation sentAnnot = sentCoveringAnnots.iterator().next();
    // separate annotations in three categories
    // unambiguous
    // annotations in other sentences
    // annotations in the same sentence
    List<Annotation> sameSentAnnotations = new ArrayList<Annotation>();
    List<Annotation> otherAnnotations = new ArrayList<Annotation>();
    List<Annotation> unambiguousAnnotations = new ArrayList<Annotation>();
    for(Annotation annot : contextAnnotations) {
      AnnotationSet lookupSet1 =
        inputAS.getContained(gate.Utils.start(annot), gate.Utils.end(annot))
          .get("Lookup");
      if(lookupSet1.size() == 1) {
        unambiguousAnnotations.add(annot);
        continue;
      }
      AnnotationSet sentCoveringAnnots1 =
        inputAS.getCovering("Sentence", annot.getStartNode().getOffset(), annot
          .getEndNode().getOffset());
      if(sentCoveringAnnots1 == null || sentCoveringAnnots1.isEmpty())
        continue;
      Annotation sentAnnot1 = sentCoveringAnnots1.iterator().next();
      if(sentAnnot1 == sentAnnot)
        sameSentAnnotations.add(sentAnnot1);
      else otherAnnotations.add(sentAnnot1);
    }
    // now we sort the annotations
    CloserAnnotationComparator<Annotation> cac =
      new CloserAnnotationComparator<Annotation>(gate.Utils.start(ann),
        gate.Utils.end(ann));
    Collections.sort(unambiguousAnnotations, cac);
    Collections.sort(sameSentAnnotations, cac);
    Collections.sort(otherAnnotations, cac);
    contextAnnotations.clear();
    contextAnnotations.addAll(unambiguousAnnotations);
    contextAnnotations.addAll(sameSentAnnotations);
    contextAnnotations.addAll(otherAnnotations);
    return true;
  }

  /**
   * calculates similarity using the ontology as context: favorise more specific
   * annotations among those that overlap (e.g. subclasses over superclasses)
   * and annotations that have a relation with some of the context annotations
   * in the ontology
   * 
   * @param ann
   * @param overlappedAnnotations
   * @param contextAnnotations
   * @return
   */
  float findStucturalSimilarity(Annotation ann,
                                List<Annotation> contextAnnotations) {
    return 0.0F;
  }

  /**
   * return true if there exist relation between the two URIs, false otherwise
   * 
   * @param inst
   * @param contextInst
   * @return
   */
  private int directRelationCount(String inst, String contextInst) {
    if(inst != null && contextInst != null && inst.equals(contextInst)) { return -1; }
    long startTime = Benchmark.startPoint();
    Set<String> termsToSearch = new HashSet<String>();
    termsToSearch.add(inst + "-" + contextInst);
    int count = 0;
    // search for sb
    // searchInField, termsToSearch, fieldsToReturn, maxNumberOfResults
    List<Hit> hits;
    try {
      hits = searcher.searchTerms(null, termsToSearch, null, 1000);
    } catch(IndexException e) {
      benchmarkCheckpoint(startTime, "__directRelations");
      return 0;
    }
    if(hits != null) {
      count = hits.size();
    }
    termsToSearch.clear();
    termsToSearch.add(contextInst + "-" + inst);
    // search for sb
    // searchInField, termsToSearch, fieldsToReturn, maxNumberOfResults
    try {
      hits = searcher.searchTerms(null, termsToSearch, null, 1000);
    } catch(IndexException e) {
      benchmarkCheckpoint(startTime, "__directRelations");
      return 0;
    }
    if(hits != null) {
      count += hits.size();
    }
    /*
     * String query = "SELECT (COUNT(*) AS ?count) WHERE {<" + inst +
     * "> ?relation <" + contextInst + "> }"; OntologyTupleQuery tupleQuery =
     * ontology.createTupleQuery(query, OConstants.QueryLanguage.SPARQL);
     * tupleQuery.evaluate(); count =
     * Integer.parseInt(tupleQuery.next().elementAt(0).getLiteral().getValue());
     * if(count == 0) { query = "SELECT (COUNT(*) AS ?count) WHERE {<" +
     * contextInst + "> ?relation <" + inst + "> }"; tupleQuery =
     * ontology.createTupleQuery(query, OConstants.QueryLanguage.SPARQL);
     * tupleQuery.evaluate(); count = Integer
     * .parseInt(tupleQuery.next().elementAt(0).getLiteral().getValue());
     * 
     * }
     */
    benchmarkCheckpoint(startTime, "__directRelations");
    return count;
  }

  /**
   * return true if there exist relation between the two URIs, false otherwise
   * 
   * @param inst
   * @param contextInst
   * @return
   */
  private int indirectRelationCount(String inst, String contextInst) {
    if (inst == null || contextInst == null || inst.equals(contextInst)) {
      return -1;
    }
    String debugIndirect = System.getProperty("debugIndirect");
    if(debugIndirect == null) {
      debugIndirect = "old";
    }

    int countOld = 0;
    int countNew = 0;
    int countRet = 0;

    if (debugIndirect.equals("both") || debugIndirect.equals("old")) {
      long startTime = Benchmark.startPoint();

      String query =
              "SELECT (COUNT(*) AS ?count) WHERE {<" + inst
              + "> ?relation ?a . ?a ?relation1 <" + contextInst + "> }";

      OntologyTupleQuery tupleQuery =
              ontology.createTupleQuery(query, OConstants.QueryLanguage.SPARQL);
      try {
        tupleQuery.evaluate();
        countOld =
              Integer.parseInt(tupleQuery.next().elementAt(0).getLiteral().getValue());
      } catch (Exception e) {
        System.err.println("Got an exception executing the query: "+query);
        e.printStackTrace(System.err);
        countOld = 0;
      }


      if (countOld == 0) {
        query =
                "SELECT (COUNT(*) AS ?count) WHERE {<" + contextInst
                + "> ?relation ?a . ?a ?relation1 <" + inst + "> }";
        tupleQuery =
                ontology.createTupleQuery(query, OConstants.QueryLanguage.SPARQL);
        try {
          tupleQuery.evaluate();
          countOld =
             Integer.parseInt(tupleQuery.next().elementAt(0).getLiteral().getValue());
        } catch (Exception e) {
          System.err.println("Got an exception executing the query: "+query);
          e.printStackTrace(System.err);
          countOld = 0;
        }
      }
      benchmarkCheckpoint(startTime, "__indirectRelationsOld");
      countRet = countOld;
    }
    if (debugIndirect.equals("new") || debugIndirect.equals("both")) {
      long startTime = Benchmark.startPoint();
      askIndirectQuery.setBinding("from", ontology.createOURI(inst));
      askIndirectQuery.setBinding("to", ontology.createOURI(contextInst));
      boolean result = false;
      try {
        result = askIndirectQuery.evaluate();
      } catch (Exception e) {
        System.err.println("Got an exception executing the query: "+askIndirectQuery);
        e.printStackTrace(System.err);
      }
      if(result) {
        countNew = 1;
      } else {
        countNew = 0;
      }
      benchmarkCheckpoint(startTime, "__indirectRelationsNew");
      if(debugIndirect.equals("new")) {
        countRet = countNew;
      }
    }
    if (debugIndirect.equals("both")) {
      if(countOld == 0 && countNew == 0) {
        // ignore this for now
      } else {
        if(countOld == 0 || countNew == 0) {
          System.err.println("ERROR counts not both >0, old/new: "+countOld+"/"+countNew);
        } else {
          System.err.println("GOOD: counts equal!");
        }
      }
    }
    return countRet;
  }

  /**
   * The cleanup method
   */
  @Override
  public void cleanup() {
    super.cleanup();
    if (searcher != null) {
      try {
        searcher.close();
        searcher = null;
      } catch (IndexException e) {
        throw new GateRuntimeException("could not close the searcher", e);
      }
    }
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

  public Boolean getDeleteAnnotationsWithNoRelation() {
    return deleteAnnotationsWithNoRelation;
  }

  public URL getLuceneIndexDirURL() {
    return luceneIndexDirURL;
  }

  @CreoleParameter
  @Optional
  public void setLuceneIndexDirURL(URL luceneIndexDirURL) {
    this.luceneIndexDirURL = luceneIndexDirURL;
  }

  /**
   * If this parameter is set to true, and if there is at least one annotation
   * found with relationsCount > 0, all other overlapping annotations with
   * relationsCount == 0 are deleted.
   * 
   * @param deleteAnnotationsWithNoRelation
   */
  @CreoleParameter
  @RunTime
  @Optional
  public void setDeleteAnnotationsWithNoRelation(Boolean deleteAnnotationsWithNoRelation) {
    this.deleteAnnotationsWithNoRelation = deleteAnnotationsWithNoRelation;
  }

  protected void benchmarkCheckpoint(long startTime, String name) {
    if(Benchmark.isBenchmarkingEnabled()) { 
      Benchmark.checkPointWithDuration(
              Benchmark.startPoint()-startTime, 
              Benchmark.createBenchmarkId(name, this.getBenchmarkId()),
              this,null); 
    }
  }

  public String getBenchmarkId() {
    return benchmarkId;
  }

  public void setBenchmarkId(String string) {
    benchmarkId = string;
  }
  private String benchmarkId = this.getName();
  
  
} // class DisambiguatePR

/**
 * annotation that computes how close two annotation are from a reference
 * annotation
 * 
 * @author niraj
 */
class CloserAnnotationComparator<E> implements Comparator<Annotation> {
  private long stOffset;

  private long enOffset;

  public CloserAnnotationComparator(long stOffset, long enOffset) {
    this.stOffset = stOffset;
    this.enOffset = enOffset;
  }

  public int compare(Annotation o1, Annotation o2) {
    long o1StOffset = Utils.start(o1);
    long o1EnOffset = Utils.end(o1);
    long o2StOffset = Utils.start(o2);
    long o2EnOffset = Utils.end(o2);
    // the annotation that is closer should be given higher priority
    long o1Distance = 0;
    long o2Distance = 0;
    if(o1StOffset >= enOffset) {
      // annotation on the right hand side
      o1Distance = o1StOffset - enOffset;
    } else if(o1EnOffset <= stOffset) {
      // annotation on the left hand side
      o1Distance = stOffset - o1EnOffset;
    }
    if(o2StOffset >= enOffset) {
      // annotation on the right hand side
      o2Distance = o2StOffset - enOffset;
    } else if(o2EnOffset <= stOffset) {
      // annotation on the left hand side
      o2Distance = stOffset - o2EnOffset;
    }
    if(o1Distance < o2Distance) {
      return -1;
    } else if(o1Distance > o2Distance) {
      return 1;
    } else {
      return 0;
    }
  }
}
