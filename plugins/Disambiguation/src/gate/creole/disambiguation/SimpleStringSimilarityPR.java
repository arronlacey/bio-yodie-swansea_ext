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
import gate.util.InvalidOffsetException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Simple String Similarity PR")
public class SimpleStringSimilarityPR extends AbstractLanguageAnalyser implements
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

	/**
	 * Wrong case adjustment factor
	 */
	private float wrongCaseAdjustment;

	/**
	 * Name of the output feature
	 */
	private String outputFeature;

	/**
	 * Whether or not to use coreference information if available.
	 */
	private boolean useCoreference;

	/**
	 * Whether or not to include all candidates or just those on
	 * the best instance in a coref chain.
	 */
	//private boolean strict;

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



		DocumentEntitySet ents = new DocumentEntitySet(document, inputASName, 
				annotationTypes, true, "LodieCoref");

		Iterator<Entity> entsit = null;
				
	    if(document.getFeatures().get("keyOverlapsOnly")!=null
	    		&& document.getFeatures().get("keyOverlapsOnly")
	    		.toString().equals("true")) {
	    	entsit = ents.getKeyOverlapsIterator(document);
	    } else {
	    	entsit = ents.getIterator();
	    }

		//For each entity ..
		while(entsit!=null && entsit.hasNext()){
			Entity ent = entsit.next();


			//Get the longest string for this entity

			//LookupList keyspan = ent.getKeyspan();
			LookupList bestspan = ent.getLongestSpan();
			//String entstring = ent.getCleanStringForBestSpan();
			String entstring = bestspan.getCleanString();
			
			if(entstring==null) continue;

			//System.out.println(entstring);

			//Get the case of it

			int caseofent = CommonUtils.caseOfString(entstring);

			//Get the case of the sentence that covers the key string

			String sentencecase = "";
			AnnotationSet sentences = null;
			try {
				sentences = document.getAnnotations(inputASName)
						.getCovering("Sentence", bestspan.startoffset, 
								bestspan.endoffset);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if(sentences!=null && sentences.size()>0){
				Annotation sentence = Utils.getOnlyAnn(sentences);
				sentencecase = (String)sentence.getFeatures().get("case");
			}

			//Evaluate each candidate.

			Iterator<String> candsit = null;

			Set<String> cands = null;
			//if(this.strict){
			//	cands = ent.getInstSet();
			//} else {
				cands = ent.getInstSet();
			//}

			if(cands!=null){
				candsit = cands.iterator();
			}

			while(candsit!=null && candsit.hasNext()){

				if(interrupted) return;

				String cand = candsit.next();

				//System.out.println("Running " + cand.getInst());

				//go through origlabels looking for the best match, adjust for case.

				List<String> labels = ent.getCombinedFeature(cand, "allOrigLabels", "\\|");

				float bestscoresofar = 0.0F;

				Iterator<String> labelsit = null;

				if(labels!=null){
					labelsit = labels.iterator();
				}

				while(labelsit!=null && labelsit.hasNext()){
					String label = labelsit.next();
					float score = CommonUtils.match(entstring, label, true);

					if(sentencecase!=null && 
							(sentencecase.equals("title") || sentencecase.equals("sentence"))){
						//Case information is likely to be useful if context is properly cased.
						//If cand string is all upper case then the label should be too and vice versa
						//since it is probably an acronym.
						int caseoflabel = CommonUtils.caseOfString(label);
						if(caseoflabel==CommonUtils.UPPER && caseofent!=CommonUtils.UPPER){
							score=wrongCaseAdjustment*score;
						} else if(caseofent==CommonUtils.UPPER && caseoflabel!=CommonUtils.UPPER){
							score=wrongCaseAdjustment*score;
						}
					}
					//System.out.println(label + ", " + score);

					if(score>bestscoresofar){
						bestscoresofar = score;
					}
				}

				//Write the score onto all spans for the entity

				ent.putFeatureFloat(cand, outputFeature, bestscoresofar);
			}

		}

		ents = null;
		
		long end = System.currentTimeMillis();
		System.out.println("Simple String Sim:" + ((end - start) / 1000));
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

	public Float getWrongCaseAdjustment() {
		return new Float(wrongCaseAdjustment);
	}

	@CreoleParameter(defaultValue = "0.9F")
	@RunTime
	@Optional
	public void setWrongCaseAdjustment(Float wca) {
		this.wrongCaseAdjustment = wca.floatValue();
	}

	public String getoutputFeature() {
		return this.outputFeature;
	}

	@CreoleParameter(defaultValue = "stringSimilarityBestLabel")
	@RunTime
	@Optional
	public void setoutputFeature(String outputFeature) {
		this.outputFeature = outputFeature;
	}

	/*public Boolean getuseCoreference() {
		return new Boolean(this.useCoreference);
	}

	@CreoleParameter(defaultValue = "true")
	@RunTime
	@Optional
	public void setuseCoreference(Boolean useCoreference) {
		this.useCoreference = useCoreference.booleanValue();
	}*/

	/*public Boolean getstrict() {
		return new Boolean(this.strict);
	}

	@CreoleParameter(defaultValue = "true")
	@RunTime
	@Optional
	public void setstrict(Boolean strict) {
		this.strict = strict.booleanValue();
	}*/

} // class StringSimilarityPR
