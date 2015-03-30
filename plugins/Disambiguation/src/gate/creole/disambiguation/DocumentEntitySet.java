package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.FeatureMap;
import gate.Utils;
import gate.util.InvalidOffsetException;
import gate.util.compilers.eclipse.jdt.internal.compiler.ast.ThisReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import com.thoughtworks.xstream.core.util.Pool.Factory;

public class DocumentEntitySet {

	private String inputASName = "";

	private List<String> annotationTypes = null;

	private List<Entity> entities = null;

	private List<Entity> keyOverlapEntities = null;

	private List<Entity> tweetSpanEntities = null;

	//Should we use coreference information to group entities
	//or just make a separate entity for each span?
	private boolean useCoreference = true;
	
	private String corefDocFeatName;

	Document document;

        /**
         * Create DocumentEntitySet instance.
         * 
         * @param document
         * @param iasn
         * @param atypes
         * @param useCoref
         * @param corefDocFeatName 
         */
	public DocumentEntitySet(Document document, String iasn, 
			List<String> atypes, boolean useCoref, String corefDocFeatName) {
		this.inputASName = iasn;
                // REMOVE
		this.annotationTypes = atypes;
		this.useCoreference = useCoref;
		this.document = document;
		this.corefDocFeatName = corefDocFeatName;

		this.populate(document);
	}

        // TODO: maybe return List<Entity> or something  
        // construct in document order
	public Iterator<Entity> getIterator(){
		if(this.entities!=null){
			return this.entities.iterator();
		} else {
			return null;
		}
	}

	//Return an iterator for entities that overlap with key mentions only.
	public Iterator<Entity> getKeyOverlapsIterator(Document document){
		AnnotationSet keyMentionsSet = document.getAnnotations("Key").get("Mention");
		keyOverlapEntities = new ArrayList<Entity>();
		Iterator<Entity> entit = this.getIterator();
		while(entit!=null && entit.hasNext()){
			Entity thisent = entit.next();
			if(thisent.overlapsAny(keyMentionsSet)){
				keyOverlapEntities.add(thisent);
			}
		}

		return keyOverlapEntities.iterator();
	}

	public Iterator<Entity> getTweetSpanIterator(Document document){
		if(document.getFeatures().get("TwitterExpanderOriginalTextSize")!=null){
			long endDoc = (Long)document.getFeatures().get("TwitterExpanderOriginalTextSize");
			tweetSpanEntities = new ArrayList<Entity>();
			Iterator<Entity> entit = this.getIterator();
			while(entit!=null && entit.hasNext()){
				Entity thisent = entit.next();
				if(thisent.featuresWithin(0L, endDoc)){
					tweetSpanEntities.add(thisent);
				}
			}
			return tweetSpanEntities.iterator();
		} else {
			return this.entities.iterator(); //Not an expanded tweet
		}
	}

	private void populate(Document document){

		Map<String, List<List<Integer>>> corefs = null;


		if(this.useCoreference==true){
			try {
				corefs = 
						(Map<String, List<List<Integer>>>)document.getFeatures().get(corefDocFeatName);
			} catch(ClassCastException e){
				e.printStackTrace();
			}
			
			if(corefs!=null){
				if(inputASName!=null && inputASName.equals("")){
					inputASName = null;
				}
				List<List<Integer>> entchains = (List<List<Integer>>)corefs.get(inputASName);
	
				if(entchains!=null){
					entities = populateCoreffedEntities(document, entchains, inputASName);
				}
			}
		}

		this.makeEntitiesNotCoreffed(document);
	}

	private void makeEntitiesNotCoreffed(Document document){
		if(entities==null){
			entities = new ArrayList<Entity>();
		}

		AnnotationSet lls = document.getAnnotations(inputASName).get("LookupList");
		
		Iterator<Annotation> llsit = lls.iterator();
                 
                // TODO: potential nondet because iterator goes over set elements
                // maybe: inDocumentOrder + something for same offset (does inDocumentOrder break ties properly?)
		while(llsit.hasNext()){
			Annotation ll = (Annotation)llsit.next();

			Iterator<Entity> entit = entities.iterator();
			Entity matchent = null;
			while(entit!=null && entit.hasNext()){
				Entity ent = (Entity)entit.next();
				if(ent.exactSpanMatch(ll)){
					//The annotation belongs to this entity
					//so therefore we have it already and can
					//skip it.
					matchent = ent;
				}
			}
			
			if(matchent==null){ //Will make new entity based on this list
				LookupList span = new LookupList(ll, ll, document, inputASName);
				List<LookupList> lll = new ArrayList<LookupList>();
				lll.add(span);
				Entity newEnt = new Entity(lll, document, inputASName);
				entities.add(newEnt);
			}
		}
	}

	private List<Entity> populateCoreffedEntities(Document doc, List<List<Integer>> entchains, 
			String iasn){
		List<Entity> entities = new ArrayList<Entity>();
		AnnotationSet as = doc.getAnnotations(iasn);
		Iterator<List<Integer>> entsit = entchains.iterator();
		
		while(entsit!=null && entsit.hasNext()){
			List<Integer> group = (List<Integer>)entsit.next();

			List<LookupList> lll = new ArrayList<LookupList>();
			int numberofllsfound = 0;
			Iterator<Integer> cgit = group.iterator();
			while(cgit!=null && cgit.hasNext()){ //For each span in the coref group
				
				Integer id = (Integer)cgit.next();
				
				//don't care what it is, just want the span
				Annotation corefann = as.get(id);
				if(corefann!=null && corefann.getStartNode()!=null
						&& corefann.getEndNode()!=null){
					
                                        // JP: parametrize ann type for the lists
                                        // TODO: use Utils.getCoextensive
					AnnotationSet llcands = 
							as.get("LookupList").get(corefann.getStartNode().getOffset());
					Annotation ll = null;
					Iterator<Annotation> llcandsit = llcands.iterator();
					while(llcandsit.hasNext()){
						Annotation thisll = llcandsit.next();
						if(corefann.coextensive(thisll)){
							ll = thisll;
						}
                                                // NOTE: always should only have one coext here!
					}
					if(ll!=null) numberofllsfound++;
					//Add it even if it is null--we might populate it later
					LookupList span = new LookupList(ll, corefann, doc, inputASName);
					lll.add(span);
				}
			}
			if(numberofllsfound>0){ //Got candidates, so worth adding
				Entity newEnt = new Entity(lll, doc, inputASName);
				entities.add(newEnt);
			}
		}
		return entities;
	}

	public SortedMap<Long, Entity> getProximalEntities(Entity ent, int charRange, 
			Boolean useTwitterExpansion){
		SortedMap<Long, Entity> proximalEntities = new TreeMap<Long, Entity>();

		Iterator<Entity> entit = this.entities.iterator();

		while(entit!=null && entit.hasNext()){
			Entity testent = entit.next();

			if(!testent.equals(ent)){

				long distancebetween = testent.shortestDistanceFrom(ent, useTwitterExpansion);

				if(distancebetween<=charRange){
					proximalEntities.put(Long.valueOf(distancebetween), testent);
				}	
			}
		}

		return proximalEntities;
	}

	public static Annotation getTwitterExpansionContextAnnotation(
			Annotation corefann, Document document){
		AnnotationSet contas1 = Utils.getCoveringAnnotations(
				document.getAnnotations().get("TwitterExpanderHashtag"), 
				corefann);
		AnnotationSet contas2 = Utils.getCoveringAnnotations(
				document.getAnnotations().get("TwitterExpanderURL"), 
				corefann);
		AnnotationSet contas3 = Utils.getCoveringAnnotations(
				document.getAnnotations().get("TwitterExpanderUserID"), 
				corefann);
		if(contas1.size()==1){
			return Utils.getOnlyAnn(contas1);
		} else if(contas2.size()==1){
			return Utils.getOnlyAnn(contas2);
		} else if(contas3.size()==1){
			return Utils.getOnlyAnn(contas3);
		}

		return null;
	}

        // remove the LookupList instance with fewer candidates and if it was the last one in
        // the Entity instance, remove that as well.
	public void choose(Entity ent1, Entity ent2){
		List<LookupList> spans1 = ent1.getSpans();
		List<LookupList> spans2 = ent2.getSpans();
		
		class SpanPair{
			LookupList ll1;
			LookupList ll2;
			
			SpanPair(LookupList ll1, LookupList ll2){
				this.ll1 = ll1;
				this.ll2 = ll2;
			}
		}
		
		List<SpanPair> spanstoresolve = new ArrayList<SpanPair>();
				
		//Find the clashes
		for(int i=0;i<spans1.size();i++){
			LookupList ll1 = spans1.get(i);
			for(int j=0;j<spans2.size();j++){
				LookupList ll2 = spans2.get(j);
				if(ll1.overlaps(ll2)){
					spanstoresolve.add(new SpanPair(ll1, ll2));
				}
			}
		}
		
		for(int i=0;i<spanstoresolve.size();i++){
			SpanPair toresolve = spanstoresolve.get(i);
			if(toresolve.ll1.getAnnotationsbyinst().keySet().size()
					> toresolve.ll2.getAnnotationsbyinst().keySet().size()){
				ent2.remove(toresolve.ll2);
				if(ent2.getSpans().size()<1){
					this.entities.remove(ent2);
				}
			} else {
				ent1.remove(toresolve.ll1);
				if(ent1.getSpans().size()<1){
					this.entities.remove(ent1);
				}
			}
		}
	}
	
	public void print(){
		System.out.println("Printing document entity set:");
		List<Entity> entlist = this.getEntities();
		for(int i=0;i<entlist.size();i++){
			Entity ent = entlist.get(i);
			System.out.print("  Entity " + i + ", ");
			ent.print();
		}
	}
	
	public boolean getUseCoreference() {
		return useCoreference;
	}

	public void setUseCoreference(boolean useCoreference) {
		this.useCoreference = useCoreference;
	}

	public String getInputASName() {
		return inputASName;
	}

	public void setInputASName(String inputASName) {
		this.inputASName = inputASName;
	}

	public List<String> getAnnotationTypes() {
		return annotationTypes;
	}

	public void setAnnotationTypes(List<String> annotationTypes) {
		this.annotationTypes = annotationTypes;
	}

	public List<Entity> getEntities() {
		return entities;
	}

	public void setEntities(List<Entity> entities) {
		this.entities = entities;
	}

	public List<Entity> getKeySpanEntities() {
		return keyOverlapEntities;
	}

	public void setKeySpanEntities(List<Entity> keySpanEntities) {
		this.keyOverlapEntities = keySpanEntities;
	}

	public List<Entity> getTweetSpanEntities() {
		return tweetSpanEntities;
	}

	public void setTweetSpanEntities(List<Entity> tweetSpanEntities) {
		this.tweetSpanEntities = tweetSpanEntities;
	}

}
