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
import gate.ri.RandomIndexing;
import gate.ri.sv.RandomIndexingSV;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Word Sample RI Contextual Similarity PR", comment = "Uses Random Indexing to select representative word sample.")
public class WordSampleRIContextualSimilarityPR extends AbstractLanguageAnalyser implements
  ProcessingResource {
  /**
   * serial version UID
   */
  private static final long serialVersionUID = -6743723360747309420L;

  static Set<String> insts = new HashSet<String>();

  /**
   * List of the annotation types to be used for lookup.
   */
  private List<String> annotationTypes;

  /**
   * name of the input annotation set
   */
  private String inputASName;

  /**
   * the term space to be used for random indexing
   */
  protected URL termVectorsFileUrl;

  /**
   * the document space to be used for random indexing
   */
  protected URL docVectorsFileUrl;

  /**
   * variables used when calling random indexing
   */
  private Set<String> annotationNames, featureNames, featureValues;

  /**
   * the cache file keywords for previously visited instances are kept.
   */
  private URL randomIndexingCacheFileUrl;

  /**
   * random indexing cache
   */
  private Map<String, String> _riCache;

  /**
   * Whether or not to use only cached RI results.
   */
  private boolean searchOnlyInCache = false;

  /**
   * How many salient words to fetch from RI per candidate.
   */
  private int wordstofind = 20;

  /**
   * Context window size in tokens.
   */
  private int contextWindow = 30;

  /**
   * Output feature.
   */
  //private String outputFeature = SemanticConstants.CONTEXTUAL_SIMILARITY_RI;
  private String outputFeature = "scContextualSimilarityRI";
  
  /**
   * use this method to produce random indexing cache
   * 
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    BufferedReader br =
      new BufferedReader(new InputStreamReader(new FileInputStream(args[0]),
        "UTF-8"));
    BufferedWriter bw =
      new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[3]),
        "UTF-8"));
    String inst = null;
    File termVectorsFile = new File(args[1]);
    File docVectorsFile = new File(args[2]);
    RandomIndexing randomIndexing = new RandomIndexingSV();
    String termVecFile = termVectorsFile.getAbsolutePath();
    String docVecFile = docVectorsFile.getAbsolutePath();
    while((inst = br.readLine()) != null) {
      Set<String> result =
        randomIndexing.findSimilarTermsForDoc(termVecFile, docVecFile, inst,
          20, false);
      // to return
      StringBuffer resultString = new StringBuffer("");
      for(String term : result) {
        resultString.append(term).append(" ");
      }
      bw.write(inst + "\t" + resultString.toString());
      bw.newLine();
      bw.flush();
    }
    bw.close();
    br.close();
  }

  /** Initialise this resource, and return it. */
  public Resource init() throws ResourceInstantiationException {
    // check for needed parameters
    if(termVectorsFileUrl == null) { throw new ResourceInstantiationException(
      "term vectors file cannot be null"); }
    if(docVectorsFileUrl == null) { throw new ResourceInstantiationException(
      "doc vectors file cannot be null"); }
    // initialize cache
    _riCache = new HashMap<String, String>();
    // populate cache
    if(randomIndexingCacheFileUrl != null) {
      BufferedReader br = null;
      System.out.println("Reading random indexing cache");
      try {
        br =
          new BufferedReader(new InputStreamReader(
            randomIndexingCacheFileUrl.openStream(), "UTF-8"));
        String line = null;
        while((line = br.readLine()) != null) {
          String[] data = line.split("\t");
          if(data.length < 1) continue;
          String words = "";
          if(data.length == 2) {
            words = data[1];
          }
          // first element is the instance
          // second element is the list of keywords
          String existingData = _riCache.get(data[0]);
          if(existingData != null) words = words + " " + existingData;
          _riCache.put(data[0], words.toLowerCase().trim());
          if(_riCache.size() % 10000 == 0)
            System.out.println("Read .." + _riCache.size() + " entries");
        }
      } catch(UnsupportedEncodingException e) {
        throw new ResourceInstantiationException(e);
      } catch(IOException e) {
        throw new ResourceInstantiationException(e);
      } finally {
        if(br != null) {
          try {
            br.close();
          } catch(IOException e) {
            throw new ResourceInstantiationException(e);
          }
        }
      }
    }
    annotationNames = new HashSet<String>();
    featureNames = new HashSet<String>();
    featureValues = new HashSet<String>();
    // obtain the value of token
    annotationNames.add("Token");
    // only if its POS category is one of the following
    // the strings underneath such tokens is used as contextual string
    // and is compared with the output from random indexing
    featureNames.add("category");
    featureValues.add("NN");
    featureValues.add("NNP");
    featureValues.add("NNS");
    featureValues.add("NNPS");
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
    // first clean up all the resources
    cleanup();
    init();
  } // reInit()

  @Override
  public void execute() throws ExecutionException {
    // record the start time
    long start = System.currentTimeMillis();
    // input and output AS
    AnnotationSet inputAS = document.getAnnotations(getInputASName());
    // annotations that should be disambiguated
    List<Annotation> annotationsToBeDisambiguated = new ArrayList<Annotation>();
    for(String annotType : annotationTypes) {
      annotationsToBeDisambiguated.addAll(inputAS.get(annotType));
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
    Set<String> annotTypesSet = new HashSet<String>(annotationTypes);
    annotTypesSet.add("Person");
    annotTypesSet.add("Organization");
    annotTypesSet.add("Location");
    // adding ners in the context
    AnnotationSet nerSet = inputAS.get(annotTypesSet);
    Set<String> extraTerms = new HashSet<String>();
    for(Annotation nerAnnot : nerSet) {
      String nerTerm = (String) nerAnnot.getFeatures().get("string");
      if(nerTerm == null) continue;

      extraTerms.add(nerTerm);
    }

    // one sem annotation at a time
    for(Annotation ann : annotationsToBeDisambiguated) {
      // when user interrupts it.. respect the user's command
      if(interrupted) return;
      if(!ann.getFeatures().containsKey("inst")) continue;
      // if already computed, do not computer again
      if(ann.getFeatures().containsKey(outputFeature))
        continue;
      String instanceUri =
        //(String)ann.getFeatures().get(SemanticConstants.LKB_FEATURE_INST);
        (String)ann.getFeatures().get("lkbFeat");
      // see if the context annotations were earlier recovered for this
      // particular span?
      List<Annotation> contextAnnotations =
        CommonUtils.getContextAnnotations(ann, inputAS, annotTypesSet, contextWindow,
          false);
      Set<String> extraTermsForThisAnnot = new HashSet<String>(extraTerms);
      String annStr = (String) ann.getFeatures().get("string");
      if(annStr != null) {
        extraTerms.remove(annStr);
      }

      float score =
        findContextualSimilarity(instanceUri, contextAnnotations,
          extraTermsForThisAnnot);
      ann.getFeatures().put(outputFeature, score);
    }
    long end = System.currentTimeMillis();
    System.out.println("Word Sample Context Sim:" + ((end - start) / 1000));
  }

  /**
   * The cleanup method
   */
  @Override
  public void cleanup() {
    super.cleanup();
  }

  /**
   * calculates similarity using the semantic space as context: for each NE URI
   * the context is the value of dbpedia:abstract; then for each overlapped URI
   * calculate the score based on its similarity with the 'context'
   * 
   * @param ann
   * @param contextAnnotations
   * @return
   */
  float findContextualSimilarity(String inst,
                                 List<Annotation> contextAnnotations,
                                 Set<String> extraTerms) {
    // to return
    float score = new Float(0.0000000);
    if(contextAnnotations == null) { return score; }
    // inst>docterms.bin; out: 20 terms from terms.bin
    String contextuallyRelatedTerms = findSimilarTerms(inst);
    if(contextuallyRelatedTerms == null ||
      contextuallyRelatedTerms.trim().length() == 0) return 0.0F;
    contextuallyRelatedTerms =
      CommonUtils.normaliseAccentedChars(contextuallyRelatedTerms);
    /*
     * // extract terms TermExtractor termExtractor = new TermExtractor(false);
     * 
     * Long startNode = null; Long endNode = null;
     */
    StringBuffer contextStr = new StringBuffer();
    // simply obtain strings for the annotations
    if(contextAnnotations != null && !contextAnnotations.isEmpty()) {
      for(Annotation ca : contextAnnotations) {
        String caStr = (String) ca.getFeatures().get("string");
        if(caStr != null) {
          contextStr.append(" " + caStr);
        }
      }
    }
    if(extraTerms != null) {
      for(String et : extraTerms) {
        contextStr.append(" " + et);
      }
    }
    return CommonUtils.match(
      CommonUtils.normaliseAccentedChars(contextStr.toString()),
      contextuallyRelatedTerms, false);
  }

  /**
   * call random indexing to find out terms for the given instance
   * 
   * @param inst
   * @return
   */
  public String findSimilarTerms(String inst) {
    if(_riCache.containsKey(inst)) {
      return _riCache.get(inst);
    } else if(searchOnlyInCache) { return ""; }
    // call the random indexing to find out terms that are similar to the
    // provided inst URI
    RandomIndexing randomIndexing = new RandomIndexingSV();
    String termVecFile =
      new File(termVectorsFileUrl.getFile()).getAbsolutePath();
    String docVecFile = new File(docVectorsFileUrl.getFile()).getAbsolutePath();
    Set<String> result =
      randomIndexing.findSimilarTermsForDoc(termVecFile, docVecFile, inst, wordstofind,
        false);
    // to return
    StringBuffer resultString = new StringBuffer("");
    for(String term : result) {
      resultString.append(term).append(" ");
    }
    // add it to the cache for next time
    _riCache.put(inst, resultString.toString());
    // return the result
    return resultString.toString();
  }

  public List<String> getAnnotationTypes() {
    return annotationTypes;
  }

  @CreoleParameter(defaultValue = "Lookup")
  @RunTime
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

  public URL getTermVectorsFileUrl() {
    return termVectorsFileUrl;
  }

  @CreoleParameter
  public void setTermVectorsFileUrl(URL termVectorsFileUrl) {
    this.termVectorsFileUrl = termVectorsFileUrl;
  }

  public URL getDocVectorsFileUrl() {
    return docVectorsFileUrl;
  }

  @CreoleParameter
  public void setDocVectorsFileUrl(URL docVectorsFileUrl) {
    this.docVectorsFileUrl = docVectorsFileUrl;
  }

  public URL getRandomIndexingCacheFileUrl() {
    return randomIndexingCacheFileUrl;
  }

  @CreoleParameter(defaultValue = "./ri-spaces/tac-ri-inst-cache.txt")
  @Optional
  public void setRandomIndexingCacheFileUrl(URL randomIndexingCacheFileUrl) {
    this.randomIndexingCacheFileUrl = randomIndexingCacheFileUrl;
  }

  public Boolean getSearchOnlyInCache() {
    return Boolean.valueOf(this.searchOnlyInCache);
  }

  @CreoleParameter
  @RunTime
  @Optional
  public void setSearchOnlyInCache(Boolean soic) {
    this.searchOnlyInCache = soic.booleanValue();
  }

  public Integer getWordsToFind() {
    return Integer.valueOf(this.wordstofind);
  }

  @CreoleParameter
  @RunTime
  @Optional
  public void setWordsToFind(Integer wtf) {
    this.wordstofind = wtf.intValue();
  }
  
  public Integer getContextWindow() {
    return Integer.valueOf(this.contextWindow);
  }

  @CreoleParameter
  @RunTime
  @Optional
  public void setContextWindow(Integer conw) {
    this.contextWindow = conw.intValue();
  }

  public String getOutputFeature() {
    return this.outputFeature;
  }

  @CreoleParameter
  @RunTime
  @Optional
  public void setOutputFeature(String outfeat) {
    this.outputFeature = outfeat;
  }
} // class WordSampleRIContextualSimilarityPR
