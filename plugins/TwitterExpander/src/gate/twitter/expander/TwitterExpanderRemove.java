/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.twitter.expander;

import gate.Annotation;
import gate.AnnotationSet;
import gate.FeatureMap;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import gate.util.InvalidOffsetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remove everything added or individual added parts.
 * 
 * If the annotation set and type to indicate removal is given, all text
 * under such an annotation plus all text under the type with "Space" appended
 * will be removed. Otherwise, everything after the original text will be
 * removed. The size of the initial text is expected to be indicated by the 
 * document feature TwitterExpanderOriginalTextSize and after removal of the
 * text after the original text, the feature will be removed too.
 * If the feature is not found, nothing will be removed.
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "TwitterExpanderRemove",
        comment = "Remove some or all of the twitter expansions")
public class TwitterExpanderRemove extends AbstractLanguageAnalyser {
  
  // PARAMETERS
  
  protected String annotationType;
  @Optional
  @RunTime
  @CreoleParameter(comment="The annotation type indicating what to remove",defaultValue="")
  public void setAnnotationType(String name) { annotationType = name; }
  public String getAnnotationType() { return annotationType; }
  
  protected String annotationSet;
  @Optional
  @RunTime
  @CreoleParameter(comment="The annotation set",defaultValue="")
  public void setAnnotationSet(String name) { annotationSet = name; }
  public String getAnnotationSet() { return annotationSet; }
  
  @Override
  public void execute() {
    //System.out.println("Annotation set "+getAnnotationSet());
    //System.out.println("Annotation type >"+getAnnotationType()+"<");
    if(getAnnotationType() != null && !getAnnotationType().isEmpty()) {
      //System.out.println("Trying to remove by annotation");
      Set<String> typesToRemove = new HashSet<String>();
      typesToRemove.add(getAnnotationType());
      typesToRemove.add(getAnnotationType()+"Space");
      AnnotationSet toRemove = document.getAnnotations(getAnnotationSet()).get(typesToRemove);
      List<Annotation> toRemoveList = toRemove.inDocumentOrder();
      Collections.reverse(toRemoveList);
      for(Annotation ann : toRemoveList) {
        try {
          document.edit(Utils.start(ann), Utils.end(ann), null);
        } catch (InvalidOffsetException ex) {
          ex.printStackTrace(System.err);
          throw new GateRuntimeException("Could not remove text",ex);
        }
      }
      FeatureMap docfm = document.getFeatures();
      if(docfm.get("TwitterExpanderOriginalTextSize") != null) {
        long origSize = (Long)docfm.get("TwitterExpanderOriginalTextSize");
        long curSize = document.getContent().size();
        if(curSize == origSize) {
          docfm.remove("TwitterExpanderOriginalTextSize");
        }
      }
    } else {
      //System.out.println("Trying to remove everything");
      // remove everything
      FeatureMap docfm = document.getFeatures();
      if(docfm.get("TwitterExpanderOriginalTextSize") != null) {
        long origSize = (Long)docfm.get("TwitterExpanderOriginalTextSize");
        long curSize = document.getContent().size();
        //System.out.println("Got orig size and current size "+origSize+"/"+curSize);
        if(curSize > origSize) {
          try {
            document.edit(origSize, curSize, null);
          } catch (InvalidOffsetException ex) {
            ex.printStackTrace(System.err);
            throw new GateRuntimeException("Could not remove text",ex);
          }
        }
        docfm.remove("TwitterExpanderOriginalTextSize");
      } else {
        System.out.println("No feature, nothing done");
      }      
    }
  }
  
}
