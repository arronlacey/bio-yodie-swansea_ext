package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.FeatureMap;
import gate.ProcessingResource;
import gate.Resource;
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
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * This PR attempts to make better use of ANNIE coref information in the
 * following ways:
 * - where we have e.g. three lookup lists of different qualities, and
 *   ANNIE says they are all the same thing, we replace the poorer
 *   quality lookup lists with the better one
 * - where we have e.g. one lookup list and ANNIE says this is the same
 *   thing as another location where we have nothing, we can make
 *   a lookup list on the previously undetected thing.
 * Having done this, we need to also merge or remove any overlapping
 * entities.
 */
@CreoleResource(name = "Coref Rationalization PR")
public class CorefRationalizationPR extends AbstractLanguageAnalyser implements
ProcessingResource {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4836781098219171384L;

	/**
	 * name of the input annotation set
	 */
	private String inputASName;

	/**
	 * name of the document feature containing the ANNIE coref
	 */
	private String matchesAnnotsType;

	/**
	 * Whether or not to use coref
	 */
	private Boolean useCoref;

	public enum FlagOrDelete{FLAG, DELETE}

	/*
	 * Whether to remove annotations that don't make the cut or just
	 * set a variable.
	 */
	private FlagOrDelete flagOrDelete;

	/*
	 * We are going to normalize all spans in a coref chain to the same
	 * candidate list. (If we don't want to do this, then the easiest way
	 * to achieve it would be to just not use coref at all--there wouldn't
	 * be any point to it.) So given that we are going to do this, how do
	 * we select the candidate set to use? We choose the n best spans
	 * (currently best is defined as containing the most separate words)
	 * and pool their candidates. This parameter indicates n.
	 */
	private Integer candNormalizationSpanSetSize;

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
		long start = System.currentTimeMillis(); // record the start time

		if(useCoref){
			DocumentEntitySet ents = new DocumentEntitySet(document, inputASName,
					true, this.matchesAnnotsType);

			//System.out.println("BEFORE DOING ANYTHING");
			//ents.print();
			
			//Coref may create a span that overlaps with one of our NEs. In that
			//case, so far these are treated separately. In fact they should
			//probably be merged. If not merged, then the overlap should be
			//removed.
			resolveOverlaps(ents);

			//System.out.println("AFTER RESOLVING OVERLAPS");
			//ents.print();
			
			//In coref cases, we can rationalize the candidate lists, so all
			//spans have the shortest and best candidate list--taken from the
			//longest, "key" span. This also takes care of cases where
			//a coref span didn't even have a lookup list on it at all, so
			//adding an NE where we didn't have one, which is likely to be good.
			rationalizeCandidates(document, inputASName, ents);

			//System.out.println("AFTER RATIONALIZING CANDIDATES");
			//ents.print();
			
			//Having rationalized the candidates, the document feature
			//describing the coref structure may be wrong. We'll make a special
			//version of our own and put it on the document. This will also
			//help PRs to know if this PR has been run or not. Using coref
			//won't really work out if this PR hasn't been run.
			document.getFeatures().put(Constants.yodieCorefType, this.getLatestCorefStructure(ents));
		} else {
			//If we don't want to use coref, we still need to create the
			//"LodieCoref" document feature, because that is what the other
			//PRs will use. It will contain a single item for each LL.
			DocumentEntitySet ents = new DocumentEntitySet(document, inputASName,
					false, "");

			document.getFeatures().put(Constants.yodieCorefType, this.getLatestCorefStructure(ents));
		}

		long end = System.currentTimeMillis();
		System.out.println("Coref Rationalization PR:" + (end - start));
	}

	private void rationalizeCandidates(Document document, String inputASName, 
			DocumentEntitySet ents){
		AnnotationSet inputAS = document.getAnnotations(inputASName);

		Iterator<Entity> it = ents.getIterator();
		while(it.hasNext()){
			Entity thisent = it.next();
			candidateListToKeySpan(thisent, inputAS);
		}
	}

        // Find the n (parameter) "best" mentions (LookupList) (based on most Tokens in the mention)
        // and project the combined candidates of those to all other members of the chain.        
	private void candidateListToKeySpan(Entity thisent, AnnotationSet inputAS){	
		List<LookupList> betterspans = 
				thisent.getBestSpans(this.candNormalizationSpanSetSize);
                // maybe also use LodieUtils for merging, adding new candidates.
                
		Map<LookupList.KeyPair, LookupList> betterllsbyinstlabel = new HashMap<LookupList.KeyPair, LookupList>();
		for(int i=0;i<betterspans.size();i++){
			LookupList ll = betterspans.get(i);
			Set<LookupList.KeyPair> instlabels = ll.getAnnotationsbyinstlabel().keySet();
			Iterator<LookupList.KeyPair> it = instlabels.iterator();
			while(it.hasNext()){
				LookupList.KeyPair instlabel = it.next();
				if(betterllsbyinstlabel.get(instlabel)==null){
					betterllsbyinstlabel.put(instlabel, ll);
				}
			}
		}
		
		Set<LookupList.KeyPair> instlabelsofbetterlls = betterllsbyinstlabel.keySet();
		
		//Make a set of insts to remove from everything
		Set<LookupList.KeyPair> candstoconsiderremovingfromthis = thisent.getInstLabelSet();
		Set<LookupList.KeyPair> candstoremovefromthis = new HashSet<LookupList.KeyPair>();
		candstoremovefromthis.addAll(candstoconsiderremovingfromthis);
		candstoremovefromthis.removeAll(instlabelsofbetterlls);

		//Work through spans one at a time removing the offending candidates
		//and adding candidates to match union of better spans where necessary.
		List<LookupList> spans = thisent.getSpans();

		Iterator<LookupList> spanit = spans.iterator();
		while(spanit.hasNext()){
			LookupList sp = spanit.next();	
			
			//Removing.
			
			Iterator<LookupList.KeyPair> ctrftit1 = candstoremovefromthis.iterator();
			while(ctrftit1.hasNext()){
				LookupList.KeyPair ctr = ctrftit1.next();
				if(this.flagOrDelete==FlagOrDelete.FLAG){
					sp.flagAnn(ctr);
				} else {
					sp.removeAnn(ctr); //Remove from ll list
				}
			}

			//Add any cands required to make this similar to the
			//key span candidate list

			Set<LookupList.KeyPair> candstoadd = new HashSet<LookupList.KeyPair>();
			candstoadd.addAll(instlabelsofbetterlls);
			candstoadd.removeAll(sp.getAnnotationsbyinstlabel().keySet()); //Already there

			Iterator<LookupList.KeyPair> ctait = candstoadd.iterator();
			while(ctait.hasNext()){
				LookupList.KeyPair cta = ctait.next();
				//Need to make a new annotation.
				//It's a bit unsatisfactory, since there could be features that
				//don't transfer meaningfully to a different span, but nonetheless 
				//we'll just copy all the features from the example on the better span.
				LookupList betterspan = betterllsbyinstlabel.get(cta);
				Annotation theoneonthekeyspan = betterspan.getAnnotationbyinstlabel(cta);
				FeatureMap fm = gate.Factory.newFeatureMap();
				fm.putAll(theoneonthekeyspan.getFeatures());
				Integer id;
				try {
					id = inputAS.add(sp.startoffset, sp.endoffset, 
							Constants.lookupType, fm);
					sp.addAnn(id);
				} catch (InvalidOffsetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		thisent.doesinstlistneedrecalculating = true;
	}

        // Merge entities which overlap and have at least one inst in common; if they overlap
        // without a common inst, remove the one with fewer candidates.
	private DocumentEntitySet resolveOverlaps(DocumentEntitySet ents){
		//Need to record all our merges otherwise it's going to be chaos.
		Map<Entity, Entity> mergehistory = new HashMap<Entity, Entity>();

                // TODO: maybe use iterator.remove() to avoid concurrent modif except. instead of for ..?
		List<Entity> entslist = ents.getEntities();
		for(int i=0;i<entslist.size();i++){
			Entity thisent1 = entslist.get(i);
			for(int j=i+1;j<entslist.size();j++){
				Entity thisent2 = entslist.get(j);
				if(thisent1.overlaps(thisent2)){ //Need to resolve
					//Switch the below to keyspan only??
					Set<String> insts1 = thisent1.getInstSet();
					Set<String> insts2 = thisent2.getInstSet();

					int noIntersectionTotal = insts1.size() + insts2.size();
					Set<String> mergeset = new HashSet<String>();
					mergeset.addAll(insts1);
					mergeset.addAll(insts2);
					int testtotal = mergeset.size();

					/*
					 * If we have overlapping candidate lists, we'll merge the
					 * entities. If not, we'll resolve the overlap by removing
					 * one of the spans.
					 */
					if(testtotal<noIntersectionTotal){ //Merge
						if(mergehistory.containsKey(thisent1)
								&& !mergehistory.containsKey(thisent2)){
							Entity head = mergehistory.get(thisent1);
							head.subsume(thisent2);
							mergehistory.put(thisent2, head);
						} else if(mergehistory.containsKey(thisent2)
								&& !mergehistory.containsKey(thisent1)){
							Entity head = mergehistory.get(thisent2);
							head.subsume(thisent1);
							mergehistory.put(thisent1, head);
						} else {
							//New merge--thisent1 becomes the head of thisent2
							thisent1.subsume(thisent2);
							mergehistory.put(thisent2, thisent1);
						}
					} else { //Choose
						ents.choose(thisent1, thisent2);		
					}
				}
			}
		}

		//Remove all the entities that have been subsumed by others.
		Iterator<Entity> mergehistoryit = mergehistory.keySet().iterator();
		while(mergehistoryit.hasNext()){
			Entity subordinate = mergehistoryit.next();
			subordinate.removeAllAnns();
			ents.getEntities().remove(subordinate);
		}

		return ents;
	}

	private Map<String, List<List<Integer>>> getLatestCorefStructure(DocumentEntitySet des){
		List<Entity> ents = des.getEntities();
		List<List<Integer>> newcorefstructure = new ArrayList<List<Integer>>();
		for(int i=0;i<ents.size();i++){
			List<LookupList> lls = ents.get(i).getSpans();
			List<Integer> spanlist = new ArrayList<Integer>();
			for(int j=0;j<lls.size();j++){
				LookupList sp = lls.get(j);
				spanlist.add(sp.lookuplistann.getId());
			}
			newcorefstructure.add(spanlist);
		}
		Map<String, List<List<Integer>>> toreturn = new HashMap<String, List<List<Integer>>>();
		toreturn.put(null, newcorefstructure);
		return toreturn;
	}

	/**
	 * The cleanup method
	 */
	@Override
	public void cleanup() {
		super.cleanup();
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

	public String getMatchesAnnotsType() {
		return this.matchesAnnotsType;
	}

	@CreoleParameter(defaultValue = "MatchesAnnots")
	public void setMatchesAnnotsType(String matchesAnnotsType) {
		this.matchesAnnotsType = matchesAnnotsType;
	}

	public Integer getCandNormalizationSpanSetSize() {
		return candNormalizationSpanSetSize;
	}

	@RunTime
	@CreoleParameter(defaultValue = "3")
	public void setCandNormalizationSpanSetSize(Integer candNormalizationSpanSetSize) {
		this.candNormalizationSpanSetSize = candNormalizationSpanSetSize;
	}

	public Boolean getUseCoref() {
		return useCoref;
	}

	@RunTime
	@CreoleParameter(defaultValue = "true")
	public void setUseCoref(Boolean useCoref) {
		this.useCoref = useCoref;
	}

	public FlagOrDelete getFlagOrDelete() {
		return flagOrDelete;
	}

	@RunTime
	@CreoleParameter(defaultValue = "DELETE")
	public void setFlagOrDelete(FlagOrDelete flagOrDelete) {
		this.flagOrDelete = flagOrDelete;
	}
} // class CorefRationalizationPR
