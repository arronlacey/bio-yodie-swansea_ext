package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.ProcessingResource;
import gate.Resource;
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
import gate.util.GateRuntimeException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Popularity Similarity PR", comment = "This PR uses a lucene index created from sources such as DBPedia or Freebase to obtain the popularity score for each URI associated with the string.")
public class PopularitySimilarityPR extends AbstractLanguageAnalyser implements
  ProcessingResource {
  private static final String COUNT = "count";

  private static final String INST2 = "inst";

  private static final String RDF_COUNT = "rdf-count";

  /**
   * serial version
   */
  private static final long serialVersionUID = 2538181800238288794L;

  /**
   * url of the lucene index
   */
  private URL luceneIndexDirURL;

  /**
   * url of the rdf lucene index
   */
  private URL rdfLuceneIndexDirURL;

  /**
   * The searcher object
   */
  private Searcher searcher;

  /**
   * The searcher object
   */
  private Searcher rdfSearcher;

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
    if(rdfLuceneIndexDirURL != null) {
      // initialize searcher
      try {
        rdfSearcher = new Searcher(new File(rdfLuceneIndexDirURL.toURI()));
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
    // calling the cleanup method first
    if(searcher != null) {
      cleanup();
    }
    init();
  } // reInit()

  @Override
  public void execute() throws ExecutionException {
    // record the start time
    long start = System.currentTimeMillis();
    // input and output AS
    AnnotationSet inputAS = document.getAnnotations(getInputASName());
    // annotations to be disambiguated
    List<Annotation> annotationsToBeDisambiguated = new ArrayList<Annotation>();
    for(String aType : annotationTypes) {
      annotationsToBeDisambiguated.addAll(inputAS.get(aType));
    }
    // no annotations found so just return
    if(annotationsToBeDisambiguated.isEmpty()) return;
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
    List<String> annotTypes = new ArrayList<String>();
    annotTypes.add("Lookup");
    // one sem annotation at a time
    for(Annotation ann : annotationsToBeDisambiguated) {
      // when user interrupts it.. respect the user's command
      if(interrupted) return;
      // we need inst
      String inst = (String)ann.getFeatures().get(INST2);
      if(inst == null) continue;
      // if already it has a count feature that is non zero, skip
      if(ann.getFeatures().containsKey(COUNT)) {
        String countStr = (String)ann.getFeatures().get(COUNT);
        if(!countStr.equals("0")) continue;
      }
      // search for sb
      List<Hit> hits;
      try {
        hits = searcher.search(INST2, inst, null, true, true, false, 1);
      } catch(IndexException e) {
        throw new ExecutionException(
          "Exception occurred while searching in the lucene index", e);
      }
      if(!ann.getFeatures().containsKey(COUNT)) {
        // put the count on annotation feature
        ann.getFeatures().put(COUNT, "0");
      }
      if(hits.size() > 0)
        ann.getFeatures().put(COUNT,
          hits.iterator().next().getMap().get(COUNT));
    }
    
    for(Annotation ann : annotationsToBeDisambiguated) {
      
      if(interrupted) return;
      // we need inst
      String inst = (String)ann.getFeatures().get(INST2);
      if(inst == null) continue;
      
      // if already it has a count feature that is non zero, skip
      if(ann.getFeatures().containsKey(RDF_COUNT)) {
        String countStr = (String)ann.getFeatures().get(RDF_COUNT);
        if(!countStr.equals("0")) continue;
      }
      
      // lets obtain the rdf-count as well
      // but only if it is provided
      if(rdfSearcher != null) {
        // lets also search for rdf count
        // search for sb
        List<Hit> hits;
        try {
          hits = rdfSearcher.search(INST2, inst, null, true, true, false, 1);
        } catch(IndexException e) {
          throw new ExecutionException(
            "Exception occurred while searching in the lucene index", e);
        }
        if(!ann.getFeatures().containsKey(RDF_COUNT)) {
          // put the count on annotation feature
          ann.getFeatures().put(RDF_COUNT, "0");
        }
        if(hits.size() > 0)
          ann.getFeatures().put(RDF_COUNT,
            hits.iterator().next().getMap().get(RDF_COUNT));
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
        CommonUtils.getContainedCoveringAnnots(ann, inputAS, annotTypes);
      overlappingAnnots.add(ann);
      for(Annotation ann1 : overlappingAnnots) {
        overlappingMap.put(ann1, overlappingAnnots);
      }
    }

    // now normalizing score
    for(Annotation ann : annotationsToBeDisambiguated) {
      // only if current annotation has count feature
      String annCount = (String)ann.getFeatures().get(COUNT);
      String rdfCount = (String)ann.getFeatures().get(RDF_COUNT);
      if(annCount == null) annCount = "0";
      if(rdfCount == null) rdfCount = "0";
      
      float currentAnnotCount = Float.parseFloat(annCount);
      float currentRdfCount = Float.parseFloat(rdfCount);
      
      // obtain popularity count for other overlapping
      // annotations
      Set<Annotation> overlappingAnnots = overlappingMap.get(ann);
      
      int totalCount = 0;
      for(Annotation a : overlappingAnnots) {
        String count = (String)a.getFeatures().get(COUNT);
        if(count != null) {
          totalCount += Integer.parseInt(count);
        }
      }
      
      int totalRDFCount = 0;
      for(Annotation a : overlappingAnnots) {
        String cRdfCount = (String)a.getFeatures().get(RDF_COUNT);
        if(cRdfCount != null) {
          totalRDFCount += Integer.parseInt(cRdfCount);
        }
      }
      
      float rdfScore = 0.0F;
      if(totalRDFCount > 0) {
        rdfScore = (float) (currentRdfCount / totalRDFCount);
        ann.getFeatures().put(SemanticConstants.RDF_POPULARITY_SIMILARITY, rdfScore);
        rdfScore = (float) (rdfScore*0.5F);
      }
      
      float countScore = 0.0F;
      if(totalCount > 0) {
        countScore = (float) (currentAnnotCount / totalCount);
        ann.getFeatures().put(SemanticConstants.CAPTION_POPULARITY_SIMILARITY, rdfScore);
        countScore = (float) (countScore*0.5F);
      }
      
      float score = rdfScore + countScore;
      ann.getFeatures().put(SemanticConstants.POPULARITY_SIMILARITY, score);
    }
    
    long end = System.currentTimeMillis();
    System.out.println("Total time for popularity pr:" + (end - start));
  }

  /**
   * The cleanup method
   */
  @Override
  public void cleanup() {
    super.cleanup();
    // close the searcher object
    try {
      searcher.close();
      searcher = null;
    } catch(IndexException e) {
      throw new GateRuntimeException("could not close the searcher", e);
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

  public URL getLuceneIndexDirURL() {
    return luceneIndexDirURL;
  }

  @CreoleParameter
  public void setLuceneIndexDirURL(URL luceneIndexDirURL) {
    this.luceneIndexDirURL = luceneIndexDirURL;
  }

  public URL getRdfLuceneIndexDirURL() {
    return rdfLuceneIndexDirURL;
  }

  @CreoleParameter
  @Optional
  public void setRdfLuceneIndexDirURL(URL rdfLuceneIndexDirURL) {
    this.rdfLuceneIndexDirURL = rdfLuceneIndexDirURL;
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
} // class PopularitySimilarityPR
