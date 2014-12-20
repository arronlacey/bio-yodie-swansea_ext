package gate.creole.gazetteer.lucene;

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
import gate.util.Benchmark;
import gate.util.Benchmarkable;
import gate.util.GateRuntimeException;
import gate.util.InvalidOffsetException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is the implementation of the resource Lucene based gazetteer.
 */
@CreoleResource(name = "Match Feature Value PR", comment = "annotates terms by searching the value of the feature provided as argument")
public class MatchFeatureValuePR extends AbstractLanguageAnalyser implements
		ProcessingResource, Benchmarkable {

	/**
	 * for serialisation
	 */
	private static final long serialVersionUID = 7434037183514920676L;

	/**
	 * List of the annotation types to be used for lookup.
	 */
	private String inputAnnotationType;


	/**
	 * Name of the input annotation set name
	 */
	private String inputASName;


        /**
         * Name of the feature whose value needs to be matched in lucene indices
         */
        private String featureName;


        /** tells the program what should be done when a feature with the same name as obtained from the lucene index is found */
	private UpdateExistingFeaturePolicy updateExistingFeaturePolicy;

	/**
	 * url of the lucene index
	 */
	private URL luceneIndexDirURL;

	/**
	 * The searcher object
	 */
	private Searcher searcher;

	/** Initialise this resource, and return it. */
	public Resource init() throws ResourceInstantiationException {
		// check that luceneIndexDirURL is provided
		if (luceneIndexDirURL == null) {
			throw new ResourceInstantiationException(
					"luceneIndexDirURL parameter cannot be null");
		}

		// initialize searcher
		try {
			searcher = new Searcher(new File(luceneIndexDirURL.toURI()));
		} catch (IndexException e) {
			throw new ResourceInstantiationException(
					"Error occurred when initializing searcher", e);
		} catch (URISyntaxException e) {
			throw new ResourceInstantiationException(
					"lucene index directory could not be converted to a file object",
					e);
		}

		return this;

	} // init()

	/**
	 * Reinitialises the processing resource. After calling this method the
	 * resource should be in the state it is after calling init. If the resource
	 * depends on external resources (such as rules files) then the resource
	 * will re-read those resources. If the data used to create the resource has
	 * changed since the resource has been created then the resource will change
	 * too after calling reInit().
	 */
	public void reInit() throws ResourceInstantiationException {

		// calling the cleanup method first
		if (searcher != null) {
			cleanup();
		}

		init();
	} // reInit()

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
		} catch (IndexException e) {
			throw new GateRuntimeException("could not close the searcher", e);
		}
	}

	String puncs = ":";

	public void execute() throws ExecutionException {

		// document sanity check
		if (document == null) {
			throw new ExecutionException("Document cannot be null");
		}

		// the annotation type must be available
		if (inputAnnotationType == null) {
			throw new ExecutionException(
					"You must provide annotation type");
		}

		// user must provide feature name
		if (featureName == null) {
			throw new ExecutionException(
					"You must provide feature name");
		}


		// input annotation set
		AnnotationSet inputAS = document.getAnnotations(inputASName);

		Set<Annotation> setOfSpecificType = inputAS.get(inputAnnotationType);

		// one annotation at a time
		outer: for (Annotation a : setOfSpecificType) {

			// when user interrupts it.. respect the user's command
			if (interrupted)
				return;

			// if there's no such feature as provided by the user, continue
			if(!a.getFeatures().containsKey(featureName)) continue;

			// what is that we would want to search?
			String s = a.getFeatures().get(featureName).toString();
				
			// searchInField = left to null so that searcher decides in
			// which field to look for it
			// the query is the annotation string
			// asking system to return values all the stored fields
			// shouldStartWith and shouldEndWith set to true for exact match
			// wildcard search which is not supported yet
			// number of hits
			List<Hit> hits;
                        long startTime = Benchmark.startPoint();
			try {
				hits = searcher.search(null, s, null, true, true, false, 2000);
			} catch (IndexException e) {
				throw new ExecutionException(
					"Exception occurred while searching in the lucene index",
					e);
			}
                        benchmarkCheckpoint(startTime, "__LuceneSearch");
                        startTime = Benchmark.startPoint();
			if (hits.size() != 0) {
				for (Hit h : hits) {
					for (String key : h.getMap().keySet()) {
						String val = h.getMap().get(key);

                                                if(a.getFeatures().containsKey(key)) {
							if(updateExistingFeaturePolicy == UpdateExistingFeaturePolicy.REPLACE_EXISTING) {
								a.getFeatures().put(key, val);
							} else if(updateExistingFeaturePolicy ==
                                                                           UpdateExistingFeaturePolicy.APPEND_TO_EXISTING) {
								String eVal = a.getFeatures().get(key).toString();
								eVal += ", " + val;
								a.getFeatures().put(key, eVal);
							} else if(updateExistingFeaturePolicy == UpdateExistingFeaturePolicy.IGNORE) {
								continue;
							}
						} else {
							a.getFeatures().put(key, val);
						}
					}
				}
			}
                        benchmarkCheckpoint(startTime, "__UpdateAnnotations");
		}
	}

	public String getInputAnnotationType() {
		return inputAnnotationType;
	}

	@RunTime
	@CreoleParameter(defaultValue = "Sem_Location")
	public void setInputAnnotationType(String inputAnnotationType) {
		this.inputAnnotationType = inputAnnotationType;
	}

	public String getInputASName() {
		return inputASName;
	}

	@RunTime
	@CreoleParameter(defaultValue = "inst")
	public void setFeatureName(String featureName) {
		this.featureName = featureName;
	}

	public String getFeatureName() {
		return featureName;
	}


	@RunTime
	@Optional
	@CreoleParameter
	public void setInputASName(String inputASName) {
		this.inputASName = inputASName;
	}

	public URL getLuceneIndexDirURL() {
		return luceneIndexDirURL;
	}

	@CreoleParameter
	public void setLuceneIndexDirURL(URL luceneIndexDirURL) {
		this.luceneIndexDirURL = luceneIndexDirURL;
	}


	@RunTime
	@CreoleParameter(defaultValue = "APPEND_TO_EXISTING")
	public void setUpdateExistingFeaturePolicy(UpdateExistingFeaturePolicy updateExistingFeaturePolicy) {
		this.updateExistingFeaturePolicy = updateExistingFeaturePolicy;
	}

	public UpdateExistingFeaturePolicy getUpdateExistingFeaturePolicy() {
		return updateExistingFeaturePolicy;
	}

	// ############### getters and setter ###########

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
  

} // class MatchFeatureValuePR
