package gate.creole.disambiguation;

import com.jpetrak.gate.jdbclookup.JdbcLR;

import gate.ProcessingResource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.disambiguation.fastgraph.FastGraphLR;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.Benchmark;
import gate.util.Benchmarkable;
import gate.util.GateRuntimeException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import org.apache.log4j.Logger;

// TODO: (JP 2014-09-21) We should refactor the code so that the calculation
// methods go into LodieUtils or a utils class in this plugin. This would 
// allow for much easier testing and for calling the methods as API 
// methods for any two instances, e.g. even from an interactive scala REPL
// for debugging.


/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Structural Similarity PR", comment = "finds relations between the sem annotations using fastgraph")
public class StructuralSimilarityPR extends AbstractLanguageAnalyser implements
        ProcessingResource, Benchmarkable {

  /**
   * serial version id
   */
  private static final long serialVersionUID = 1634599221705298081L;

  private Boolean useTwitterExpansion;
  
  private Logger logger = Logger.getLogger(gate.creole.disambiguation.StructuralSimilarityPR.class);  
  
  private PreparedStatement stSelect;
  private Connection connection;
  
  public void initConnection() {
    if (connection == null) {
      throw new GateRuntimeException("We have a JdbcLR with a null connection!");
    } else {
      try {
        stSelect = connection.prepareStatement(
                  "SELECT incomingUris FROM wikilinks WHERE uri = ?");
       } catch (Exception e) {
         throw new GateRuntimeException("Could not prepare select for wikilinks",e);
       }
    }
  } 
  
  
  @Override
  public void execute() throws ExecutionException {
    long start = System.currentTimeMillis();

    DocumentEntitySet ents = new DocumentEntitySet(document, inputASName,
            true, Constants.yodieCorefType);
    //ents.print();


    Iterator<Entity> origentit = null;

    if (document.getFeatures().get(Constants.tacSwitch) != null
            && document.getFeatures().get(Constants.tacSwitch)
            .toString().equals("true")) {
      origentit = ents.getKeyOverlapsIterator(document);
    } else {
        //Tweet span iterator will figure out for itself if this is
        //an expanded tweet. If it is, it just returns an iterator
        //over entities that feature in the tweet body. Otherwise, all 
        //of them.
        origentit = ents.getTweetSpanIterator(document, Constants.twExpOrigTexSzDocFt);
      }
    
    //System.out.println(ents.getTweetSpanEntities().size() + " tweet span entities.");


    while (origentit != null && origentit.hasNext()) { //For each entity
      Entity origent = origentit.next();

      //Get the candidates
      Set<String> origcands = origent.getInstSet();

      //Get proximal entities
      SortedMap<Long, Entity> proxents =
              ents.getProximalEntities(origent, contextChars, useTwitterExpansion);
      Iterator<Entity> proxentit = null;
      if (proxents != null) {
        proxentit = proxents.values().iterator();
      }
      //System.out.println(proxents.size() + " proxents for this one.");

      while (proxentit != null && proxentit.hasNext()) {
        Entity proxent = proxentit.next();

        //System.out.println("Doing " 
        //		+ origent.getCleanStringForKeySpan(document) + " and "
        //		+ proxent.getCleanStringForKeySpan(document));
        
        //Get the candidates for the proximal entity
        Set<String> proxcands = proxent.getInstSet();

        //Now we have the two candidate lists for this entity pair
        //so we can iterate through and relate them.
        Iterator<String> origcandsit = null;
        if (origcands != null) {
        	origcandsit = origcands.iterator();
        }

        while (origcandsit != null && origcandsit.hasNext()) { //For each candidate on the origin entity
          String inst = origcandsit.next();

          float unidirectionalAccumulator = 0.0F;
          float bidirectionalAccumulator = 0.0F;
          float sharedParentAccumulator = 0.0F;
          float sharedChildAccumulator = 0.0F;
          float unidirectionalIndirectAccumulator = 0.0F;
          float bidirectionalIndirectAccumulator = 0.0F;
          float relatednessAccumulator = 0.0F;

          //inst = inst.substring(StructuralSimilarityPRv2.namespace.length());

          Iterator<String> proxcandsit = null;
          if (proxcands != null) {
            proxcandsit = proxcands.iterator();
          }

          while (proxcandsit != null && proxcandsit.hasNext()) { //For each candidate on the origin entity

            if (interrupted) {
              return;
            }

            String contextInst = proxcandsit.next();

            //contextInst = contextInst.substring(StructuralSimilarityPRv2.namespace.length());


            //System.out.println("Inst: " + inst + ", contextInst: " + contextInst);

            if (contextInst == null || inst.equals(contextInst)) {
              continue;
            }

            //float thisDistance = (float)getDistanceBetweenTwoAnnsInChars(ann, conAnn);
            float thisDistance = origent.shortestDistanceFrom(proxent, useTwitterExpansion);

            // JP: code template to run the relatedness code: 
            // By default, the scoring pipeline has the parameter wikiLinkDbLR
            // set to a LR that represents the wikilinks database. If this
            // parameter is set, the code should be run. To disable running,
            // the parameter should get set to null from the config file.
            double relatedness = 0.0;
            if(wikiLinksDbLR != null) {
              relatedness = getRelatedness(inst, contextInst);
            }
            
            //DIRECT RELATIONS

            int directRelationCount = getDirectRelationCount(inst, contextInst);
            int reverseRelationCount = getDirectRelationCount(contextInst, inst);

            unidirectionalAccumulator += directRelationCount / thisDistance;
            bidirectionalAccumulator += (directRelationCount + reverseRelationCount) / thisDistance;

            //INDIRECT RELATIONS

            int sharedParentCount = getSharedParentRelationCount(contextInst, inst);
            int sharedChildCount = getSharedChildRelationCount(contextInst, inst);
            int outboundCount = getSequenceRelationCount(inst, contextInst);
            int inboundCount = getSequenceRelationCount(contextInst, inst);

            sharedParentAccumulator += sharedParentCount / thisDistance;
            sharedChildAccumulator += sharedChildCount / thisDistance;
            unidirectionalIndirectAccumulator += outboundCount / thisDistance;
            bidirectionalIndirectAccumulator += (outboundCount + inboundCount) / thisDistance;
            
            // JP: we should figure out if and how weighting by distance affects
            // how this works and also if the sum or the average may work better?
            // TODO:
            relatednessAccumulator += relatedness / thisDistance;

            /*System.out.println("Counts: " + directRelationCount
             + ", " + reverseRelationCount
             + ", " + sharedParentCount
             + ", " + sharedChildCount
             + ", " + outboundCount
             + ", " + inboundCount
             );

             System.out.println("Accumulators: " + unidirectionalAccumulator
             + ", " + bidirectionalAccumulator
             + ", " + sharedParentAccumulator
             + ", " + sharedChildAccumulator
             + ", " + unidirectionalIndirectAccumulator
             + ", " + bidirectionalIndirectAccumulator
             );
             */
          }

          origent.putFeatureFloat(inst, outputFeaturePrefix + "UniDirect",
                  unidirectionalAccumulator);
          origent.putFeatureFloat(inst, outputFeaturePrefix + "BiDirect",
                  bidirectionalAccumulator);
          origent.putFeatureFloat(inst, outputFeaturePrefix + "SharedParent",
                  sharedParentAccumulator);
          origent.putFeatureFloat(inst, outputFeaturePrefix + "SharedChild",
                  sharedChildAccumulator);
          origent.putFeatureFloat(inst, outputFeaturePrefix + "UniIndirect",
                  unidirectionalIndirectAccumulator);
          origent.putFeatureFloat(inst, outputFeaturePrefix + "BiIndirect",
                  bidirectionalIndirectAccumulator);
          origent.putFeatureFloat(inst, outputFeaturePrefix + "Combined",
                  bidirectionalAccumulator + 0.25F * (bidirectionalIndirectAccumulator
                  + sharedParentAccumulator + sharedChildAccumulator));
          origent.putFeatureFloat(inst, outputFeaturePrefix + "Relatedness",
                  relatednessAccumulator);
        }
      }
    }

    ents = null;

    long end = System.currentTimeMillis();
    System.out.println("Structure Sim:" + (end - start));
  }

  // returns the elapsed Time in millisecods since the time passed as parameter
  private long elapsedTime(long startMillis) {
    return (System.currentTimeMillis()-startMillis);
  }
  
  private int getDirectRelationCount(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();
    int count = fastGraphLR.getDirectRelationCount(fromInst,toInst);
    benchmarkCheckpoint(startTime, "__directRelations");
    //logger.debug("StructuralSimilarityPRv3\tValue:directRelations\t"+fromInst+"\t"+toInst+"\t"+count);
    //logger.debug("StructuralSimilarityPRv3\tdirectRelations\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
    return count;
  }
  
  private int getSharedParentRelationCount(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();
    int count = fastGraphLR.getSharedParentRelationCount(fromInst,toInst);
    benchmarkCheckpoint(startTime, "__sharedParent");
    //logger.debug("StructuralSimilarityPRv3\tsharedParent\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
    return count;
  }

  private int getSharedChildRelationCount(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();
    int count = fastGraphLR.getSharedChildRelationCount(fromInst, toInst);
    benchmarkCheckpoint(startTime, "__sharedChild");
    //logger.debug("StructuralSimilarityPRv3\tsharedChild\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
    return count;
  }

  private int getSequenceRelationCount(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();
    int count = fastGraphLR.getSequenceRelationCount(fromInst, toInst);
    benchmarkCheckpoint(startTime, "__indirectSequence");
    //logger.debug("StructuralSimilarityPRv2\tindirectSequence\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
    return count;
  }

  // Relatedness: calculated as described in 
  // Milne,Witten 2008: An effective, low-cost measure of semantic relatedness
  // obtained from Wikipedia links.
  // 
  // For instances a and b, incoming(a) the set of incoming links to page a,
  // union, intersection being the set-union and intersection:
  // A = incoming(a)
  // B = incoming(b)
  // relatedness(a,b) = 
  // (log(max(|A|,|B|))-log(|intersection(A,B)|)) / (log(|W|)-log(min(|A|,|B|)))
  // In our case, we did not actually count the distinct incoming links, but
  // we approximate |W| with a number which is likely to be much bigger.
  // Since this is a constant term and we are only interested in the ranking
  // by this score, the concrete number does not really matter.
  static final private double bigW = 10000000.0F;
  static final double logBigW = (float)Math.log(bigW);
  private float getRelatedness(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();
    float ret = 0.0F;
    // get the set of incoming links for both the instances
    String incomingString = null;  // the db contains the resource names, separated by |
    String inst = null;
    Set<String> incoming1 = new HashSet<String>();
    Set<String> incoming2 = new HashSet<String>();
    try {
      inst = fromInst;
      stSelect.setString(1, inst);
      ResultSet results = stSelect.executeQuery();
      if (results.next()) {  // we found something
        incomingString = results.getString(1);
        for(String resource : incomingString.split("\\|")) {
          incoming1.add(resource);
        }
      }
      results.close();

      inst = toInst;
      stSelect.setString(1, inst);
      results = stSelect.executeQuery();
      if (results.next()) {  // we found something
        incomingString = results.getString(1);
        for(String resource : incomingString.split("\\|")) {
          incoming2.add(resource);
        }
      }
      results.close();
      
    } catch (Exception e) {
      throw new GateRuntimeException("Could not retrieve incoming links for "+fromInst,e);
    }
    //logger.debug("StructuralSimilarityPRv3\tValue:relatednes:incoming1\t"+fromInst+"\t"+toInst+"\t"+incoming1.size());
    //logger.debug("StructuralSimilarityPRv3\tValue:relatednes:incoming2\t"+fromInst+"\t"+toInst+"\t"+incoming2.size());
    if(incoming1.size() == 0 || incoming2.size() == 0) {
      // just use ret=0.0F
    } else {
      // make sure incoming1 is the smaller set of the two
      int maxSize;
      int minSize;
      int intSize = 0; // size of the intersection set
      if (incoming1.size() > incoming2.size()) {
        Set<String> tmp = incoming1;
        incoming1 = incoming2;
        incoming2 = tmp;
        //maxSize = incoming1.size();
        //minSize = incoming2.size();
      }//else {
        maxSize = incoming2.size();
        minSize = incoming1.size();
      //}
      // calculate the size of the intersection
      for (String resource : incoming1) {
        if (incoming2.contains(resource)) {
          intSize++;
        }
      }
      
      if(intSize==0){
    	  //just use ret=0.0F
      } else {
    	  ret = (float) ((float) ((Math.log(maxSize) - Math.log(intSize))) / (logBigW - Math.log(minSize)));
      }
    }
    benchmarkCheckpoint(startTime, "__relatedness");
    //logger.debug("StructuralSimilarityPRv3\tValue:relatedness\t"+fromInst+"\t"+toInst+"\t"+ret);
    //logger.debug("StructuralSimilarityPRv3\trelatedness\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));    
    return ret;
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


  @CreoleParameter(defaultValue = "10")
  @RunTime
  @Optional
  public void setContextChars(Integer maxCont) {
    this.contextChars = maxCont.intValue();
  }
  public Integer getContextChars() {
    return Integer.valueOf(contextChars);
  }
  private int contextChars;

  @CreoleParameter
  public void setFastGraphLR(FastGraphLR fastGraphLR) {
    this.fastGraphLR = fastGraphLR;
  }
  public FastGraphLR getFastGraphLR() {
    return this.fastGraphLR;
  }
  private FastGraphLR fastGraphLR;
  
  @CreoleParameter
  @RunTime
  @Optional
  public void setWikiLinksDbLR(JdbcLR lr) {
    // we have to re-initialize the prepared statement whenever this is called
    // with a non-null argument AND the connection we get from it is different
    // from our previous connection (which may be null, if we check the first
    // time).
    if(lr != null && lr.getConnection() != connection) {
      wikiLinksDbLR = lr;
      connection = wikiLinksDbLR.getConnection();
      initConnection();
    } else {
      wikiLinksDbLR = lr;
    }
  }
  public JdbcLR getWikiLinksDbLR() {
    return wikiLinksDbLR;
  }
  private JdbcLR wikiLinksDbLR = null;
  

  @CreoleParameter(defaultValue = "structSimV2")
  @RunTime
  @Optional
  public void setoutputFeaturePrefix(String outputFeaturePrefix) {
    this.outputFeaturePrefix = outputFeaturePrefix;
  }
  public String getoutputFeaturePrefix() {
    return this.outputFeaturePrefix;
  }
  private String outputFeaturePrefix;

  public Boolean getUseTwitterExpansion() {
    return this.useTwitterExpansion;
  }

  @RunTime
  @CreoleParameter(defaultValue = "true")
  public void setUseTwitterExpansion(Boolean useTwitterExpansion) {
    this.useTwitterExpansion = useTwitterExpansion;
  }

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

} // class StructuralSimilarityPRv2
