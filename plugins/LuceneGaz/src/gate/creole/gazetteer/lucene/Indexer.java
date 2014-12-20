package gate.creole.gazetteer.lucene;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;

/**
 * The indexer class, responsible for indexing data.
 * 
 * @author niraj
 */
public class Indexer {

  /**
   * Index writer object
   */
  private IndexWriter indexWriter;

  /**
   * fields to be indexed and stored
   */
  private List<FieldInfo> fields;

  /**
   * should index be caseSensitive?
   */
  private boolean caseSensitive;
  
  /**
   * Constructor
   * 
   * @param indexDir
   * @param information
   *          about the fields to be index
   */
  public Indexer(File indexDir, List<FieldInfo> fields, boolean caseSensitive) throws IndexException {

    // fields
    this.fields = fields;
    
    this.caseSensitive = caseSensitive;

    // sanity check on provided fields
    // there must be at least one field with indexing set to true
    // there must be only one field set as default searchable field
    boolean defaultSearchFieldSet = false;

    for(FieldInfo fi : fields) {
      if(fi.isDefaultSearchField()) {
        if(defaultSearchFieldSet) {
          throw new IndexException(
              "Only one field can be be set as default search field.");
        } else {
          if(!fi.isIndexed()) { throw new IndexException(
              "The field "
                  + fi.getName()
                  + " is not indexable and therefore cannot be set as default search field"); }

          defaultSearchFieldSet = true;
        }
      }
    }

    // is there a default search field available?
    if(!defaultSearchFieldSet) {
      throw new IndexException(
          "You must choose one and only one field as default searchable field");
    }

    // if the directory does not exist, create one
    if(!indexDir.exists()) {
      indexDir.mkdirs();
    } else {
      // is there anything inside, if so throw an exception
      if(indexDir.listFiles().length > 0) { throw new IndexException(
          "The directory " + indexDir.getAbsolutePath() + " is not empty!"); }
    }

    Analyzer analyzer = new WhitespaceAnalyzer(Version.LUCENE_35);
    if(!caseSensitive) {
    	analyzer = new StandardAnalyzer(
                Version.LUCENE_35);
    }
    // the configuration
    IndexWriterConfig config =
        new IndexWriterConfig(Version.LUCENE_35, analyzer);

    // lucene index directory
    File luceneIndex = new File(indexDir, Constants.INDEX_FOLDER_NAME);

    // create the index writer object
    try {
      indexWriter = new IndexWriter(new SimpleFSDirectory(luceneIndex), config);
    } catch(Exception e) {
      throw new IndexException(e);
    }

    // write the field info to the config file
    File configFile = new File(indexDir, Constants.INDEX_CONFIG_FILE_NAME);
    BufferedWriter bw = null;
    try {
      bw =
          new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
              configFile), "UTF-8"));
      bw.write(""+caseSensitive);
      bw.newLine();
      for(FieldInfo fi : fields) {
        bw.write(fi.getName() + "\t" + fi.isIndexed() + "\t" + fi.isStored()
            + "\t" + fi.isDefaultSearchField());
        bw.newLine();
      }
    } catch(Exception e) {
      throw new IndexException(e);
    } finally {
      if(bw != null) {
        try {
          bw.close();
        } catch(IOException e) {
          throw new IndexException(e);
        }
      }
    }
  }

  /**
   * One line indexed as one document. The delimiter is used to separate fields.
   * 
   * @param fileToIndex
   * @param encoding
   * @param delimeter
   * @throws IndexException
   */
  public void index(File fileToIndex, String encoding, String delimeter)
      throws IndexException {

    // reader
    BufferedReader br = null;

    try {

      // initialize reader
      br =
          new BufferedReader(new InputStreamReader(new FileInputStream(
              fileToIndex), encoding));

      // reading one line at a time
      int lineCounter = 0;

      String line = null;
      while((line = br.readLine()) != null) {
        // keeping track of line numbers
        lineCounter++;

        // remove spaces
        line = line.trim();
        
        //line = java.net.URLDecoder.decode(line, "UTF-8");

        // is it a comment?
        if(line.length() == 0 || line.startsWith("#") || line.startsWith("//"))
          continue;

        // split values
        String[] values = line.split(delimeter);

        // values must match number of fields
        if(values.length != fields.size()) {
          System.err.println("skipping line " + lineCounter
              + " as number of values (" + values.length
              + ") differ from the number of fields (" + fields.size() + ")");
          continue;
        }

        // both are same
        // one field at a time
        Document aDoc = new Document();
        for(int i = 0; i < values.length; i++) {
          FieldInfo fi = fields.get(i);
          String value = Constants.FIELD_START_MARKUP + " " + values[i] + " " + Constants.FIELD_END_MARKUP;
          if(fi.isIndexed() && fi.isStored()) {
            aDoc.add(new Field(fi.getName(), value , Field.Store.YES,
                Field.Index.ANALYZED));
          } else if(fi.isIndexed()) {
            aDoc.add(new Field(fi.getName(), value, Field.Store.NO,
                Field.Index.ANALYZED));
          } else if(fi.isStored()) {
            aDoc.add(new Field(fi.getName(), value, Field.Store.YES,
                Field.Index.NO));
          } else {
            // skip this field
          }
        }
        indexWriter.addDocument(aDoc);
        
        // commit after every 100,000 lines
        if(lineCounter % 200000 == 0) {
        	System.out.println("commiting changes at line:"+lineCounter);
        	indexWriter.commit();
        }
      }
    } catch(Exception e) {
      throw new IndexException(e);
    } finally {
      if(br != null) try {
        br.close();
      } catch(IOException e) {
        throw new IndexException(e);
      }
    }
  }

  public static void addOneEntry(String indexDir, String caption, String inst, boolean caseSensitive) throws Exception {

    Analyzer analyzer = new WhitespaceAnalyzer(Version.LUCENE_35);
    if(!caseSensitive) {
        analyzer = new StandardAnalyzer(Version.LUCENE_35);
    }
    // the configuration
    IndexWriterConfig config =
        new IndexWriterConfig(Version.LUCENE_35, analyzer);

    // lucene index directory
    File luceneIndex = new File(indexDir, Constants.INDEX_FOLDER_NAME);

    // create the index writer object
    IndexWriter indexWriter = new IndexWriter(new SimpleFSDirectory(luceneIndex), config);

    // both are same
    // one field at a time
    Document aDoc = new Document();
    String value = Constants.FIELD_START_MARKUP + " " + caption + " " + Constants.FIELD_END_MARKUP;
    aDoc.add(new Field("caption", value , Field.Store.YES, Field.Index.ANALYZED));

    value = Constants.FIELD_START_MARKUP + " " + inst + " " + Constants.FIELD_END_MARKUP;
    aDoc.add(new Field("inst", value, Field.Store.YES, Field.Index.NO));
    indexWriter.addDocument(aDoc);
    indexWriter.commit(); indexWriter.close();
  }

  /**
   * close down the index
   * 
   * @throws IndexException
   */
  public void close() throws IndexException {
    if(indexWriter != null) {
      try {
        indexWriter.commit();
        indexWriter.close();
      } catch(Exception e) {
        throw new IndexException(e);
      }
    }
  }

  public static void main1(String [] args) throws Exception {
    addOneEntry(args[0], args[1], args[2], false);
  }

  public static void main(String[] args) throws Exception {

    // field info
    List<FieldInfo> fields = new ArrayList<FieldInfo>();

    // fieldname, index, store, set as a default search field
    fields.add(new FieldInfo("inst", true, false, true));    
    fields.add(new FieldInfo("class", false, true, false));
    //fields.add(new FieldInfo("inst2", false, true, false));
    //fields.add(new FieldInfo("longitude", false, true, false));
    //fields.add(new FieldInfo("count", false, true, false));
    //fields.add(new FieldInfo("class", false, true, false));
    //fields.add(new FieldInfo("type", false, true, false));

    // take from user
    File indexDir = new File(args[0]);

    // the indexer object
    Indexer indexer = new Indexer(indexDir, fields, true);
    indexer.index(new File(args[1]), "UTF-8", "\t");
    indexer.close();
  }
}
