package gate.creole.disambiguation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Set;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Utils;
import gate.util.Benchmark;

public class Entity {
	
	//private Map<String, List<Annotation>> annotationsbyinst = new HashMap<String, List<Annotation>>();
	
	private Set<String> instlist = new HashSet<String>();
	
	boolean doesinstlistneedrecalculating = false;
	
	private List<LookupList> spans = new ArrayList<LookupList>();
	
	//private LookupList keyspan = null;
	
	private Document document;
	
	private String inputASName;
	
	public Entity(List<LookupList> spans, Document document, String inputASName) {
		this.document = document;
		this.inputASName = inputASName;
                for(LookupList span : spans) {
                  addList(span);
                }
		//this.selectKeySpan();
	}
	
        /**
         * Add a single LookupList instance to this Entity.
         * 
         * If the LookupList instance overlaps with one of the spans already in this Entity,
         * then the span gets merged into the existing span. 
         * <p>
         * TODO: at the moment this only merges into the last span that overlaps in the list
         * of spans. This should get modified so that we make sure we merge into all overlapping
         * spans.
         * 
         * @param span 
         */
	private void addList(LookupList span){
		LookupList mergespan = null;
		
		Iterator<LookupList> testspanit = spans.iterator();
		while(testspanit.hasNext()){
			LookupList testspan = testspanit.next();
			if(testspan.overlaps(span)){
                          // TODO: this may get executed more than once so we may miss some for merging?
				mergespan = testspan;
			}
		}
		
		//Is there a clash? If so, merge.
		if(mergespan!=null){
			mergespan.subsume(span);
		} else { //Else just add it, no problem.
			this.spans.add(span);
		}

		doesinstlistneedrecalculating = true;
	}
	
        // NOTE: this will probably only work if the ll has previously retrieved from this 
        // Entity (not content-equals). 
	public void remove(LookupList ll){
		ll.removeAllAnns();
		this.spans.remove(ll);
		doesinstlistneedrecalculating = true;
	}
	
        /**
         * Add all spans from the other Entity to this Entity.
         * 
         * 
         * @param ent 
         */
	public void subsume(Entity ent){
                for(LookupList span : ent.spans) {
                  addList(span);
                }
		doesinstlistneedrecalculating = true;
	}
	
	public void removeAllAnns(){
          for(LookupList span : spans){
            span.removeAllAnns();
          }
	  doesinstlistneedrecalculating = true;
	}
	
	//For every example of this candidate, write the float value into the feature
	public void putFeatureFloat(String inst, String feature, float toPut){
		List<Annotation> examples = this.getAnnsByInst(inst);
		Iterator<Annotation> annit = examples.iterator();

		while(annit.hasNext()){
			Annotation ann = annit.next();
			ann.getFeatures().put(feature, Float.valueOf(toPut));
		}
	}

	public List<String> getCombinedFeature(
			String inst, String feature, String separator){
		List<Annotation> examples = this.getAnnsByInst(inst);
		Iterator<Annotation> annit = examples.iterator();
		List<String> strings = new ArrayList<String>();

		while(annit.hasNext()){
			Annotation ann = annit.next();
			String featurecontent = ann.getFeatures().get(feature).toString();
			String[] splitstring = featurecontent.split(separator);
			for(int i=0;i<splitstring.length;i++){
				strings.add(splitstring[i]);
			}
		}
		
		return strings;
	}
	
	public boolean exactSpanMatch(Annotation ann){
		Iterator<LookupList> spanit = spans.iterator();
		while(spanit.hasNext()){
			LookupList span = spanit.next();
			if(span.startoffset==ann.getStartNode().getOffset() 
					&& span.endoffset==ann.getEndNode().getOffset()){
				return true;
			}
		}
		return false;
	}

	public boolean overlaps(Entity testent){
		Iterator<LookupList> spanit = spans.iterator();
		while(spanit.hasNext()){
			LookupList thisspan = spanit.next();
			
			Iterator<LookupList> testspanit = testent.spans.iterator();
			while(testspanit.hasNext()){
				LookupList testspan = testspanit.next();
				if(thisspan.startoffset<=testspan.startoffset
						&& thisspan.endoffset>=testspan.startoffset){
					return true;
				} else if(thisspan.startoffset<=testspan.endoffset
						&& thisspan.endoffset>=testspan.startoffset){
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean overlapsAny(AnnotationSet as){
		Iterator<Annotation> asit = as.iterator();
		while(asit.hasNext()){
			Annotation ann = asit.next();
			Iterator<LookupList> spanit = spans.iterator();
			while(spanit.hasNext()){
				LookupList span = spanit.next();
				if(span.overlaps(ann)){
					return true;
				}
			}
		}
		return false;
	}

	public boolean featuresWithin(Long start, Long end){
		Iterator<LookupList> spanit = spans.iterator();
		while(spanit.hasNext()){
			LookupList span = spanit.next();
			if(span.isContained(start, end)){
				return true;
			}
		}
		return false;
	}
	
	/*public boolean isKeySpan(Annotation ann){
		if(this.getKeyspan().startoffset==ann.getStartNode().getOffset() 
				&& this.getKeyspan().endoffset==ann.getEndNode().getOffset()){
			return true;
		} else {
			return false;
		}
	}*/

	public LookupList getLongestSpan(){
		LookupList longestspan = null;
		Iterator<LookupList> spanit = spans.iterator();
		while(spanit.hasNext()){
			LookupList span = spanit.next();
			if(longestspan==null || span.getLength()>longestspan.getLength()){
				longestspan = span;
			}
		}
		return longestspan;
	}

        // find span with most characters
	public static LookupList getLongestSpan(List<LookupList> spans){
		LookupList longestspan = null;
		Iterator<LookupList> spanit = spans.iterator();
		while(spanit.hasNext()){
			LookupList span = spanit.next();
			if(longestspan==null || span.getLength()>longestspan.getLength()){
				longestspan = span;
			}
		}
		return longestspan;
	}
	
	public LookupList getSpanWithMostWords(){
		LookupList longestspan = null;
		int mostwords = 0;
		Iterator<LookupList> spanit = spans.iterator();
		while(spanit.hasNext()){
			LookupList span = spanit.next();
			String thistext = null;
			try {
				thistext = document.getContent().getContent(
						span.startoffset, span.endoffset).toString();
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(thistext!=null){
				thistext.replaceAll("[\\s\\p{Punct}]+", " ").trim();
				String[] words = thistext.split(" ");
				if(longestspan==null || words.length>mostwords){
					longestspan = span;
					mostwords = words.length;
				}
			}
		}
		return longestspan;
	}
	
        // NOTE: maybe allow other criteria for what are the n best spans (currently: find
        // the longest spans (most characters).
	public List<LookupList> getBestSpans(int n){
		List<LookupList> tempspans = new ArrayList<LookupList>();
		tempspans.addAll(this.spans);
		List<LookupList> toreturn = new ArrayList<LookupList>();
		int numbertoaddifposs = n;
		while(tempspans.size()>0 && numbertoaddifposs>0){
			LookupList longest = Entity.getLongestSpan(tempspans);
			toreturn.add(longest);
			tempspans.remove(longest);
			numbertoaddifposs--;
		}
		return toreturn;
	}
	
	public long shortestDistanceFrom(Entity testent, Boolean useTwitterExpansion){
                long startTime = Benchmark.startPoint();
		Iterator<LookupList> testspanit = testent.spans.iterator();
		Iterator<LookupList> thisspanit = this.spans.iterator();
		long shortestdistance = 1000000;
		
		while(testspanit.hasNext()){
			LookupList testspan = testspanit.next();
			
			while(thisspanit.hasNext()){ //For each combination of spans
				LookupList thisspan = thisspanit.next();
				
				long teststart = 0;
				long thisstart = 1000000;
				
				//Spans should be in the same context (tweet expansions)
				if(thisspan.expansion==testspan.expansion){
					teststart = testspan.startoffset;
					thisstart = thisspan.startoffset;
				} else if(useTwitterExpansion){
					//If not in same context, they might be in expansions 
					//expanded from items in the same context. If we are 
					//using twitter expansion, these count.
					AnnotationSet inputAS = document.getAnnotations(inputASName);
					
					Annotation thiscontext = thisspan.expansion;
					Annotation testcontext = testspan.expansion;

					Annotation thisorigin = thisspan.lookuplistann;
					Annotation testorigin = testspan.lookuplistann;
					
					//Replace contexts with contexts of annotations that they are
					//expansions of. Should both be null actually since expansions
					//as currently implemented always originate in the tweet body.
					if(thiscontext!=null){
						Integer thisreferent = 
								(Integer)thiscontext.getFeatures().get("mentionId");
						thisorigin = inputAS.get(thisreferent);
						thiscontext = DocumentEntitySet.getTwitterExpansionContextAnnotation(
								thisorigin, document);
					}
					
					if(testcontext!=null){
						Integer testreferent = 
								(Integer)testcontext.getFeatures().get("mentionId");
						testorigin = inputAS.get(testreferent);
						testcontext = DocumentEntitySet.getTwitterExpansionContextAnnotation(
								testorigin, document);
					}
					
					if(thiscontext==testcontext){
						teststart = testorigin.getStartNode().getOffset();
						thisstart = thisorigin.getStartNode().getOffset();
					}		
				}
				
				long thisdistance = Math.abs(thisstart-teststart);
								
				if(thisdistance<shortestdistance){
					shortestdistance=thisdistance;
				}
			}
		}
                benchmarkCheckpoint(startTime, "__shortestDistanceFrom");
		
		return shortestdistance;
	}

	public String getContext(int contextLength, Boolean useTwitterExpansion){
                long startTime = Benchmark.startPoint();
		Set<String> contextSet = new HashSet<String>();
	    String contextString = "";
		//int docContentLength = document.getContent().toString().length();
	    AnnotationSet inputAS = this.document.getAnnotations(inputASName);
	    AnnotationSet hashtagExpansions = inputAS.get("TwitterExpanderHashtag");
	    AnnotationSet urlExpansions = inputAS.get("TwitterExpanderURL");
	    AnnotationSet userIDExpansions = inputAS.get("TwitterExpanderUserID");
	    
		Iterator<LookupList> spanit = spans.iterator();
		
		while(spanit.hasNext()){
			LookupList span = spanit.next();
			
		    long startOffset = span.startoffset - contextLength - 2;
		    long endOffset = span.endoffset + contextLength + 2;
		    if(startOffset < span.startcontext) startOffset = span.startcontext;
		    if(endOffset > span.endcontext) endOffset = span.endcontext;

		    String context = gate.Utils.cleanStringFor(
		    		document, startOffset, endOffset);
		    
		    if(useTwitterExpansion){ //Add all expansions for the string
		    	AnnotationSet hashtags = inputAS.get("Hashtag", startOffset, endOffset);
		    	AnnotationSet urls = inputAS.get("URL", startOffset, endOffset);
		    	AnnotationSet userIDs = inputAS.get("UserID", startOffset, endOffset);
		    	
		    	Set<Integer> idsToExpand = new HashSet<Integer>();
		    			    	
		    	Iterator<Annotation> hit = hashtags.iterator();
		    	while(hit.hasNext()){
		    		Annotation h = hit.next();
		    		idsToExpand.add(h.getId());
		    	}

		    	Iterator<Annotation> uit = urls.iterator();
		    	while(uit.hasNext()){
		    		Annotation u = uit.next();
		    		idsToExpand.add(u.getId());
		    	}

		    	Iterator<Annotation> iit = userIDs.iterator();
		    	while(iit.hasNext()){
		    		Annotation i = iit.next();
		    		idsToExpand.add(i.getId());
		    	}
		    			    	
		    	//Now we have IDs for everything we need to append to this context.

		    	Iterator<Annotation> heit = hashtagExpansions.iterator();
		    	while(heit.hasNext()){
		    		Annotation he = heit.next();
		    		Integer mentionId = (Integer)he.getFeatures().get("mentionId");
		    		if(idsToExpand.contains(mentionId)){
		    			context = context + " " + Utils.cleanStringFor(document, he);
		    		}
		    	}

		    	Iterator<Annotation> ueit = urlExpansions.iterator();
		    	while(ueit.hasNext()){
		    		Annotation ue = ueit.next();
		    		Integer mentionId = (Integer)ue.getFeatures().get("mentionId");
		    		if(idsToExpand.contains(mentionId)){
		    			context = context + " " + Utils.cleanStringFor(document, ue);
		    		}
		    	}

		    	Iterator<Annotation> ieit = userIDExpansions.iterator();
		    	while(ieit.hasNext()){
		    		Annotation ie = ieit.next();
		    		Integer mentionId = (Integer)ie.getFeatures().get("mentionId");
		    		if(idsToExpand.contains(mentionId)){
		    			context = context + " " + Utils.cleanStringFor(document, ie);
		    		}
		    	}
		    	
		    }
		    
		    String[] words = context.split("[_ \\p{Punct}]+");

		    if(words!=null && words.length>0){
			    if(startOffset==span.startcontext){
			    	contextSet.add(words[0]);
			    }
			    
			    for(int i=1;i<words.length-1;i++){
			    	contextSet.add(words[i]);
			    }
	
			    if(endOffset==span.endcontext){
			    	contextSet.add(words[words.length-1]);
			    }
		    }
		}
		    
	    Object[] dedupedWords = contextSet.toArray();
		    
	    for(int i=0;i<dedupedWords.length;i++){
	    	contextString = contextString + " " + dedupedWords[i].toString();
	    }
            benchmarkCheckpoint(startTime, "__getContext");
	    
	    return contextString;
	}
	
	public Set<String> contextTokensAsList(int contextLength, boolean nounsOnly){
                long startTime = Benchmark.startPoint();
		AnnotationSet inputAS = document.getAnnotations(inputASName);
		int docContentLength = document.getContent().toString().length();
		Set<String> contextSet = new HashSet<String>();

		Iterator<LookupList> spanit = spans.iterator();

		while(spanit.hasNext()){
			LookupList span = spanit.next();

			long startOffset = span.startoffset - contextLength - 2;
			long endOffset = span.endoffset + contextLength + 2;
			if(startOffset < 0) startOffset = 0;
			if(endOffset > docContentLength) endOffset = docContentLength;

			for(Annotation aToken : inputAS.getContained(startOffset, endOffset)
					.get("Token")) {
				if(nounsOnly){
					String cat = (String)aToken.getFeatures().get("category");
					if(cat != null && cat.startsWith("NN")) {
						contextSet.add(Utils.stringFor(document, aToken));
					}
				} else { //Just add all of them
					contextSet.add(Utils.stringFor(document, aToken));
				}

				// for the hindi plugin, where tokens are assigned english translations
				String english = (String) aToken.getFeatures().get("english");
				if(english != null && english.trim().length() > 0) {
					String [] words = english.split(";");
					for(String aWord : words) {
						contextSet.add(aWord);
					}
				}
			}
		}
                benchmarkCheckpoint(startTime, "__contextTokensAsList");
		return contextSet;
	}

	public String getCleanStringForBestSpan(){
		List<LookupList> best = this.getBestSpans(1);
		if(best.size()==1){
			LookupList thebest = best.get(0);
			return thebest.getCleanString();
		} else {
			return "";
		}
	}

	public Set<String> getInstSet() {
                long startTime = Benchmark.startPoint();
		if(doesinstlistneedrecalculating){
			Set<String> newinstlist = new HashSet<String>();
			for(int i=0;i<this.spans.size();i++){
				LookupList sp = this.spans.get(i);
				newinstlist.addAll(sp.getAnnotationsbyinst().keySet());
			}
			this.instlist = newinstlist;
			doesinstlistneedrecalculating = false;
		}
                benchmarkCheckpoint(startTime, "__getInstSet");
		return this.instlist;
	}

	public List<Annotation> getAnnsByInst(String inst){
                long startTime = Benchmark.startPoint();
		List<Annotation> toreturn = new ArrayList<Annotation>();
		for(int i=0;i<this.getSpans().size();i++){
			LookupList sp = spans.get(i);
			Annotation anntoadd = sp.getAnnotationbyinst(inst);
			if(anntoadd!=null){
				toreturn.add(anntoadd);
			}
		}
                benchmarkCheckpoint(startTime, "__getAnnsByInst");
		return toreturn;
	}
	
	public void print(){
		System.out.println(this.getInstSet().size() + " insts: " 
				+ this.getCleanStringForBestSpan());
		List<LookupList> lls = this.getSpans();
		for(int j=0;j<lls.size();j++){
			LookupList ll = lls.get(j);
			System.out.println("    LookupList " + j + ": " + ll.getKey());
			Set<String> llss = ll.getAnnotationsbyinst().keySet();
			Iterator<String> llssit = llss.iterator();
			while(llssit.hasNext()){
				String s = llssit.next();
				System.out.println("      " + s);
			}
		}
	}
	
	public List<LookupList> getSpans() {
		return spans;
	}

	public void setSpans(List<LookupList> spans) {
		this.spans = spans;
	}

	/*public LookupList getKeyspan() {
		return keyspan;
	}

	public void setKeyspan(LookupList keyspan) {
		this.keyspan = keyspan;
	}*/

// **** BENCHMARK-RELATED
  protected void benchmarkCheckpoint(long startTime, String name) {
    if (Benchmark.isBenchmarkingEnabled()) {
      Benchmark.checkPointWithDuration(
              Benchmark.startPoint() - startTime,
              Benchmark.createBenchmarkId(name, this.getBenchmarkId()),
              this, null);
    }
  }

  public String getBenchmarkId() {
    return benchmarkId;
  }

  public void setBenchmarkId(String string) {
    benchmarkId = string;
  }
  private String benchmarkId = "Entity";


          
        
        
}
