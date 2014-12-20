package gate.twitter.expander;

import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;

/**
 * Annotate all the text annotated by the individual PRs with a single annotation.
 * 
 * Annotates all the text after the position indicated by the document feature
 * TwitterExpanderOriginalTextSize with the given annotation type (default
 * TwitterExpanderAll) in the given annotation set. If the document feature
 * is not found, nothing is annotated.
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "TwitterExpanderAnnotateAll",
        comment = "Annotate all text additions by the TwitterExpander with a single annotation")
public class TwitterExpanderAnnotateAll extends AbstractLanguageAnalyser {
  
  // PARAMETERS
  
  protected String annotationType;
  @Optional
  @RunTime
  @CreoleParameter(comment="The output annotation type",defaultValue="TwitterExpanderAll")
  public void setAnnotationType(String name) { annotationType = name; }
  public String getAnnotationType() { return annotationType; }
  
  protected String annotationSet;
  @Optional
  @RunTime
  @CreoleParameter(comment="The output annotation set annotation set",defaultValue="")
  public void setAnnotationSet(String name) { annotationSet = name; }
  public String getAnnotationSet() { return annotationSet; }
  
  @Override
  public void execute() {
    FeatureMap docfm = document.getFeatures();
    if(docfm.get("TwitterExpanderOriginalTextSize") == null) {
      return;
    }
    long origSize = (Long)docfm.get("TwitterExpanderOriginalTextSize");
    long curSize = document.getContent().size();
    
    AnnotationSet outAS = document.getAnnotations(getAnnotationSet());
    String outType = getAnnotationType();
    if(outType == null || !outType.isEmpty()) { outType = "TwitterExpanderAll"; }

    gate.Utils.addAnn(outAS, origSize, curSize, outType, Factory.newFeatureMap());
  }
  
}
