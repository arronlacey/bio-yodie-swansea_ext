package gate.creole.gazetteer.lucene;

/**
 * Field information
 * 
 * @author niraj
 */
public class FieldInfo {

  /**
   * name of the field
   */
  private String name;

  /**
   * should it be indexed?
   */
  private boolean indexed;

  /**
   * should it be stored?
   */
  private boolean stored;
  
  /**
   * default search field
   */
  private boolean defaultSearchField;

  /**
   * Constructor
   * 
   * @param name
   * @param indexed
   * @param stored
   * @param defaultSearchField
   */
  public FieldInfo(String name, boolean indexed, boolean stored, boolean defaultSearchField) {
    this.name = name;
    this.indexed = indexed;
    this.stored = stored;
    this.defaultSearchField = defaultSearchField;
  }

  /**
   * returns the name of the field
   * 
   * @return
   */
  public String getName() {
    return name;
  }

  /**
   * Tells if the field content is indexed or not.
   * 
   * @return
   */
  public boolean isIndexed() {
    return indexed;
  }

  /**
   * Tells if the field content is stored or not.
   * 
   * @return
   */
  public boolean isStored() {
    return stored;
  }

  /**
   * indicates if the given field is set as default search field.
   * @return
   */
  public boolean isDefaultSearchField() {
    return defaultSearchField;
  }

  /**
   * tells if the provided field is set as default searchable field
   * @param defaultSearchField
   */
  public void setDefaultSearchField(boolean defaultSearchField) {
    this.defaultSearchField = defaultSearchField;
  }
}
