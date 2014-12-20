package gate.creole.gazetteer.lucene;

import java.util.HashMap;
import java.util.Map;

public class Hit {

  /**
   * id of the document
   */
  private String documentId;

  /**
   * the map containing information requested by the user
   */
  private Map<String, String> map;

  /**
   * constructor
   * 
   * @param documentId
   */
  public Hit(String documentId) {
    this.documentId = documentId;
    map = new HashMap<String, String>();
  }

  /**
   * add key value pair to the hit
   * @param key
   * @param value
   */
  public void add(String key, String value) {
    map.put(key, value);
  }

  /**
   * returns the document id of this hit
   * @return
   */
  public String getDocumentId() {
    return documentId;
  }

  /**
   * returns the stored values for this hit
   * @return
   */
  public Map<String, String> getMap() {
    return map;
  }
}
