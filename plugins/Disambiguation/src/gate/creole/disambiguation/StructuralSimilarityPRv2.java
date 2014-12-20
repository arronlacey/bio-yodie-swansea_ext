package gate.creole.disambiguation;

import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
//import gate.creole.gazetteer.lucene.Hit;
//import gate.creole.gazetteer.lucene.IndexException;
//import gate.creole.gazetteer.lucene.Searcher;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.Benchmark;
import gate.util.Benchmarkable;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import org.apache.log4j.Logger;

/**
 * This class is the implementation of the resource DISAMBIGUATE PR.
 */
@CreoleResource(name = "Structural Similarity PR V2", comment = "finds relations between the sem annotations")
public class StructuralSimilarityPRv2 extends AbstractLanguageAnalyser implements
        ProcessingResource, Benchmarkable {

  /**
   * serial version id
   */
  private static final long serialVersionUID = 1632318208705298081L;

  private Logger logger = Logger.getLogger(gate.creole.disambiguation.StructuralSimilarityPRv2.class);  
  
  private class CacheMode {

    static final int UNSET = -1;
    static final int FALSE = 0;
    static final int TRUE = 1;
  }
  /**
   * List of the annotation types to be used for lookup.
   */
  private List<String> annotationTypes;
  /**
   * name of the input annotation set
   */
  private String inputASName;
  /**
   * Number of tokens in either direction to consider as context
   */
  private int numContextLookups;
  /**
   * Absolute maximum number of context annotations to use.
   */
  private int maximumContextLookups;
  /**
   * Reject parent queries with higher incounts than this.
   */
  private int maxIncount;
  /**
   * Name of the DB
   */
  private String dbName;
  /**
   * Location of the database.
   */
  private URL databaseDirectory;
  /**
   * Name of the output feature
   */
  private String outputFeaturePrefix;
  /**
   * Whether or not to use coreference information if available.
   */
  ;;private boolean useCoreference;
  private boolean useTwitterExpansion;
  private int useCaching;
  private int currentUseCaching = CacheMode.UNSET;
  private PreparedStatement inCountStatement;
  private PreparedStatement relCountStatement;
  private PreparedStatement sharedParentStatement;
  private PreparedStatement sharedChildStatement;
  private PreparedStatement relSequenceStatement;
  private PreparedStatement cacheSharedParent;
  private PreparedStatement cacheSharedChild;
  private PreparedStatement cacheRelSequence;
  private PreparedStatement fromCacheSharedParent;
  private PreparedStatement fromCacheSharedChild;
  private PreparedStatement fromCacheRelSequence;
  private Connection connection;
  private String jdbcUrl;
  private static String namespace = "http://dbpedia.org/resource/";

  /**
   * Initialise this resource, and return it.
   */
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
    long start = System.currentTimeMillis();

    try {
      this.dBSetup();
    } catch (Exception e) {
      e.printStackTrace();
    }


    DocumentEntitySet ents = new DocumentEntitySet(document, inputASName,
            annotationTypes, useCoreference, "LodieCoref");


    Iterator<Entity> origentit = null;

    if (document.getFeatures().get("lodie.scoring.keyOverlapsOnly") != null
            && document.getFeatures().get("lodie.scoring.keyOverlapsOnly")
            .toString().equals("true")) {
      origentit = ents.getKeyOverlapsIterator(document);
    } else {
      //Tweet span iterator will figure out for itself if this is
      //an expanded tweet. If it is, it just returns an iterator
      //over entities that feature in the tweet body. Otherwise, all 
      //of them.
      origentit = ents.getTweetSpanIterator(document);
    }


    while (origentit != null && origentit.hasNext()) { //For each entity
      Entity origent = origentit.next();

      //Get the candidates
      Set<String> origcands = origent.getInstSet();

      //Get proximal entities
      SortedMap<Long, Entity> proxents =
              ents.getProximalEntities(origent, maximumContextLookups, useTwitterExpansion);
      Iterator<Entity> proxentit = null;
      if (proxents != null) {
        proxentit = proxents.values().iterator();
      }

      while (proxentit != null && proxentit.hasNext()) {
        Entity proxent = proxentit.next();

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
    int count = 0;
    try {

      relCountStatement.setString(1, fromInst);
      relCountStatement.setString(2, toInst);
      ResultSet results = relCountStatement.executeQuery();

      if (results.next()) {  // we found something
        count = results.getInt("relationCount");
      }

      results.close();
    } catch (Exception e) {
      e.printStackTrace();
      //System.exit(1);
    }
    benchmarkCheckpoint(startTime, "__directRelations");
    logger.debug("StructuralSimilarityPRv2\tdirectRelations\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
    return count;
  }

  private int getSharedParentRelationCount(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();

    //Shared parent is potentially very slow the first time.
    //Optionally skip it completely for insts with high incounts.
    float count1 = 0.0F;
    float count2 = 0.0F;
    try {
      inCountStatement.setString(1, fromInst);
      ResultSet incountresults1 = inCountStatement.executeQuery();
      if (incountresults1.next()) {  // we found something
        count1 = incountresults1.getFloat("INCOUNT");
      }
      incountresults1.close();

      inCountStatement.setString(1, toInst);
      ResultSet incountresults2 = inCountStatement.executeQuery();
      if (incountresults2.next()) {  // we found something
        count2 = incountresults2.getFloat("INCOUNT");
      }
      incountresults2.close();
    } catch (Exception e) {
      e.printStackTrace();
      //System.exit(1);
    }

    if (count1 > this.maxIncount || count2 > this.maxIncount) {
      benchmarkCheckpoint(startTime, "__sharedParent");
      logger.debug("StructuralSimilarityPRv2\tsharedParent:SKIPPED\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return 0;
    }

    int dbCacheSharedParentRelationCount = -1;
    int tableJoinCount = -1;

    try {
      //Alphabetical order in the DB cache
      if (fromInst.compareTo(toInst) < 0) {
        fromCacheSharedParent.setString(1, fromInst);
        fromCacheSharedParent.setString(2, toInst);
      } else {
        fromCacheSharedParent.setString(1, toInst);
        fromCacheSharedParent.setString(2, fromInst);
      }
      ResultSet cacheresults = fromCacheSharedParent.executeQuery();

      if (cacheresults.next()) {  // we found something
        dbCacheSharedParentRelationCount = cacheresults.getInt("COUNT");
      }
      cacheresults.close();
    } catch (Exception e) {
      e.printStackTrace();
      //System.exit(1);
    }


    if (dbCacheSharedParentRelationCount == -1.0F) {
      //If all else fails, construct the relation from relationcounts table
      try {

        sharedParentStatement.setString(1, fromInst);
        sharedParentStatement.setString(2, toInst);
        ResultSet relcountresults = sharedParentStatement.executeQuery();

        if (relcountresults.next()) {  // we found something
          tableJoinCount = relcountresults.getInt("count1p");
          tableJoinCount += relcountresults.getInt("count2p");
        }

        relcountresults.close();
      } catch (Exception e) {
        e.printStackTrace();
        //System.exit(1);
      }
    } else {
      benchmarkCheckpoint(startTime, "__sharedParent");
      logger.debug("StructuralSimilarityPRv2\tsharedParent:FOUNDINCACHE\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return dbCacheSharedParentRelationCount;
    }

    //If we made it this far we are relying on the relationcounts result
    //Cache this result so we don't have to do that again
    if (useCaching == CacheMode.TRUE) {
      try {
        if (fromInst.compareTo(toInst) < 0) {
          cacheSharedParent.setString(1, fromInst);
          cacheSharedParent.setString(2, toInst);
        } else {
          cacheSharedParent.setString(1, toInst);
          cacheSharedParent.setString(2, fromInst);
        }
        if (tableJoinCount == -1.0F) {
          cacheSharedParent.setInt(3, 0);
        } else {
          cacheSharedParent.setInt(3, tableJoinCount);
        }
        cacheSharedParent.executeUpdate();
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    if (tableJoinCount == -1) {
      benchmarkCheckpoint(startTime, "__sharedParent");
      logger.debug("StructuralSimilarityPRv2\tsharedParent:NOTFOUND\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return 0;
    } else {
      benchmarkCheckpoint(startTime, "__sharedParent");
      logger.debug("StructuralSimilarityPRv2\tsharedParent:FOUND\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return tableJoinCount;
    }

  }

  private int getSharedChildRelationCount(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();

    int dbCacheSharedChildRelationCount = -1;
    int tableJoinCount = -1;

    try {
      //Alphabetical order in the DB cache
      if (fromInst.compareTo(toInst) < 0) {
        fromCacheSharedChild.setString(1, fromInst);
        fromCacheSharedChild.setString(2, toInst);
      } else {
        fromCacheSharedChild.setString(1, toInst);
        fromCacheSharedChild.setString(2, fromInst);
      }
      ResultSet cacheresults = fromCacheSharedChild.executeQuery();

      if (cacheresults.next()) {  // we found something
        dbCacheSharedChildRelationCount = cacheresults.getInt("COUNT");
      }
      cacheresults.close();
    } catch (Exception e) {
      e.printStackTrace();
      //System.exit(1);
    }

    if (dbCacheSharedChildRelationCount == -1) {
      //If all else fails, construct the relation from relationcounts table
      try {
        sharedChildStatement.setString(1, fromInst);
        sharedChildStatement.setString(2, toInst);
        ResultSet relcountresults = sharedChildStatement.executeQuery();

        if (relcountresults.next()) {  // we found something
          tableJoinCount = relcountresults.getInt("count1c");
          tableJoinCount += relcountresults.getInt("count2c");
        }

        relcountresults.close();
      } catch (Exception e) {
        e.printStackTrace();
        //System.exit(1);
      }
    } else {
      benchmarkCheckpoint(startTime, "__sharedChild");
      logger.debug("StructuralSimilarityPRv2\tsharedChild:FOUNDINCACHE\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return dbCacheSharedChildRelationCount;
    }

    //If we made it this far we are relying on the relationcounts result
    //Cache this result so we don't have to do that again
    if (useCaching == CacheMode.TRUE) {
      try {
        if (fromInst.compareTo(toInst) < 0) {
          cacheSharedChild.setString(1, fromInst);
          cacheSharedChild.setString(2, toInst);
        } else {
          cacheSharedChild.setString(1, toInst);
          cacheSharedChild.setString(2, fromInst);
        }
        if (tableJoinCount == -1) {
          cacheSharedChild.setInt(3, 0);
        } else {
          cacheSharedChild.setInt(3, tableJoinCount);
        }
        cacheSharedChild.executeUpdate();
      } catch (Exception e) {
        e.printStackTrace();
        //System.exit(1);
      }
    }

    if (tableJoinCount == -1) {
      logger.debug("StructuralSimilarityPRv2\tsharedChild:NOTFOUND\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      benchmarkCheckpoint(startTime, "__sharedChild");
      return 0;
    } else {
      benchmarkCheckpoint(startTime, "__sharedChild");
      logger.debug("StructuralSimilarityPRv2\tsharedChild:FOUND\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return tableJoinCount;
    }
  }

  private int getSequenceRelationCount(String fromInst, String toInst) {
    long startTime = Benchmark.startPoint();

    int dbCacheIndirectSequenceRelationCount = -1;
    int tableJoinCount = -1;

    try {
      fromCacheRelSequence.setString(1, fromInst);
      fromCacheRelSequence.setString(2, toInst);
      ResultSet cacheresults = fromCacheRelSequence.executeQuery();

      if (cacheresults.next()) {  // we found something
        dbCacheIndirectSequenceRelationCount = cacheresults.getInt("COUNT");
      }
      cacheresults.close();
    } catch (Exception e) {
      e.printStackTrace();
      //System.exit(1);
    }

    if (dbCacheIndirectSequenceRelationCount == -1) {
      //If all else fails, construct the relation from relationcounts table
      try {
        relSequenceStatement.setString(1, fromInst);
        relSequenceStatement.setString(2, toInst);
        ResultSet relseqresults = relSequenceStatement.executeQuery();

        if (relseqresults.next()) {  // we found something
          tableJoinCount = relseqresults.getInt("count1s");
          tableJoinCount += relseqresults.getInt("count2s");
        }
        relseqresults.close();
      } catch (Exception e) {
        e.printStackTrace();
        //System.exit(1);
      }
    } else {
      benchmarkCheckpoint(startTime, "__indirectSequence");
      logger.debug("StructuralSimilarityPRv2\tindirectSequence:FOUNDINCACHE\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return dbCacheIndirectSequenceRelationCount;
    }

    //If we made it this far we are relying on the relationcounts result
    //Cache this result so we don't have to do that again
    if (useCaching == CacheMode.TRUE) {
      try {
        cacheRelSequence.setString(1, fromInst);
        cacheRelSequence.setString(2, toInst);
        if (tableJoinCount == -1) {
          cacheRelSequence.setInt(3, 0);
        } else {
          cacheRelSequence.setInt(3, tableJoinCount);
        }
        cacheRelSequence.executeUpdate();
      } catch (Exception e) {
        e.printStackTrace();
        //System.exit(1);
      }
    }

    if (tableJoinCount == -1) {
      benchmarkCheckpoint(startTime, "__indirectSequence");
      logger.debug("StructuralSimilarityPRv2\tindirectSequence:NOTFOUND\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return 0;
    } else {
      benchmarkCheckpoint(startTime, "__indirectSequence");
      logger.debug("StructuralSimilarityPRv2\tindirectSequence:FOUND\t"+fromInst+"\t"+toInst+"\t"+elapsedTime(startTime));
      return tableJoinCount;
    }
  }

  private void dBSetup() throws ResourceInstantiationException {

    if (useCaching != currentUseCaching) {

      try {
        if (useCaching == CacheMode.TRUE) {
          jdbcUrl = "jdbc:h2:"
                  + databaseDirectory.getPath()
                  + this.dbName
                  + ";ACCESS_MODE_DATA=rw;CACHE_SIZE=1000000";
        } else {
          jdbcUrl = "jdbc:h2:"
                  + databaseDirectory.getPath()
                  + this.dbName
                  + ";ACCESS_MODE_DATA=r;CACHE_SIZE=1000000";
        }
        System.out.println(jdbcUrl);
      } catch (Exception e) {
        throw new ResourceInstantiationException(
                "Failed to make database URL.", e);
      }

      try {
        Class.forName("org.h2.Driver");
        connection = DriverManager.getConnection(jdbcUrl, "", "");
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

      } catch (Exception e) {
        throw new ResourceInstantiationException(
                "Failed to connect to the database.", e);
      }

      try {
        //Check how many things point to this URI
        inCountStatement = connection.prepareStatement(
                "SELECT INCOUNT FROM URIINCOUNTS "
                + "WHERE URI = ?");

        //Simple direct relation count between two URIs
        relCountStatement = connection.prepareStatement(
                "SELECT relationCount FROM relationCounts "
                + "WHERE uriFrom = ? AND uriTo = ?");

        //Two URIs are both pointed to by the same intermediary
        sharedParentStatement = connection.prepareStatement(
                "SELECT "
                + "SUM(t1.relationCount) as count1p, "
                + "SUM(t2.relationCount) as count2p "
                + "FROM relationcounts as t1, relationcounts as t2 "
                + "WHERE "
                + "t1.uriTo = ? AND "
                + "t2.uriTo = ? AND "
                + "t1.uriFrom = t2.uriFrom");

        //Two URIs both point to the same intermediary
        sharedChildStatement = connection.prepareStatement(
                "SELECT "
                + "SUM(t1.relationCount) as count1c, "
                + "SUM(t2.relationCount) as count2c "
                + "FROM relationcounts as t1, relationcounts as t2 "
                + "WHERE "
                + "t1.uriFrom = ? AND "
                + "t2.uriFrom = ? AND "
                + "t1.uriTo = t2.uriTo");

        //URI links to an intermediary which in turn links to another URI
        relSequenceStatement = connection.prepareStatement(
                "SELECT "
                + "SUM(t1.relationCount) as count1s, "
                + "SUM(t2.relationCount) as count2s "
                + "FROM relationcounts as t1, relationcounts as t2 "
                + "WHERE "
                + "t1.uriFrom = ? AND "
                + "t1.uriTo = t2.uriFrom AND "
                + "t2.uriTo = ?");

        //Some statements for inserting items into the cache

        fromCacheSharedParent = connection.prepareStatement(
                "SELECT COUNT FROM SHAREDPARENT WHERE URI1 = ? AND URI2 = ?;");

        fromCacheSharedChild = connection.prepareStatement(
                "SELECT COUNT FROM SHAREDCHILD WHERE URI1 = ? AND URI2 = ?;");

        fromCacheRelSequence = connection.prepareStatement(
                "SELECT COUNT FROM INDIRECTSEQ WHERE URIFROM = ? AND URITO = ?;");

        cacheSharedParent = connection.prepareStatement(
                "INSERT INTO SHAREDPARENT (URI1, URI2, COUNT) VALUES(?, ?, ?);");

        cacheSharedChild = connection.prepareStatement(
                "INSERT INTO SHAREDCHILD (URI1, URI2, COUNT) VALUES(?, ?, ?);");

        cacheRelSequence = connection.prepareStatement(
                "INSERT INTO INDIRECTSEQ (URIFROM, URITO, COUNT) VALUES(?, ?, ?);");

      } catch (Exception e) {
        throw new ResourceInstantiationException(
                "Failed to prepare statement.", e);
      }

      currentUseCaching = useCaching;

    }
  }

  /**
   * The cleanup method
   */
  @Override
  public void cleanup() {
    super.cleanup();
    if (connection != null) {
      try {
        connection.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
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

  public Integer getContextTokens() {
    return Integer.valueOf(numContextLookups);
  }

  @CreoleParameter(defaultValue = "10")
  @RunTime
  @Optional
  public void setContextTokens(Integer conttok) {
    this.numContextLookups = conttok.intValue();
  }

  public Integer getMaximumContextLookups() {
    return Integer.valueOf(maximumContextLookups);
  }

  @CreoleParameter(defaultValue = "10")
  @RunTime
  @Optional
  public void setMaximumContextLookups(Integer maxCont) {
    this.maximumContextLookups = maxCont.intValue();
  }

  public Integer getMaxIncount() {
    return Integer.valueOf(this.maxIncount);
  }

  @CreoleParameter(defaultValue = "10000000")
  @RunTime
  @Optional
  public void setMaxIncount(Integer mi) {
    this.maxIncount = mi.intValue();
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

  public URL getdatabaseDirectory() {
    return this.databaseDirectory;
  }

  @CreoleParameter
  public void setdatabaseDirectory(URL databaseDirectory) {
    this.databaseDirectory = databaseDirectory;
  }

  public String getdbName() {
    return this.dbName;
  }

  @CreoleParameter(defaultValue = "relations")
  public void setdbName(String dbName) {
    this.dbName = dbName;
  }

  public String getoutputFeaturePrefix() {
    return this.outputFeaturePrefix;
  }

  @CreoleParameter(defaultValue = "structSimV2")
  @RunTime
  @Optional
  public void setoutputFeaturePrefix(String outputFeaturePrefix) {
    this.outputFeaturePrefix = outputFeaturePrefix;
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

  public Boolean getuseCaching() {
    if (this.useCaching == CacheMode.TRUE) {
      return new Boolean(true);
    } else {
      return new Boolean(false);
    }
  }

  @CreoleParameter(defaultValue = "true")
  @RunTime
  @Optional
  public void setuseCaching(Boolean useCaching) {
    if (useCaching.booleanValue() == true) {
      this.useCaching = CacheMode.TRUE;
    } else {
      this.useCaching = CacheMode.FALSE;
    }
  }

  public Boolean getUseTwitterExpansion() {
    return this.useTwitterExpansion;
  }

  @RunTime
  @CreoleParameter(defaultValue = "true")
  public void setUseTwitterExpansion(Boolean useTwitterExpansion) {
    this.useTwitterExpansion = useTwitterExpansion;
  }

  public List<String> getAnnotationTypes() {
    return annotationTypes;
  }

  @RunTime
  @CreoleParameter(defaultValue = "Lookup")
  public void setAnnotationTypes(List<String> annotationTypes) {
    this.annotationTypes = annotationTypes;
  }
} // class StructuralSimilarityPRv2
