package gate.creole.disambiguation;

import edu.ucla.sspace.common.DocumentVectorBuilder;
import edu.ucla.sspace.common.SemanticSpace;
import edu.ucla.sspace.common.Similarity;
import edu.ucla.sspace.vector.DoubleVector;
import edu.ucla.sspace.vector.DenseVector;
import edu.ucla.sspace.vsm.VectorSpaceModel;

import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.disambiguation.matrices.DocumentWordMatrix;
import gate.creole.disambiguation.matrices.DocumentWordMatrix.SparseVector;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.Benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

import org.apache.log4j.Logger;

/**
 *
 */
@CreoleResource(name = "TFICF Contextual Similarity PR", comment = "This PR calculates similarity for every candidate URI using the TFICF approach.")
public class TFICFContextualSimilarityPR extends AbstractLanguageAnalyser implements
ProcessingResource {

	  /**
	   * serial version
	   */
	  private static final long serialVersionUID = 2538181800238288794L;
	  /**
	   * Name of the DB table
	   */
	  private String jdbcTableName;
	  /**
	   * Name of the DB
	   */
	  private String dbName;
	  /**
	   * Location of the database.
	   */
	  private URL databaseDirectory;

	  private Boolean useTwitterExpansion;
	  
	  private Logger logger = Logger.getLogger(gate.creole.disambiguation.VectorContextualSimilarityPR.class);    
	  
	  /**
	   * name of the input annotation set
	   */
	  private String inputASName;
	  /**
	   * Choice of similarity function
	   */
	  private Similarity.SimType similarityFunction;
	  /**
	   * context length
	   */
	  private int contextLength = 100;
	  /**
	   * Whether or not to use coreference information if available.
	   */
	  private boolean useCoreference;
	  /**
	   * Name of the output feature
	   */
	  	  
	  //private String rankOutputFeature = SemanticConstants.CONTEXTUAL_SIMILARITY; TODO!!!!
	  private String outputFeature;
	  private PreparedStatement stSelect;
	  private Connection connection;
	  private String jdbcUrl;
	  private String fieldName;
	  private String identifierName;
	  List<String> DEFAULT_STOP_WORDS = new ArrayList<String>(Arrays.asList(",", ".", "?",
	          "!", ":", ";", "#", "~", "^", "@", "%", "&", "(", ")", "[", "]", "{", "}",
	          "|", "\\", "<", ">", "-", "+", "*", "/", "=", "'", "\"", "'s", "1", "2",
	          "3", "4", "5", "6", "7", "8", "9", "0", "a", "about", "above", "above",
	          "across", "after", "afterwards", "again", "against", "all", "almost",
	          "alone", "along", "already", "also", "although", "always", "am", "among",
	          "amongst", "amoungst", "amount", "an", "and", "another", "any", "anyhow",
	          "anyone", "anything", "anyway", "anywhere", "are", "around", "as", "at",
	          "b", "back", "be", "became", "because", "become", "becomes", "becoming",
	          "been", "before", "beforehand", "behind", "being", "below", "beside",
	          "besides", "between", "beyond", "bill", "both", "bottom", "but", "by", "c",
	          "call", "can", "cannot", "cant", "co", "con", "could", "couldnt", "cry",
	          "d", "de", "describe", "detail", "do", "done", "down", "due", "during",
	          "e", "each", "eg", "eight", "either", "eleven", "else", "elsewhere",
	          "empty", "enough", "etc", "even", "ever", "every", "everyone",
	          "everything", "everywhere", "except", "f", "few", "fifteen", "fify",
	          "fill", "find", "fire", "first", "five", "for", "former", "formerly",
	          "forty", "found", "four", "from", "front", "full", "further", "g", "get",
	          "give", "go", "h", "had", "has", "hasnt", "have", "he", "hence", "her",
	          "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself",
	          "him", "himself", "his", "how", "however", "hundred", "i", "ie", "if",
	          "in", "inc", "indeed", "interest", "into", "is", "it", "its", "itself",
	          "j", "k", "keep", "l", "last", "latter", "latterly", "least", "less",
	          "ltd", "m", "made", "many", "may", "me", "meanwhile", "might", "mill",
	          "mine", "more", "moreover", "most", "mostly", "move", "much", "must", "my",
	          "myself", "n", "name", "namely", "neither", "never", "nevertheless",
	          "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing",
	          "now", "nowhere", "o", "of", "off", "often", "on", "once", "one", "only",
	          "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves",
	          "out", "over", "own", "p", "part", "per", "perhaps", "please", "put", "q",
	          "r", "rather", "re", "s", "same", "see", "seem", "seemed", "seeming",
	          "seems", "serious", "several", "she", "should", "show", "side", "since",
	          "sincere", "six", "sixty", "so", "some", "somehow", "someone", "something",
	          "sometime", "sometimes", "somewhere", "still", "such", "system", "t",
	          "take", "ten", "than", "that", "the", "their", "them", "themselves",
	          "then", "thence", "there", "thereafter", "thereby", "therefore", "therein",
	          "thereupon", "these", "they", "thickv", "thin", "third", "this", "those",
	          "though", "three", "through", "throughout", "thru", "thus", "to",
	          "together", "too", "top", "toward", "towards", "twelve", "twenty", "two",
	          "u", "un", "under", "until", "up", "upon", "us", "v", "very", "via", "w",
	          "was", "we", "well", "were", "what", "whatever", "when", "whence",
	          "whenever", "where", "whereafter", "whereas", "whereby", "wherein",
	          "whereupon", "wherever", "whether", "which", "while", "whither", "who",
	          "whoever", "whole", "whom", "whose", "why", "will", "with", "within",
	          "without", "would", "x", "y", "yet", "you", "your", "yours", "yourself",
	          "yourselves", "z"));
	  

	  /**
	   * The vector builder for building similarity vectors.
	   */
	  private DocumentVectorBuilder vectorBuilder;

	  /**
	   * Initialise this resource, and return it.
	   */
	  public Resource init() throws ResourceInstantiationException {
	    edu.ucla.sspace.util.LoggerUtil.setLevel(java.util.logging.Level.SEVERE);
	    long startTime = Benchmark.startPoint();

	      try {
	        jdbcUrl = "jdbc:h2:"
	                + databaseDirectory.getPath() 
                        + (databaseDirectory.getPath().endsWith("/") ? "" : "/")
	                + this.dbName
	                + ";ACCESS_MODE_DATA=r;CACHE_SIZE=1000000";
	        System.out.println(jdbcUrl);
	      } catch (Exception e) {
	        e.printStackTrace();
	      }

	      try {
	        Class.forName("org.h2.Driver");
	        connection = DriverManager.getConnection(jdbcUrl, "", "");
	      } catch (Exception e) {
	        e.printStackTrace();
	      }

	      if (connection == null) {
	        System.out.println("TFICF Contextual Similarity PR: "
	                + "Failed to connect to the database.");
	      } else {
	        try {
	          stSelect = connection.prepareStatement(
	                  "SELECT " + fieldName + " FROM " + jdbcTableName + " WHERE " + identifierName + " = ?");
	        } catch (Exception e) {
	          e.printStackTrace();
	        }

	        if (stSelect == null) {
	          System.out.println("TFICF Contextual Similarity PR: "
	                  + "Failed to prepare statement.");
	        }
	      }
	      benchmarkCheckpoint(startTime, "__init");
	      return super.init();
	    } // init()


	    @Override
	    public void execute
	    () throws ExecutionException {

	      long start = System.currentTimeMillis(); // record the start time

	      DocumentEntitySet ents = new DocumentEntitySet(document, inputASName,
	              true, Constants.yodieCorefType);

	      Iterator<Entity> entsit = null;

	      if (document.getFeatures().get(Constants.tacSwitch) != null
	              && document.getFeatures().get(Constants.tacSwitch)
	              .toString().equals("true")) {
	        entsit = ents.getKeyOverlapsIterator(document);
	      } else {
	          //Tweet span iterator will figure out for itself if this is
	          //an expanded tweet. If it is, it just returns an iterator
	          //over entities that feature in the tweet body. Otherwise, all 
	          //of them.
	        entsit = ents.getTweetSpanIterator(document, Constants.twExpOrigTexSzDocFt);
	      }
	      
	      while (entsit != null && entsit.hasNext()) {

	        long startTime = Benchmark.startPoint();
	        Entity ent = entsit.next();

			//A simple document word matrix for simple TFIDF
			DocumentWordMatrix dwm = new DocumentWordMatrix();

	        SemanticSpace candidateListSemSpace = null;
			if(useSSpace){
		        //Small semantic space for this candidate list.
				//Using candidates rather than words as dimensions should reduce
				//dimensionality a bit and perhaps improve the result. However
				//it's optional. We have some problems with SSpace making temp files.
		        try {
					candidateListSemSpace = new VectorSpaceModel();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}

	        String contextString = ent.getContext(contextLength, useTwitterExpansion);
	        //System.out.println("TARGET: " + contextString);
	        contextString = processLikeSSpace(contextString);

	        startTime = Benchmark.startPoint();

	        benchmarkCheckpoint(startTime, "__vec2");
	        
	        Set<String> candidatesbyinst = ent.getInstSet();

	        Iterator<String> candsit = candidatesbyinst.iterator();
	        benchmarkCheckpoint(startTime, "__pre1");

	        //Store the candidate vectors. We're going to use them later.
	        Map<String, String> candTexts = new HashMap<String, String>();
	        while (candsit != null && candsit.hasNext()) {

	          if (interrupted) {
	            return;
	          }

	          startTime = Benchmark.startPoint();
	          String cand = candsit.next();

	          String candidateText = "";
	          try {

	            stSelect.setString(1, cand);
	            ResultSet results = stSelect.executeQuery();

	            if (results.next()) {  // we found something
	              candidateText = results.getString(fieldName);
	            }

	            results.close();
	          } catch (Exception e) {
	            e.printStackTrace();
	          }
	          candidateText = processLikeSSpace(candidateText);
	          candTexts.put(cand, candidateText);

			  //Add to the simple document word matrix
			  dwm.addDocument(candidateText);
				
	          //Add it to the candidate-list-specific model
			  if(useSSpace && candidateListSemSpace!=null){
		          try {
					candidateListSemSpace.processDocument(
							  new BufferedReader(new StringReader(candidateText)));
				  } catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
				  }
			  }
	        }

			//Set up the simple context vector for comparison
			SparseVector tfidfContextVector = dwm.tfidf(contextString);
			
			DoubleVector simpleContextVector = 
					dwm.sparseVectorToCompactSparseVector(tfidfContextVector);
			
			DocumentVectorBuilder tficfVectorBuilder = null;
			DoubleVector tficfContextVector = null;
			if(useSSpace && candidateListSemSpace!=null){
				//Semantic version
		        Properties tficfConfig = new Properties();
		        // JP: the sspace seems to store vectors in transposed form: 
		        // rows are documents and columns are terms, while the 
		        // edu.ucla.sspace.matrix.TfIdfTransform transform expects it the 
		        // other way round.
		        //tficfConfig.put(VectorSpaceModel.MATRIX_TRANSFORM_PROPERTY,
		        //  "edu.ucla.sspace.matrix.TfIdfDocStripedTransform");
		        tficfConfig.put(VectorSpaceModel.MATRIX_TRANSFORM_PROPERTY,
		          "edu.ucla.sspace.matrix.TfIdfTransform");
		        candidateListSemSpace.processSpace(tficfConfig);
	
		        tficfVectorBuilder = new DocumentVectorBuilder(candidateListSemSpace);
	
		        tficfContextVector = tficfVectorBuilder.buildVector(
		                new BufferedReader(new StringReader(contextString)),
		                new DenseVector(candidateListSemSpace.getVectorLength()));
			}

	        //System.out.println("\nNow doing " + ent.getCleanStringForBestSpan());
	        //System.out.println("CONTEXT STRING: " + contextString);
	        //System.out.println("CONTEXT: " + tficfContextVector.toString());
	        
	        candsit = candidatesbyinst.iterator();

	        while (candsit != null && candsit.hasNext()) {
	        	String cand = candsit.next();
	        	String candtext = candTexts.get(cand);
				SparseVector tfidfCandidateVector = dwm.tfidf(candtext);
				DoubleVector simpleCandidateVector = 
						dwm.sparseVectorToCompactSparseVector(tfidfCandidateVector);

				//Create simple feature
				Double simpleSimilarity = Similarity.getSimilarity(
						similarityFunction, simpleCandidateVector, 
						simpleContextVector);
				ent.putFeatureFloat(cand, this.outputFeature + "Simple", 
						simpleSimilarity.floatValue());
				
				if(useSSpace && candidateListSemSpace!=null 
						&& tficfVectorBuilder!=null && tficfContextVector!=null){
				//Semantic candidate vector
		        	DoubleVector candidateVector = tficfVectorBuilder.buildVector(
		                    new BufferedReader(new StringReader(candtext)),
		                    new DenseVector(candidateListSemSpace.getVectorLength()));

					//Semantic
		            Double similarity = Similarity.getSimilarity(
		            		similarityFunction, candidateVector, tficfContextVector);
		            //System.out.println("Similarity: " + similarity);
		            ent.putFeatureFloat(cand, this.outputFeature + "Semantic", similarity.floatValue());
				}
	        	
	            //System.out.println("CAND STRING: " + candtext);
	            //System.out.println("CAND: " + candidateVector.toString());
        	 }
	      }

	      ents = null;

	      long end = System.currentTimeMillis();
	      System.out.println("Total time for TFICF contextual similarity PR:" + (end - start));
	    }

	  

	  private String processLikeSSpace(String instr) {
	    String[] words = instr.split("[_ \\p{Punct}]+");
	    String outstr = "";
	    for (int i = 0; i < words.length; i++) {
	      String thisword = words[i].toLowerCase();
	      if (!DEFAULT_STOP_WORDS.contains(thisword)) {
	        outstr = outstr + " " + thisword;
	      }
	    }
	    return outstr;
	  }

	  /**
	   * The cleanup method
	   */
	  @Override
	  public void cleanup() {
	    super.cleanup();
	    try {
	      connection.close();
	    } catch (Exception e) {
	      e.printStackTrace();
	    }

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

	  public Integer getContextLength() {
	    return Integer.valueOf(contextLength);
	  }

	  @CreoleParameter
	  @RunTime
	  @Optional
	  public void setContextLength(Integer conlen) {
	    this.contextLength = conlen.intValue();
	  }

	  public String getOutputFeature() {
	    return this.outputFeature;
	  }

	  @CreoleParameter(defaultValue = "scContextualSimilarityTFICF")
	  @RunTime
	  @Optional
	  public void setOutputFeature(String outputFeature) {
	    this.outputFeature = outputFeature;
	  }

	  public Similarity.SimType getSimilarityFunction() {
	    return this.similarityFunction;
	  }

	  @CreoleParameter(defaultValue = "COSINE")
	  @RunTime
	  @Optional
	  public void setSimilarityFunction(Similarity.SimType sf) {
	    this.similarityFunction = sf;
	  }

	  public URL getDatabaseDirectory() {
	    return this.databaseDirectory;
	  }

	  @CreoleParameter
	  public void setDatabaseDirectory(URL databaseDirectory) {
	    this.databaseDirectory = databaseDirectory;
	  }

	  public String getJdbcTableName() {
	    return this.jdbcTableName;
	  }

	  @CreoleParameter(defaultValue = "shortabstracts")
	  public void setJdbcTableName(String dbtablename) {
	    this.jdbcTableName = dbtablename;
	  }

	  /*public Boolean getUseCoreference() {
	    return new Boolean(this.useCoreference);
	  }

	  @CreoleParameter(defaultValue = "true")
	  @RunTime
	  @Optional
	  public void setUseCoreference(Boolean useCoreference) {
	    this.useCoreference = useCoreference.booleanValue();
	  }*/

	  public String getDbName() {
	    return this.dbName;
	  }

	  @CreoleParameter(defaultValue = "abstracts")
	  public void setDbName(String dbName) {
	    this.dbName = dbName;
	  }

	  public String getFieldName() {
	    return this.fieldName;
	  }

	  @CreoleParameter(defaultValue = "abstract")
	  public void setFieldName(String fieldName) {
	    this.fieldName = fieldName;
	  }

	  public String getIdentifierName() {
	    return this.identifierName;
	  }

	  @CreoleParameter(defaultValue = "uri")
	  public void setIdentifierName(String identifierName) {
	    this.identifierName = identifierName;
	  }

	  public Boolean getUseTwitterExpansion() {
	    return this.useTwitterExpansion;
	  }

	  @RunTime
	  @CreoleParameter(defaultValue = "true")
	  public void setUseTwitterExpansion(Boolean useTwitterExpansion) {
	    this.useTwitterExpansion = useTwitterExpansion;
	  }

	  private Boolean useSSpace;
	  
	  public Boolean getUseSSpace() {
	    return this.useSSpace;
	  }

	  @RunTime
	  @CreoleParameter(defaultValue = "true")
	  public void setUseSSpace(Boolean useSSpace) {
	    this.useSSpace = useSSpace;
	  }
	  
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
	  
	  private long elapsedTime(long startMillis) {
	    return (System.currentTimeMillis()-startMillis);
	  }

}
