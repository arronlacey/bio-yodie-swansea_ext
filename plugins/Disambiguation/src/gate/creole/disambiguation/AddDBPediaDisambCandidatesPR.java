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
import gate.creole.gazetteer.lucene.Hit;
import gate.creole.gazetteer.lucene.IndexException;
import gate.creole.gazetteer.lucene.Searcher;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import gate.util.InvalidOffsetException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "DBPedia Disamb Candidates PR", comment = "This PR uses a lucene index to add wiki disambiguates URIs")
public class AddDBPediaDisambCandidatesPR extends AbstractLanguageAnalyser
  implements ProcessingResource {
  /**
	 * 
	 */
  private static final long serialVersionUID = -3509788531829884817L;

  /**
   * url of the lucene index
   */
  private URL luceneIndexDirURL;

  /**
   * The searcher object
   */
  private Searcher searcher;

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
    // one sem annotation at a time
    for(Annotation ann : annotationsToBeDisambiguated) {
      // when user interrupts it.. respect the user's command
      if(interrupted) return;
      // only if current annotation has inst feature
      String inst = (String)ann.getFeatures().get("inst");
      if(inst == null) continue;
      // search for sb
      List<Hit> hits;
      try {
        hits = searcher.search("inst", inst, null, true, true, false, 2000);
        hits.addAll(searcher.search("sameInst", inst, null, true, true, false,
          2000));
      } catch(IndexException e) {
        throw new ExecutionException(
          "Exception occurred while searching in the lucene index", e);
      }
      Set<String> insts = new HashSet<String>();
      for(Hit h : hits) {
        insts.add(h.getMap().get("inst"));
        insts.add(h.getMap().get("sameInst"));
      }
      for(String i : insts) {
        // add this annotation only if there ins't already one
        try {
          addAnnotation(Utils.start(ann), Utils.end(ann), i);
        } catch(InvalidOffsetException e) {
          throw new ExecutionException(
            "Exception occurred when adding a new annotation", e);
        }
      }
    }
    long end = System.currentTimeMillis();
    System.out.println("Total time for add DBPedia Disamb Candidates PR:" +
      (end - start));
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
  @Optional
  public void setLuceneIndexDirURL(URL luceneIndexDirURL) {
    this.luceneIndexDirURL = luceneIndexDirURL;
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

  private void addAnnotation(Long start, Long end, String inst)
    throws InvalidOffsetException {
    FeatureMap temp = Factory.newFeatureMap();
    temp.put("inst", inst);
    AnnotationSet outputAS = document.getAnnotations(inputASName);
    AnnotationSet existingAnnotSet =
      outputAS.getContained(start, end).get("Lookup", temp);
    if(existingAnnotSet.size() == 0) {
      existingAnnotSet =
        outputAS.getCovering("Lookup", start, end).get("Lookup", temp);
    }
    if(existingAnnotSet.size() == 0) {
      outputAS.add(start, end, "Lookup", temp);
    }
  }
} // class PopularitySimilarityPR
