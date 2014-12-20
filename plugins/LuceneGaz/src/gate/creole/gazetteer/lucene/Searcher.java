package gate.creole.gazetteer.lucene;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.queryParser.standard.QueryParserUtil;

public class Searcher {

	/**
	 * The searcher object
	 */
	private IndexSearcher searcher;

	/**
	 * fields
	 */
	private Map<String, FieldInfo> fields;

	/**
	 * name of the default search field
	 */
	private String defaultSearchFieldName;

	/**
	 * the index reader object
	 */
	private IndexReader reader;

	private boolean caseSensitive = false;

	/**
	 * constructor
	 * 
	 * @param indexDir
	 */
	public Searcher(File indexDir) throws IndexException {
		// read the indexDirectory and obtain information about the index
		// configuration
		File configFile = new File(indexDir, Constants.INDEX_CONFIG_FILE_NAME);

		// does the file exist
		if (!configFile.exists()) {
			throw new IndexException("The config file "
					+ configFile.getAbsolutePath() + " does not exist");
		}

		// lucene index folder?
		File indexFolder = new File(indexDir, Constants.INDEX_FOLDER_NAME);
		if (!indexFolder.exists() || indexFolder.listFiles().length == 0) {
			throw new IndexException("The lucene index folder "
					+ indexFolder.getAbsolutePath()
					+ " does not exist or does not contain valid index files");
		}

		// read the fields information

		// inititlise searcher but read index in the read only mode only
		try {
			reader = IndexReader.open(new SimpleFSDirectory(indexFolder), true);
			searcher = new IndexSearcher(reader);
		} catch (Exception e) {
			throw new IndexException(e);
		}

		// read the configuration file and obtain the field that user had set as
		// searchable
		fields = new HashMap<String, FieldInfo>();
		BufferedReader br = null;

		// initialize reader
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(
					configFile), "UTF-8"));

			String line = null;

			// keeping track of line numbers
			int lineCounter = 0;

			while ((line = br.readLine()) != null) {
				lineCounter++;

				// remove spaces
				line = line.trim();

				// is it a comment?
				if (line.length() == 0 || line.startsWith("#")
						|| line.startsWith("//"))
					continue;

				// thre must be four columns
				String cols[] = line.split("\t");

				if (cols.length == 1) {
					caseSensitive = Boolean.parseBoolean(cols[0]);
					continue;
				}

				if (cols.length != 4) {
					throw new IndexException("Not enough content on line "
							+ lineCounter);
				}

				// is it default searchField?
				boolean defaultSearchField = Boolean.parseBoolean(cols[3]);
				if (defaultSearchField)
					defaultSearchFieldName = cols[0];

				fields.put(cols[0],
						new FieldInfo(cols[0], Boolean.parseBoolean(cols[1]),
								Boolean.parseBoolean(cols[2]),
								defaultSearchField));
			}
		} catch (Exception e) {
			throw new IndexException(e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					throw new IndexException(e);
				}
			}
		}
	}

	public List<Hit> searchTerms(String searchInField, Set<String> termsToSearch, Set<String> fieldsToReturn, int noOfHits) throws IndexException {
	  
	   // to return
    List<Hit> toReturn = new ArrayList<Hit>();

    // has user provided the field to search in?
    if (searchInField == null) {
      searchInField = defaultSearchFieldName;
    } else {
      if (!fields.containsKey(searchInField)) {
        throw new IndexException("Invalid value (" + searchInField
            + ") for the searchInField parameter.");
      }
    }

    // use all stored fields if no fields provided
    if (fieldsToReturn == null) {
      fieldsToReturn = new HashSet<String>();
      for (FieldInfo fi : fields.values()) {
        if (fi.isStored())
          fieldsToReturn.add(fi.getName());
      }
    }

    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_35);
    if(caseSensitive) {
      analyzer = new WhitespaceAnalyzer(Version.LUCENE_35);
    }
    
    // the parser object
    QueryParser parser = new QueryParser(Version.LUCENE_35, searchInField,
        analyzer);

    Query aQuery = null;
    StringBuilder query = new StringBuilder();
    try {
      boolean first = true;
      for(String aTerm : termsToSearch) {
        if(!first) {
          query.append(" OR ");
        }
        query.append("(\"" + QueryParserUtil.escape(aTerm) + "\")");
        first = false;
      }
      
      aQuery = parser.parse(query.toString());

      TopScoreDocCollector collector = TopScoreDocCollector.create(
          noOfHits, true);
      searcher.search(aQuery, collector);
      TopDocs topDocs = collector.topDocs();
      for (ScoreDoc aDoc : topDocs.scoreDocs) {
        int docId = aDoc.doc;
        Document doc = reader.document(docId);
        Hit aHit = new Hit(docId + "");
        for (String aField : fieldsToReturn) {
          String val = doc.get(aField);
          if (val != null) {
            val = val.substring(
                Constants.FIELD_START_MARKUP.length(),
                val.length()
                    - Constants.FIELD_END_MARKUP.length());
            aHit.add(aField, val.trim());
          }
        }
        aHit.add("score", Float.toString(aDoc.score));
        toReturn.add(aHit);
      }
    } catch (ParseException e) {
      throw new IndexException(e);
    } catch (IOException e) {
      throw new IndexException(e);
    }
    return toReturn;
	}
	
	/**
	 * @param searchInField
	 *            leave this null if you want to use the default search field
	 *            set at the indexing time
	 * @param query
	 * @param fieldsToReturn
	 *            set to null if return all the fields
	 * @param shouldStartWith
	 * @param shouldEndWith
	 * @param appendStar
	 * @param noOfHits
	 * @return
	 */
	public List<Hit> search(String searchInField, String query,
			Set<String> fieldsToReturn, boolean shouldStartWith,
			boolean shouldEndWith, boolean appendStar, int noOfHits)
			throws IndexException {

		// to return
		List<Hit> toReturn = new ArrayList<Hit>();

		// sanity check on the parameters
		if (shouldEndWith && appendStar) {
			throw new IndexException(
					"Both shouldEndWith and appendStart cannot be set to true at the same time");
		}

		// has user provided the field to search in?
		if (searchInField == null) {
			searchInField = defaultSearchFieldName;
		} else {
			if (!fields.containsKey(searchInField)) {
				throw new IndexException("Invalid value (" + searchInField
						+ ") for the searchInField parameter.");
			}
		}

		// use all stored fields if no fields provided
		if (fieldsToReturn == null) {
			fieldsToReturn = new HashSet<String>();
			for (FieldInfo fi : fields.values()) {
				if (fi.isStored())
					fieldsToReturn.add(fi.getName());
			}
		}

		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_35);
		if(caseSensitive) {
			analyzer = new WhitespaceAnalyzer(Version.LUCENE_35);
		}
		
		// the parser object
		QueryParser parser = new QueryParser(Version.LUCENE_35, searchInField,
				analyzer);

		Query aQuery = null;
		try {

			if (shouldStartWith) {
				query = Constants.FIELD_START_MARKUP + " " + query;
			}

			if (shouldEndWith) {
				query += " " + Constants.FIELD_END_MARKUP;
			}

                        query = query.replaceAll("\\\"","");

			/*
			 * if (appendStar) { int index = query.lastIndexOf(' '); String
			 * lastTerm = query.substring(index + 1);
			 * 
			 * PrefixTermEnum pte = new PrefixTermEnum(reader, new Term(
			 * searchInField, lastTerm)); while (pte.next()) {
			 * System.out.println(pte.term().text()); } }
			 */

			query = "\"" + QueryParserUtil.escape(query) + "\"";

			aQuery = parser.parse(query);
			// System.out.println(aQuery.toString());

			TopScoreDocCollector collector = TopScoreDocCollector.create(
					noOfHits, true);
			searcher.search(aQuery, collector);
			TopDocs topDocs = collector.topDocs();
			for (ScoreDoc aDoc : topDocs.scoreDocs) {
				int docId = aDoc.doc;
				Document doc = reader.document(docId);
				Hit aHit = new Hit(docId + "");
				for (String aField : fieldsToReturn) {
					String val = doc.get(aField);
					if (val != null) {
						val = val.substring(
								Constants.FIELD_START_MARKUP.length(),
								val.length()
										- Constants.FIELD_END_MARKUP.length());
						aHit.add(aField, val.trim());
					}
				}
		        aHit.add("score", Float.toString(aDoc.score));
				toReturn.add(aHit);
			}
		} catch (ParseException e) {
			throw new IndexException(e);
		} catch (IOException e) {
			throw new IndexException(e);
		}
		return toReturn;
	}

	public void close() throws IndexException {
		try {
			reader.close();
			searcher.close();
		} catch (IOException e) {
			throw new IndexException(e);
		}

	}

	public static void main(String[] args) throws Exception {
		if (args.length < 5) {
			System.out
					.println("Usage: java Searcher <absolutePath_indexDir>"
							+ " <string_query> <bool_shouldStartWith?> <bool_shouldEndWith?> <topN> <searchInField>");
			System.exit(-1);
		}

		boolean shouldStartWith = Boolean.parseBoolean(args[2]);
		boolean shouldEndWith = Boolean.parseBoolean(args[3]);
		int topN = Integer.parseInt(args[4]);
		String searchInField = null;
		if (args.length > 5)
			searchInField = args[5];

		// the searcher object
		Searcher s = new Searcher(new File(args[0]));

		// initiate search
		List<Hit> hits = s.search(searchInField, args[1], null,
				shouldStartWith, shouldEndWith, false, topN);
		for (Hit h : hits) {
			System.out.println("====\n" + h.getDocumentId());
			for (String key : h.getMap().keySet()) {
				System.out.println(key + "\t" + h.getMap().get(key));
			}
		}

		s.close();
	}
	
	
	 public static void main1(String[] args) throws Exception {
	    if (args.length < 3) {
	      System.out
	          .println("Usage: java Searcher <absolutePath_indexDir> <topN> <term1> {<term2> ... <termN>}");
	      System.exit(-1);
	    }

	    int topN = Integer.parseInt(args[1]);
	    String searchInField = null;

	    // the searcher object
	    Searcher s = new Searcher(new File(args[0]));

	    Set<String> termsToSearch = new HashSet<String>();
	    for(int i=2;i<args.length;i++) {
	      termsToSearch.add(args[i]);
	    }
	    
	    // initiate search
	    List<Hit> hits = s.searchTerms(searchInField, termsToSearch, null, topN);
	    for (Hit h : hits) {
	      System.out.println("====\n" + h.getDocumentId());
	      for (String key : h.getMap().keySet()) {
	        System.out.println(key + "\t" + h.getMap().get(key));
	      }
	    }

	    s.close();
	  }

}
