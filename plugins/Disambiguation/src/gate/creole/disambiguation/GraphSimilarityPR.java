package gate.creole.disambiguation;

import gate.Annotation;
import gate.ProcessingResource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.Benchmark;
import gate.util.Benchmarkable;

import java.lang.ProcessBuilder.Redirect;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

/**
 * 
 */
@CreoleResource(name = "Graph Similarity PR", comment = "finds relations between the sem annotations using PageRank.")
public class GraphSimilarityPR extends AbstractLanguageAnalyser implements
        ProcessingResource, Benchmarkable {

  /**
   * serial version id
   */
  private static final long serialVersionUID = 1634599221705298081L;

  private Boolean useTwitterExpansion;
  
  private Logger logger = Logger.getLogger(gate.creole.disambiguation.StructuralSimilarityPR.class);  
    
  /*public enum Mode {
		STATIC,
		PERSONALIZEDPAGERANK;
  }*/
  
  @Override
  public void execute() throws ExecutionException {
    long start = System.currentTimeMillis();

    //No coref for GraphSimilarityPR. It's because UKB uses its own precompiled
    //dictionary to map labels to candidates so we can't do a context-dependent
    //one.
    DocumentEntitySet ents = new DocumentEntitySet(document, inputASName,
            false, Constants.yodieCorefType);
    //ents.print();


    Iterator<Entity> entit = null;

    if (document.getFeatures().get(Constants.tacSwitch) != null
            && document.getFeatures().get(Constants.tacSwitch)
            .toString().equals("true")) {
      entit = ents.getKeyOverlapsIterator(document);
    } else {
        //Tweet span iterator will figure out for itself if this is
        //an expanded tweet. If it is, it just returns an iterator
        //over entities that feature in the tweet body. Otherwise, all 
        //of them.
        entit = ents.getTweetSpanIterator(document, Constants.twExpOrigTexSzDocFt);
      }
    
    //System.out.println(ents.getTweetSpanEntities().size() + " tweet span entities.");

    HashMap<String, Entity> entmap = new HashMap<String, Entity>();
    String ukbcontext = "";
    while (entit != null && entit.hasNext()) { //For each entity
      Entity ent = entit.next();
      ukbcontext = ukbcontext + ent.getCleanStringForBestSpan().replace(' ', '_') + "##w" + entmap.size() + "#1 ";
      entmap.put("w" + entmap.size(), ent);
    }
    ukbcontext.trim();
    ukbcontext = "ctx_01\n" + ukbcontext;
    
    File workingdir = (new File(graphURL.getFile())).getParentFile();
    Random random = new Random();
    File contextfile = new File(workingdir, ".ukbcontext" + System.currentTimeMillis() + random.nextInt(1000)+1);
    File outfile = new File(workingdir, ".ukbout" + System.currentTimeMillis() + random.nextInt(1000)+1);
    
    FileWriter fw;
	try {
		fw = new FileWriter(contextfile);
	    fw.write(ukbcontext);
	    fw.flush();
	    fw.close();
	} catch (IOException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}

    ProcessBuilder pb = new ProcessBuilder("ukb_wsd", "--nopos", "--static", "--allranks", "--prank_weight", "-K" + graphURL.getPath(),
    		"-D" + dictionaryURL.getPath(), contextfile.getPath());
    pb.redirectOutput(Redirect.to(outfile));
    pb.redirectError(Redirect.INHERIT);
    Process p = null;
    try {
		p = pb.start();
	} catch (IOException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
    

	try {
		p.waitFor();
	} catch (InterruptedException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
    
    List<String> ukboutput = null;
    try {
		ukboutput = Files.readAllLines(outfile.toPath(), Charset.defaultCharset());
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

    if(ukboutput!=null){ //we got a result	
    	for(int i=1;i<ukboutput.size();i++){
    		String line = ukboutput.get(i);
    		String[] parts = line.split("\\s+");
    		for(int j=2;j<parts.length-2;j++){
    			String[] cuivalpair = parts[j].split("/");
    			if(cuivalpair.length==2){
    				Entity thisent = entmap.get(parts[1]);
    				if(thisent!=null){
		    			List<Annotation> annstoscore = thisent.getAnnsByInst(cuivalpair[0]);
		    			for(Annotation ann : annstoscore){
		    				ann.getFeatures().put(outputFeature, cuivalpair[1]);
		    			}
    				}
    			} else {
    				//System.out.println("Graph PR: Failed to parse cui/val pair: " + parts[j]);
    			}
    		}
    	}
    }
    
    ents = null;


	try {
		Files.deleteIfExists(contextfile.toPath());
		Files.deleteIfExists(outfile.toPath());
	} catch (IOException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
		
    
    long end = System.currentTimeMillis();
    System.out.println("Graph PR:" + (end - start));
  }

  // returns the elapsed Time in millisecods since the time passed as parameter
  private long elapsedTime(long startMillis) {
    return (System.currentTimeMillis()-startMillis);
  }
  
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
  private String benchmarkId = this.getName();

  
  // ********** PR PARAMETERS
  
  @CreoleParameter
  @RunTime
  @Optional
  public void setInputASName(String inputASName) {
    this.inputASName = inputASName;
  }
  public String getInputASName() {
    return inputASName;
  }
  private String inputASName;


  @CreoleParameter(defaultValue = "scPageRank")
  @RunTime
  @Optional
  public void setoutputFeature(String outputFeature) {
    this.outputFeature = outputFeature;
  }
  public String getoutputFeature() {
    return this.outputFeature;
  }
  private String outputFeature;

  /*@CreoleParameter(defaultValue = "scMeshFreq")
  @RunTime
  @Optional
  public void setPPRFeature(String pPRFeature) {
    this.pPRFeature = pPRFeature;
  }
  public String getPPRFeature() {
    return this.pPRFeature;
  }
  private String pPRFeature;*/

  public Boolean getUseTwitterExpansion() {
    return this.useTwitterExpansion;
  }

  @RunTime
  @CreoleParameter(defaultValue = "true")
  public void setUseTwitterExpansion(Boolean useTwitterExpansion) {
    this.useTwitterExpansion = useTwitterExpansion;
  }

  public URL getDictionaryURL() {
    return this.dictionaryURL;
  }

  @CreoleParameter
  public void setDictionaryURL(URL dictionaryURL) {
    this.dictionaryURL = dictionaryURL;
  }
  private URL dictionaryURL;

  public URL getGraphURL() {
    return this.graphURL;
  }

  @CreoleParameter
  public void setGraphURL(URL graphURL) {
    this.graphURL = graphURL;
  }
  private URL graphURL;

	/*@RunTime
	@CreoleParameter(defaultValue = "STATIC", comment = "Which PageRank to compute.")
	public void setMode(Mode mode) {
		this.mode = mode;
	}
	
	public Mode getMode() {
		return this.mode;
	}
	private Mode mode;*/
	
  /*@CreoleParameter(defaultValue = "true")
  @RunTime
  @Optional
  public void setUseCoreference(Boolean useCoreference) {
    this.useCoreference = useCoreference.booleanValue();
  }
  public Boolean getUseCoreference() {
    return this.useCoreference;
  }
  private boolean useCoreference;*/

}
