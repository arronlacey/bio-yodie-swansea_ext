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
import gate.util.GateRuntimeException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is the implementation of Lucene Based Contextual Similarity PR. at the
 * indexing time, instead of using abstracts, we are using RDF triples as values
 * for building context. We index this context with lucene indexer (LuceneGaz
 * plugin). So in a triple of (subject, predicate, object), we use the following
 * for objects: labels, direct non-instance property values and comments. We
 * create one document per URI and index with lucene.
 * <p>
 * At the search time (this PR): from all nouns, and NNPs from the context of
 * (current 100 characters either side), we build the query vector (OR query).
 * We execute the query in Lucene and find out the topN (currently 500) URIs
 * that match the query. We find out the rank of the current candidate URI in
 * the result and generate a score which is (1 - rank/numberOfURIsFound + 0.001).
 * Here 0.001 is added to avoid getting score that is zero.;
 */
@CreoleResource(name = "Lucene Based RDF Similarity PR", comment = "This PR calculates similarity for every candidate URI using Lucene.")
public class LuceneBasedRDFSimilarityPR extends AbstractLanguageAnalyser implements
ProcessingResource {
	/**
	 * serial version
	 */
	private static final long serialVersionUID = 2538181800238288794L;

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

	/**
	 * context length
	 */
	private int contextLength = 100;

	/**
	 * max number of URIs to obtain
	 */
	private int topNResults = 500;

	/**
	 * Name of the rank-based output feature
	 */
	//private String rankOutputFeature = SemanticConstants.CONTEXTUAL_SIMILARITY;
	private String rankOutputFeature = "rnLuceneContextualSimilarity";

	/**
	 * Name of the score-based output feature
	 */
	//private String scoreOutputFeature = SemanticConstants.CONTEXTUAL_SIMILARITY_LUCENE_SCORE;
	private String scoreOutputFeature = "scLuceneContextualSimilarity";

	/**
	 * Whether or not to use coreference information if available.
	 */
	private boolean useCoreference;

	/** Initialise this resource, and return it. */
	public Resource init() throws ResourceInstantiationException {
		// check that luceneIndexDirURL is provided
		if(luceneIndexDirURL == null) {
			System.err.println("LuceneIndexDirURL is not set!");
		} else {
			// initialize searcher
			try {
				searcher = new Searcher(new File(luceneIndexDirURL.toURI()));
			} catch(IndexException e) {
				throw new ResourceInstantiationException(
						"Error occurred when initializing searcher", e);
			} catch(URISyntaxException e) {
				throw new ResourceInstantiationException(
						"Lucene index directory could not be converted to a file object", e);
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
		long start = System.currentTimeMillis();


		DocumentEntitySet ents = new DocumentEntitySet(document, inputASName, 
				true, "LodieCoref");

		Iterator<Entity> entsit = null;

		if(document.getFeatures().get("lodie.scoring.keyOverlapsOnly")!=null
				&& document.getFeatures().get("lodie.scoring.keyOverlapsOnly")
				.toString().equals("true")) {
			entsit = ents.getKeyOverlapsIterator(document);
		} else {
			entsit = ents.getIterator();
		}

		while(entsit!=null && entsit.hasNext()){

			Entity ent = entsit.next();

			if(interrupted) return;

			AnnotationSet inputAS = document.getAnnotations(this.inputASName);

			Set<String> termsToSearch = ent.contextTokensAsList(contextLength, true);

			//Go ahead and do the search for this context

			List<String> insts = new ArrayList<String>();
			List<String> scores = new ArrayList<String>();
			if(!termsToSearch.isEmpty()) {
				try {
					List<gate.creole.gazetteer.lucene.Hit> hits = searcher.searchTerms(null, termsToSearch, null, topNResults);
					if(hits != null) {
						for(Hit aHit : hits) {
							String instURI = aHit.getMap().get("inst");
							String instScore = aHit.getMap().get("score");
							if(instURI != null) {
								insts.add(instURI);
								if(instScore!=null){
									scores.add(instScore);
								} else {
									scores.add("0");
								}
							}

						}
					}
				} catch(IndexException e) {
					throw new ExecutionException(
							"Exception occurred while searching in the lucene index", e);
				}
			}

			Set<String> candidatesbyinst = ent.getInstSet();

			Iterator<String> candsit = candidatesbyinst.iterator();

			while(candsit!=null && candsit.hasNext()){

				if(interrupted) return;

				String inst = candsit.next();
				//inst = gate.Utils.expandUriString(inst, CommonUtils.nsMap);
				inst = "http://dbpedia.org/resource/" + inst;

				float rscore = 0.0F;
				float lscore = 0F;
				if(insts != null && !insts.isEmpty()) {
					// we find out the position of instance in the result
					int index = insts.indexOf(inst);
					if(index >= 0) {
						lscore = Float.parseFloat(scores.get(index));
						index++;
						// 0.001 is added to avoid getting score that is zero
						rscore = (float)(1 - (float)index /insts.size() + 0.001);
					}
				}
				ent.putFeatureFloat(inst, rankOutputFeature, rscore);
				ent.putFeatureFloat(inst, scoreOutputFeature, lscore);
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Total time for lucene-based RDF contextual similarity PR:" + (end - start));
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

	public Integer getTopNResults() {
		return Integer.valueOf(topNResults);
	}

	@CreoleParameter
	@RunTime
	@Optional
	public void setTopNResults(Integer topNResults) {
		this.topNResults = topNResults.intValue();
	}

	public Integer getContextLength() {
		return Integer.valueOf(contextLength);
	}

	@CreoleParameter
	@RunTime
	@Optional
	public void setContextLength(Integer conlen) {
		this.contextLength = conlen.intValue();
	}

	public Boolean getuseCoreference() {
		return new Boolean(this.useCoreference);
	}

	@CreoleParameter(defaultValue = "true")
	@RunTime
	@Optional
	public void setuseCoreference(Boolean useCoreference) {
		this.useCoreference = useCoreference.booleanValue();
	}

	public String getRankBasedOutputFeature() {
		return this.rankOutputFeature;
	}

	@CreoleParameter
	@RunTime
	@Optional
	public void setRankBasedOutputFeature(String rankfeat) {
		this.rankOutputFeature = rankfeat;
	}

	public String getScoreBasedOutputFeature() {
		return this.scoreOutputFeature;
	}

	@CreoleParameter
	@RunTime
	@Optional
	public void setScoreBasedOutputFeature(String scorefeat) {
		this.scoreOutputFeature = scorefeat;
	}
} // class PopularitySimilarityPR
