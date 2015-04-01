package gate.creole.disambiguation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.util.InvalidOffsetException;

/*
 * The LookupList class keeps track of our LookupList annotations
 * and keeps them up to date with the underlying Lookup annotations.
 * It adds and removes Lookups and LookupList annotations from
 * the document as necessary. It knows what twitter expansion
 * context it is located in.
 */
public class LookupList {
	long startoffset;
	long endoffset;
	
	//In the case of tweet expansions, the context of the span
	//isn't necessarily the whole document.
	long startcontext;
	long endcontext;
	Annotation expansion = null; //And will remain null for LLs not in expansions.
	
	Annotation lookuplistann;
	Document document;
	String inputASName;

	private Map<String, Annotation> annotationbyinst = new HashMap<String, Annotation>();
	
	public LookupList(Annotation lookuplist, Annotation spanann, 
			Document document, String inputASName){		
		//Look for an overlapping twitter expansion type
		long contStart = 0;
		long contEnd = (long)document.getContent().toString().length();
		if(document.getFeatures().get("TwitterExpanderOriginalTextSize")!=null){
			contEnd = (Long)document.getFeatures().get("TwitterExpanderOriginalTextSize");
		}
		Annotation contann = DocumentEntitySet.getTwitterExpansionContextAnnotation(spanann, document);
		if(contann!=null){
			contStart = contann.getStartNode().getOffset();
			contEnd = contann.getEndNode().getOffset();
			this.expansion = contann;
		}
		
		this.startoffset = spanann.getStartNode().getOffset();
		this.endoffset = spanann.getEndNode().getOffset();
		this.startcontext = contStart;
		this.endcontext = contEnd;
		this.lookuplistann = lookuplist;
		this.document = document;
		this.inputASName = inputASName;
		
		//If we have a lookup list annotation, link all the annotations
		//it refers to.
		if(lookuplist!=null){
			List<Integer> ids = (List<Integer>)lookuplist.getFeatures().get("ids");
			for(int i=0;i<ids.size();i++){
				Annotation ann = document.getAnnotations().get(ids.get(i));
				this.annotationbyinst.put(ann.getFeatures().get("inst").toString(), ann);
			}
		} else { //Otherwise make an empty new one.
			AnnotationSet inputAS = document.getAnnotations(inputASName);
			FeatureMap fm = gate.Factory.newFeatureMap();
			List<Integer> ids = new ArrayList<Integer>();
			fm.put("ids", ids);

			try {
				Integer newid = inputAS.add(this.startoffset, this.endoffset, "LookupList", fm);
				Annotation newll = inputAS.get(newid);
				this.lookuplistann = newll;
			} catch (InvalidOffsetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * Merge one LL into another. We need to respan the new ones
	 * and keep the LL annotation up to date.
	 */
	public void subsume(LookupList ll){
		Annotation llann = ll.getLookupListAnn();
		if(llann==null){
			//We have no lookup list on this span, therefore no
			//lookups so there's nothing to do here.
		} else {
			AnnotationSet inputAS = document.getAnnotations(inputASName);
			List<Integer> annstoadd = (List<Integer>)llann.getFeatures().get("ids");
			for(int i=0;i<annstoadd.size();i++){
				Integer id = annstoadd.get(i);
				Annotation toadd = inputAS.get(id);
				if(this.matches(toadd)){ //Same span.
					this.addAnn(id);
				} else { //Make one in the right location.
					FeatureMap fm = Factory.newFeatureMap();
					fm.putAll(toadd.getFeatures());
					try {
						Integer newid = inputAS.add(this.startoffset, this.endoffset, "Lookup", fm);
						this.addAnn(newid);
					} catch (InvalidOffsetException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	public void addAnn(Integer item){
		AnnotationSet as = document.getAnnotations(inputASName);
		Annotation ann = as.get(item);
		List<Integer> ids = (List<Integer>)this.lookuplistann.getFeatures().get("ids");
		
		if(!ids.contains(item)){
			ids.add(item);
			this.lookuplistann.getFeatures().put("ids", ids);
			
			//If we are merging, we might get an ann with the wrong span.
			//In this case, make a version that has the right span and add that.
			if((ann.getStartNode().getOffset()!=this.startoffset)
					|| (ann.getEndNode().getOffset()!=this.endoffset)){
				FeatureMap fm = gate.Factory.newFeatureMap();
				fm.putAll(ann.getFeatures());
				try {
					Integer id = as.add(this.startoffset, this.endoffset, "Lookup", fm);
					ann = as.get(id);
				} catch (InvalidOffsetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				as.remove(ann);
			}
	
			//Add it to the structure.
                        // TODO: parameter instead of "inst"?
                        // !!TODO: this only keeps the last inst, may overwrite if there are more than 1
                        // candidate with the same inst (which will be the case if we merge non-coext lokuplists)
			this.annotationbyinst.put((String)ann.getFeatures().get("inst"), ann);
		}
	}
	
	public void removeAllAnns(){
		Set<String> annstoremove = (Set<String>)this.annotationbyinst.keySet();
		 //Make copy to avoid concurrent modification exception with removeAnn.
		Set<String> copyofannstoremove = new HashSet<String>();
		copyofannstoremove.addAll(annstoremove);
		Iterator<String> it = copyofannstoremove.iterator();
		while(it.hasNext()){
			String inst = it.next();
			this.removeAnn(inst);
		}
		
		//Remove ll from document
		AnnotationSet inputAS = document.getAnnotations(inputASName);
		inputAS.remove(this.lookuplistann);
	}
	
	public void removeAnn(String inst){
		Annotation anntoremove = this.getAnnotationbyinst(inst);
		if(anntoremove!=null){ //Will be null if this one isn't on this span.
			if(this.lookuplistann!=null){
				List<Integer> ids = (List<Integer>)this.lookuplistann.getFeatures().get("ids");
				ids.remove(anntoremove.getId());
				this.lookuplistann.getFeatures().put("ids", ids);
				this.annotationbyinst.remove(inst);
			} else {
				System.out.println("Attempt to remove Lookup from location with "
						+ "no LookupList!");
			}
			//Remove from document
			document.getAnnotations(inputASName).remove(anntoremove);
		}
	}
	
	public void flagAnn(String inst){
		Annotation anntoflag = this.getAnnotationbyinst(inst);
		if(anntoflag!=null){ //Will be null if this one isn't on this span.
			anntoflag.getFeatures().put("isOnKeySpan", "false");
		}
	}
	
	public boolean overlaps(Annotation ann){
		long start = ann.getStartNode().getOffset();
		long end = ann.getEndNode().getOffset();
		if(start>=startoffset && start<endoffset){
			return true;
		} else if(end>startoffset && end<=endoffset){
			return true;
		} else if(start<startoffset && end>endoffset){
			return true;
		}
		return false;
	}

	public boolean overlaps(LookupList ll){
		if(ll.startoffset>=this.startoffset && ll.startoffset<this.endoffset){
			return true;
		} else if(ll.endoffset>this.startoffset && ll.endoffset<=this.endoffset){
			return true;
		} else if(ll.startoffset<this.startoffset && ll.endoffset>this.endoffset){
			return true;
		}
		return false;
	}

	public boolean matches(Annotation ann){
		long start = ann.getStartNode().getOffset();
		long end = ann.getEndNode().getOffset();
		if(start==startoffset && end==endoffset){
			return true;
		}
		return false;
	}
	
	public boolean isContained(Long start, Long end){
		if(startoffset>=start && endoffset<=end){
			return true;
		}
		return false;
	}

	public String getCleanString(){
		return gate.Utils.cleanStringFor(document, 
				this.startoffset, this.endoffset);
	}
	
	public String getKey(){
		long start = this.startoffset;
		long end = this.endoffset;
		return start + "-" + end;
	}
	
	public long getLength(){
		return endoffset - startoffset;
	}
	
	public long getStartoffset() {
		return startoffset;
	}

	public void setStartoffset(long startoffset) {
		this.startoffset = startoffset;
	}

	public long getEndoffset() {
		return endoffset;
	}

	public void setEndoffset(long endoffset) {
		this.endoffset = endoffset;
	}

	public Annotation getLookupListAnn() {
		return lookuplistann;
	}

	public void setLookupListAnn(Annotation lookuplist) {
		this.lookuplistann = lookuplist;
	}

	public Annotation getAnnotationbyinst(String inst) {
		return annotationbyinst.get(inst);
	}

	public Map<String, Annotation> getAnnotationsbyinst() {
		return annotationbyinst;
	}

}
