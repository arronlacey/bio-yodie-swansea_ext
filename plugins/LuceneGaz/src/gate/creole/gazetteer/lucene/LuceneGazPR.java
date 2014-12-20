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
import gate.util.GateRuntimeException;
import gate.util.InvalidOffsetException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is the implementation of the resource Lucene based gazetteer.
 */
@CreoleResource(name = "Lucene Gazetteer PR", comment = "annotates terms by searching them in the index")
public class LuceneGazPR extends AbstractLanguageAnalyser implements
		ProcessingResource {

	/**
	 * for serialisation
	 */
	private static final long serialVersionUID = 7434037183514920676L;

	/**
	 * List of the annotation types to be used for lookup.
	 */
	private List<String> annotationTypes;

	/**
	 * Name of the input annotation set name
	 */
	private String inputASName;

	/**
	 * Name of the annotationSet where the new annotations should be created
	 */
	private String outputASName;

	/**
	 * url of the lucene index
	 */
	private URL luceneIndexDirURL;

	/**
	 * The searcher object
	 */
	private Searcher searcher;

	/**
	 * if set to true, string is divided in number of n-grams (bi-grams,
	 * tri-grams etc.). Only if no match is found, uni-grams are queried.
	 */
	private Boolean considerMatchingNGrams;

	/**
	 * if there's one token in the entire annotation matching, the URI is
	 * assigned.
	 */
	private Boolean considerSingleTerms;

	/**
	 * if any entry in the index starts with the first token of the annotation.
	 */
	private Boolean considerMatchingWithSimilarStart;
	
	/**
	 * number of maximum hits to obtain
	 */
	private final int MAX_RESULTS = 200;

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

	Map<String, List<Hit>> hitsCache = new HashMap<String, List<Hit>>();

	public void execute() throws ExecutionException {
		// set the interrupted flag to false
		interrupted = false;

		// clear the cache when entering a new document
		hitsCache.clear();

		// document sanity check
		if (document == null) {
			throw new ExecutionException("Document cannot be null");
		}

		// there must be at least one annotation type available
		if (annotationTypes == null || annotationTypes.isEmpty()) {
			throw new ExecutionException(
					"You must provide at least one annotation type");
		}

		// input annotation set
		AnnotationSet inputAS = document.getAnnotations(inputASName);

		// one annotation type at a time
		for (String at : annotationTypes) {
			Set<Annotation> setOfSpecificType = inputAS.get(at);

			AnnotationSet outputAS = document.getAnnotations(outputASName);
			FeatureMap fullStringMap = Factory.newFeatureMap();
			fullStringMap.put("lookupRule", "fullString");

			// one annotation at a time
			outer: for (Annotation a : setOfSpecificType) {

				String annotStr = (String) a.getFeatures().get("string");
                                if(annotStr == null || annotStr.trim().length() == 0) continue;
				
				// to avoid adding duplicate annotations with same inst
				Set<String> addedInsts = new HashSet<String>();

				// when user interrupts it.. respect the user's command
				if (interrupted)
					return;

				/*
				 * // see if there's already an annotation on the entire string
				 * // with lookupRule=fullString. If so, no need to process it
				 * AnnotationSet foundAnnots = outputAS.getContained(
				 * Utils.start(a), Utils.end(a)).get("Lookup", fullStringMap);
				 * 
				 * for (Annotation fa : foundAnnots) { if
				 * (Utils.start(fa).equals(Utils.start(a)) &&
				 * Utils.end(fa).equals(Utils.end(a))) { continue outer; } }
				 */

                                String s = (String) a.getFeatures().get("string");
				for (int i = 0; i < s.length(); i++) {
					char c = s.charAt(i);
					if (puncs.indexOf(c) >= 0)
						continue outer;
				}

				// searchInField = left to null so that searcher decides in
				// which field to look for it
				// the query is the annotation string
				// asking system to return values all the stored fields
				// shouldStartWith and shouldEndWith set to true for exact match
				// wildcard search which is not supported yet
				// number of hits
				List<Hit> hits;
				try {
					if (hitsCache.containsKey(s.toLowerCase())) {
						hits = hitsCache.get(s.toLowerCase());
					} else {
						hits = searcher.search(null, s, null, true, true,
								false, MAX_RESULTS);
						hitsCache.put(s.toLowerCase(), hits);
					}

				} catch (IndexException e) {
					throw new ExecutionException(
							"Exception occurred while searching in the lucene index",
							e);
				}
				boolean found = false;
				if (hits.size() != 0) {
					for (Hit h : hits) {
						String inst = (String) h.getMap().get("inst");
						if(addedInsts.contains(inst)) continue;
						
						String type = h.getMap().get("type");

						// if we have type information available than use it to
						// filter out lookups
						if (type != null) {
							if (type.equals("Place"))
								type = "Location";

							if (!type.equals(a.getType()))
								continue;
						}

						FeatureMap fm = Factory.newFeatureMap();
						for (String key : h.getMap().keySet()) {
							String val = h.getMap().get(key);
							if (key.equals("type") && val.equals("Place"))
								val = "Location";
							fm.put(key, val);
							fm.put("matched", s);
							fm.put("lookupRule", "fullString");
                                                        fm.put("string",s);
						}

						try {
							addAnnotation(Utils.start(a), Utils.end(a), fm);
							found = true;
							addedInsts.add(inst);
						} catch (InvalidOffsetException e) {
							throw new ExecutionException(
									"Exception occurred when adding a new annotation",
									e);
						}
					}
				}

				AnnotationSet tokensSet = inputAS.getContained(Utils.start(a),
						Utils.end(a)).get("Token");
				List<Annotation> tokensList = Utils.inDocumentOrder(tokensSet);

				// only if no full-string match found
				if (/* !found && */getConsiderMatchingNGrams()) {
					int r = 2;
					if (tokensList.size() < r)
						r = tokensList.size();

					if (tokensList.size() > 0) {
						List<int[]> indices = CombinationGenerator
								.getCombinations(tokensList.size(), r);
						outerLoop: for (int[] subindices : indices) {
							StringBuffer sb = new StringBuffer();
							for (int i = 0; i < subindices.length; i++) {
								Annotation token = tokensList
										.get(subindices[i]);
								// only if token is NNP, NNS, NNPS or NN
								String cat = (String) token.getFeatures().get(
										"category");
								if (cat == null || !cat.startsWith("NN")
										|| cat.length() < 3)
									continue outerLoop;
                                                                String tokenStr = (String) 
                                                                       tokensList.get(subindices[i]).getFeatures().get("string");
                                                                if(tokenStr != null) {
  								  sb.append(tokenStr + " ");
                                                                }
							}

							// search for sb
							try {
								if (hitsCache.containsKey(sb.toString().trim()
										.toLowerCase())) {
									hits = hitsCache.get(sb.toString().trim()
											.toLowerCase());
								} else {
									hits = searcher.search(null, sb.toString()
											.trim(), null, true, true, false,MAX_RESULTS);
									hitsCache.put(sb.toString().trim()
											.toLowerCase(), hits);
								}

							} catch (IndexException e) {
								throw new ExecutionException(
										"Exception occurred while searching in the lucene index",
										e);
							}

							for (Hit h : hits) {
								String inst = (String) h.getMap().get("inst");
								if(addedInsts.contains(inst)) continue;

								String type = h.getMap().get("type");

								// if we have type information available than
								// use it
								// to filter out lookups
								if (type != null) {

									if (type.equals("Place"))
										type = "Location";

									if (!type.equals(a.getType()))
										continue;
								}

								FeatureMap fm = Factory.newFeatureMap();
								for (String key : h.getMap().keySet()) {
									String val = h.getMap().get(key);
									if (key.equals("type")
											&& val.equals("Place"))
										val = "Location";
									fm.put(key, val);
								}

								try {
									found = true;
									fm.put("matched", sb.toString().trim());
									fm.put("lookupRule", "bigram");
                                                                        fm.put("string",s);
									addAnnotation(Utils.start(a), Utils.end(a),
											fm);
									addedInsts.add(inst);
								} catch (InvalidOffsetException e) {
									throw new ExecutionException(
											"Exception occurred when adding a new annotation",
											e);
								}
							}
						}
					}

				}

				// search only single terms
				if (/* !found && */considerSingleTerms) {
					for (Annotation t : tokensList) {
						String cat = (String) t.getFeatures().get("category");
						if (cat == null || !cat.startsWith("NN")
								|| cat.length() < 3)
							continue;

                                                String str = (String) t.getFeatures().get("string");
                                                if(str == null)  continue;

						// skip tokens with small length
						if (str.length() < 3)
							continue;

						// search for sb
						try {
							if (hitsCache.containsKey(str.trim().toLowerCase())) {
								hits = hitsCache.get(str.trim().toLowerCase());
							} else {
								hits = searcher.search(null, str.trim(), null,
										true, true, false, MAX_RESULTS);
								hitsCache.put(str.trim().toLowerCase(), hits);
							}

						} catch (IndexException e) {
							throw new ExecutionException(
									"Exception occurred while searching in the lucene index",
									e);
						}

						for (Hit h : hits) {
							String inst = (String) h.getMap().get("inst");
							if(addedInsts.contains(inst)) continue;

							String type = h.getMap().get("type");

							// if we have type information available than
							// use it
							// to filter out lookups
							if (type != null) {
								if (type.equals("Place"))
									type = "Location";
								if (!type.equals(a.getType()))
									continue;
							}

							FeatureMap fm = Factory.newFeatureMap();
							for (String key : h.getMap().keySet()) {
								String val = h.getMap().get(key);
								if (key.equals("type") && val.equals("Place"))
									val = "Location";
								fm.put(key, val);
							}

							try {
								found = true;
								fm.put("matched", str.trim());
								fm.put("lookupRule", "singleToken");
							        fm.put("string",s);
								addAnnotation(Utils.start(a), Utils.end(a), fm);
								addedInsts.add(inst);
							} catch (InvalidOffsetException e) {
								throw new ExecutionException(
										"Exception occurred when adding a new annotation",
										e);
							}
						}

					}
				}

				// starts with search
				if (/* !found && */considerMatchingWithSimilarStart) {

					// when set to true, the loop is broken
					boolean breakLoop = false;

					for (int i = 0; i < tokensList.size() && !breakLoop; i++) {
						Annotation t = tokensList.get(i);
						String cat = (String) t.getFeatures().get("category");
						if (cat == null || !cat.startsWith("NN")
								|| cat.length() < 3)
							continue;

						// we just want to match the first breakLoop
						breakLoop = true;

                                                String str = (String) t.getFeatures().get("string");
                                                if(str == null)  continue;

						// skip tokens with small length
						if (str.length() < 3)
							continue;

						// search for sb
						try {

							if (hitsCache.containsKey(str.trim().toLowerCase()
									+ "_S")) {
								hits = hitsCache.get(str.trim().toLowerCase()
										+ "_S");
							} else {
								hits = searcher.search(null, str.trim(), null,
										true, false, false, MAX_RESULTS);
								hitsCache.put(str.trim().toLowerCase() + "_S",
										hits);
							}

						} catch (IndexException e) {
							throw new ExecutionException(
									"Exception occurred while searching in the lucene index",
									e);
						}

						for (Hit h : hits) {
							String inst = (String) h.getMap().get("inst");
							if(addedInsts.contains(inst)) continue;

							String type = h.getMap().get("type");
							// if we have type information available than
							// use it
							// to filter out lookups
							if (type != null) {
								if (type.equals("Place"))
									type = "Location";
								if (!type.equals(a.getType()))
									continue;
							}

							FeatureMap fm = Factory.newFeatureMap();
							for (String key : h.getMap().keySet()) {
								String val = h.getMap().get(key);
								if (key.equals("type") && val.equals("Place"))
									val = "Location";
								fm.put(key, val);
							}

							try {
								found = true;
								fm.put("matched", str.trim());
								fm.put("lookupRule", "startsWith");
                                                                fm.put("string",s);
								addAnnotation(Utils.start(a), Utils.end(a), fm);
								addedInsts.add(inst);
							} catch (InvalidOffsetException e) {
								throw new ExecutionException(
										"Exception occurred when adding a new annotation",
										e);
							}
						}

					}
				}
			}
		}
	}

	private void addAnnotation(Long start, Long end, FeatureMap fm)
			throws InvalidOffsetException {
		String inst = (String) fm.get("inst");
		String cls = (String) fm.get("class");
		String matched = (String) fm.get("matched");
		String lookupRule = (String) fm.get("lookupRule");
                String string = (String) fm.get("string");

		FeatureMap temp = Factory.newFeatureMap();
		temp.put("inst", inst);
		if (cls != null)
			temp.put("class", cls);

		AnnotationSet outputAS = document.getAnnotations(outputASName);
		AnnotationSet existingAnnotSet = outputAS.getContained(start, end).get(
				"Lookup", temp);

		if (matched != null) {
			temp.put("matched", matched);
		}

		if (lookupRule != null) {
			temp.put("lookupRule", lookupRule);
		}

		if (existingAnnotSet.size() == 0)
			outputAS.add(start, end, "Lookup", fm);

	}

	public List<String> getAnnotationTypes() {
		return annotationTypes;
	}

	@RunTime
	@CreoleParameter(defaultValue = "Person;Location;Organization")
	public void setAnnotationTypes(List<String> annotationTypes) {
		this.annotationTypes = annotationTypes;
	}

	public String getInputASName() {
		return inputASName;
	}

	@RunTime
	@Optional
	@CreoleParameter
	public void setInputASName(String inputASName) {
		this.inputASName = inputASName;
	}

	public String getOutputASName() {
		return outputASName;
	}

	@RunTime
	@Optional
	@CreoleParameter
	public void setOutputASName(String outputASName) {
		this.outputASName = outputASName;
	}

	public URL getLuceneIndexDirURL() {
		return luceneIndexDirURL;
	}

	@CreoleParameter
	public void setLuceneIndexDirURL(URL luceneIndexDirURL) {
		this.luceneIndexDirURL = luceneIndexDirURL;
	}

	public Boolean getConsiderMatchingNGrams() {
		return considerMatchingNGrams;
	}

	@RunTime
	@Optional
	@CreoleParameter(defaultValue = "false")
	public void setConsiderMatchingNGrams(Boolean considerMatchingNGrams) {
		this.considerMatchingNGrams = considerMatchingNGrams;
	}

	public Boolean getConsiderSingleTerms() {
		return considerSingleTerms;
	}

	@RunTime
	@Optional
	@CreoleParameter(defaultValue = "false")
	public void setConsiderSingleTerms(Boolean considerSingleTerms) {
		this.considerSingleTerms = considerSingleTerms;
	}

	public Boolean getConsiderMatchingWithSimilarStart() {
		return considerMatchingWithSimilarStart;
	}

	@RunTime
	@Optional
	@CreoleParameter(defaultValue = "false")
	public void setConsiderMatchingWithSimilarStart(
			Boolean considerMatchingWithSimilarStart) {
		this.considerMatchingWithSimilarStart = considerMatchingWithSimilarStart;
	}
	// ############### getters and setter ###########

} // class LuceneGazPR
