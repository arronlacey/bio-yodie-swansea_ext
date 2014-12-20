package gate.creole.disambiguation;

/*
 * FlexibleGazetteer.java
 * 
 * Copyright (c) 2004-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June1991.
 * 
 * A copy of this licence is included in the distribution in the file
 * licence.html, and is also available at http://gate.ac.uk/gate/licence.html.
 * 
 * Niraj Aswani 02/2002 $Id: FlexibleGazetteer.java 14808 2011-12-19 13:42:09Z
 * adamfunk $
 */
import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.ProcessingResource;
import gate.Resource;
import gate.Utils;
import gate.corpora.DocumentImpl;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.gazetteer.NodePosition;
import gate.util.InvalidOffsetException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Title: Flexible Gazetteer
 * </p>
 * <p>
 * The Flexible Gazetteer provides users with the flexibility to choose their
 * own customised input and an external Gazetteer. For example, the user might
 * want to replace words in the text with their base forms (which is an output
 * of the Morphological Analyser).
 * </p>
 * <p>
 * The Flexible Gazetteer performs lookup over a document based on the values of
 * an arbitrary feature of an arbitrary annotation type, by using an externally
 * provided gazetteer. It is important to use an external gazetteer as this
 * allows the use of any type of gazetteer (e.g. an Ontological gazetteer).
 * </p>
 * 
 * @author niraj aswani
 * @version 1.0
 */
public class FlexibleGazetteer extends AbstractLanguageAnalyser implements
  ProcessingResource {
  private static final long serialVersionUID = -1023682327651886920L;

  /**
   * Does the actual loading and parsing of the lists. This method must be
   * called before the gazetteer can be used
   */
  public Resource init() throws ResourceInstantiationException {
    if(gazetteerInst == null) { throw new ResourceInstantiationException(
      "No Gazetteer Provided!"); }
    return this;
  }

  /**
   * This method runs the gazetteer. It assumes that all the needed parameters
   * are set. If they are not, an exception will be fired.
   */
  public void execute() throws ExecutionException {
    fireProgressChanged(0);
    fireStatusChanged("Checking Document...");
    if(document == null) { throw new ExecutionException(
      "No document to process!"); }
    // obtain the inputAS
    AnnotationSet inputAS = document.getAnnotations(inputASName);
    // anything in the inputFeatureNames?
    if(inputFeatureNames == null || inputFeatureNames.size() == 0) { throw new ExecutionException(
      "No input feature names provided!"); }
    // for each input feature, create a temporary document and run the
    // gazetteer
    for(String aFeature : inputFeatureNames) {
      // find out the feature name user wants us to use
      String[] keyVal = aFeature.split("\\.");
      // if invalid feature name
      if(keyVal.length != 2) {
        System.err.println("Invalid input feature name:" + aFeature);
        continue;
      }
      // keyVal[0] = annotation type
      // keyVal[1] = feature name
      // holds mapping for newly created annotations
      Map<Long, NodePosition> annotationMappings =
        new HashMap<Long, NodePosition>();
      fireStatusChanged("Creating temporary Document for feature " + aFeature);
      StringBuilder newdocString =
        new StringBuilder(document.getContent().toString());
      // sort annotations
      List<Annotation> annotations =
        Utils.inDocumentOrder(inputAS.get(keyVal[0]));
      // remove duplicate annotations
      removeOverlappingAnnotations(annotations);
      // initially no space is deducted
      int totalDeductedSpaces = 0;
      // now replace the document content with the value of the feature that
      // user has provided
      for(Annotation currentAnnotation : annotations) {
        // if there's no such feature, continue
        if(!currentAnnotation.getFeatures().containsKey(keyVal[1])) continue;
        String newTokenValue =
          currentAnnotation.getFeatures().get(keyVal[1]).toString();
        // if no value found for this feature
        if(newTokenValue == null) continue;
        // feature value found so we need to replace it
        // find the start and end offsets for this token
        long startOffset = Utils.start(currentAnnotation);
        long endOffset = Utils.end(currentAnnotation);
        // let us find the difference between the lengths of the
        // actual string and the newTokenValue
        long actualLength = endOffset - startOffset;
        long lengthDifference = actualLength - newTokenValue.length();
        // so lets find out the new startOffset and endOffset
        long newStartOffset = startOffset - totalDeductedSpaces;
        long newEndOffset = newStartOffset + newTokenValue.length();
        totalDeductedSpaces += lengthDifference;
        // only include node if there's some difference in the offsets
        if(startOffset != newStartOffset || endOffset != newEndOffset) {
          // and make the entry for this
          NodePosition mapping =
            new NodePosition(startOffset, endOffset, newStartOffset,
              newEndOffset);
          annotationMappings.put(newEndOffset, mapping);
        }
        // and finally replace the actual string in the document
        // with the new document
        newdocString.replace((int)newStartOffset, (int)newStartOffset +
          (int)actualLength, newTokenValue);
      }
      // proceed only if there was any replacement Map
      if(annotationMappings.isEmpty()) continue;
      // storing end offsets of annotations in an array for quick
      // lookup later on
      long[] offsets = new long[annotationMappings.size()];
      int index = 0;
      for(Long aKey : annotationMappings.keySet()) {
        offsets[index] = aKey;
        index++;
      }
      // for binary search, offsets need to be in ascending order
      Arrays.sort(offsets);
      // otherwise create a temporary document for the new text
      Document tempDoc = null;
      // update the status
      fireStatusChanged("Processing document with Gazetteer...");
      try {
        FeatureMap params = Factory.newFeatureMap();
        params.put("stringContent", newdocString.toString());
        // set the appropriate encoding
        if(document instanceof DocumentImpl) {
          params.put("encoding", ((DocumentImpl)document).getEncoding());
          params.put("markupAware", ((DocumentImpl)document).getMarkupAware());
        }
        FeatureMap features = Factory.newFeatureMap();
        tempDoc =
          (Document)Factory.createResource("gate.corpora.DocumentImpl", params,
            features);
      } catch(ResourceInstantiationException rie) {
        throw new ExecutionException("Temporary document cannot be created",
          rie);
      }
      try {
        // lets create the gazetteer based on the provided gazetteer name
        gazetteerInst.setDocument(tempDoc);
        try {
          gazetteerInst.setParameterValue("annotationSetName",
            this.outputASName);
        } catch(Exception e) {
          e.printStackTrace();
        }
        fireStatusChanged("Executing Gazetteer...");
        gazetteerInst.execute();
        // now the tempDoc has been looked up, we need to shift the annotations
        // from this temp document to the original document
        fireStatusChanged("Transfering new annotations to the original one...");
        AnnotationSet original = document.getAnnotations(outputASName);
        // okay iterate over new annotations and transfer them back to
        // the original document
        for(Annotation currentLookup : tempDoc.getAnnotations(outputASName)) {
          long tempStartOffset = Utils.start(currentLookup);
          long tempEndOffset = Utils.end(currentLookup);
          long newStartOffset = tempStartOffset;
          long newEndOffset = tempEndOffset;
          long addedSpaces = 0;
          // we find out the node before the current annotation's startoffset
          // and it to find out the number of extra characters added
          index = Arrays.binarySearch(offsets, newStartOffset);
          // if index <0, the absolute position of it refers to the
          // position after the node we want to access to
          // find out the no. of extra characters added before the
          // current position
          if(index < 0) {
            index = Math.abs(index) - 1;
          }
          if(index > 0) {
            // go back one node
            index--;
            NodePosition node = annotationMappings.get(offsets[index]);
            long oldEnd = node.getOriginalEndOffset();
            addedSpaces = node.getTempEndOffset() - oldEnd;
            newStartOffset -= addedSpaces;
          }
          // we are trying to find a node which holds information
          // about the number of new characters added before
          // the new end offset
          index = Arrays.binarySearch(offsets, newEndOffset);
          if(index < 0) {
            index = Math.abs(index) - 1;
          }
          if(index >= 0) {
            // if the index 0
            // it means
            // if points to the length of the array, it means,
            // we need to refer to the last element
            if(index == offsets.length) index--;
            NodePosition node = annotationMappings.get(offsets[index]);
            if(offsets[index] <= newEndOffset) {
              long oldEnd = node.getOriginalEndOffset();
              addedSpaces = node.getTempEndOffset() - oldEnd;
            } else {
              long oldStart = node.getOriginalStartOffset();
              addedSpaces = node.getTempStartOffset() - oldStart;
            }
          }
          newEndOffset -= addedSpaces;
          try {
            // before we do this, make sure there is no other annotation like
            // this
            AnnotationSet tempSet =
              original.getContained(newStartOffset, newEndOffset).get(
                currentLookup.getType(), currentLookup.getFeatures());
            boolean found = false;
            for(Annotation annot : tempSet) {
              if(Utils.start(annot) == newStartOffset &&
                Utils.end(annot) == newEndOffset &&
                annot.getFeatures().size() == currentLookup.getFeatures()
                  .size()) {
                found = true;
                break;
              }
            }
            if(!found) {
              original.add(newStartOffset, newEndOffset,
                currentLookup.getType(), currentLookup.getFeatures());
            }
          } catch(InvalidOffsetException e) {
            throw new ExecutionException(e);
          }
        } // END for OVER ALL THE Lookups
      } finally {
        gazetteerInst.setDocument(null);
        if(tempDoc != null) {
          // now remove the newDoc
          Factory.deleteResource(tempDoc);
        }
      }
    } // for
    fireProcessFinished();
  } // END execute METHOD

  /**
   * Removes the overlapping annotations. preserves the one that appears first
   * in the list
   * 
   * @param annotations
   */
  private void removeOverlappingAnnotations(List<Annotation> annotations) {
    for(int i = 0; i < annotations.size() - 1; i++) {
      Annotation annot1 = annotations.get(i);
      Annotation annot2 = annotations.get(i + 1);
      long annot2Start = Utils.start(annot2);
      if(annot2Start >= Utils.start(annot1) && annot2Start < Utils.end(annot1)) {
        annotations.remove(annot2);
        i--;
        continue;
      }
    }
  }

  /**
   * Sets the document to work on
   * 
   * @param doc
   */
  public void setDocument(gate.Document doc) {
    this.document = doc;
  }

  /**
   * Returns the document set up by user to work on
   * 
   * @return a {@link Document}
   */
  public gate.Document getDocument() {
    return this.document;
  }

  /**
   * Sets the name of annotation set that should be used for storing new
   * annotations
   * 
   * @param outputASName
   */
  public void setOutputASName(String outputASName) {
    this.outputASName = outputASName;
  }

  /**
   * sets the outputAnnotationSetName
   * 
   * @param annName
   */
  @Deprecated
  public void setOutputAnnotationSetName(String annName) {
    setOutputASName(annName);
  }

  /**
   * Returns the outputAnnotationSetName
   * 
   * @return a {@link String} value.
   */
  public String getOutputASName() {
    return this.outputASName;
  }

  /**
   * sets the inputAnnotationSetName
   * 
   * @param annName
   */
  @Deprecated
  public void setInputAnnotationSetName(String annName) {
    setInputASName(annName);
  }

  /**
   * sets the input AnnotationSet Name
   * 
   * @param inputASName
   */
  public void setInputASName(String inputASName) {
    this.inputASName = inputASName;
  }

  /**
   * Returns the inputAnnotationSetName
   * 
   * @return a {@link String} value.
   */
  public String getInputASName() {
    return this.inputASName;
  }

  /**
   * Feature names for example: Token.string, Token.root etc... Values of these
   * features should be used to replace the actual string of these features.
   * This method allows a user to set the name of such features
   * 
   * @param inputs
   */
  public void setInputFeatureNames(java.util.List<String> inputs) {
    this.inputFeatureNames = inputs;
  }

  /**
   * Returns the feature names that are provided by the user to use their values
   * to replace their actual strings in the document
   * 
   * @return a {@link List} value.
   */
  public java.util.List<String> getInputFeatureNames() {
    return this.inputFeatureNames;
  }

  public AbstractLanguageAnalyser getGazetteerInst() {
    return this.gazetteerInst;
  }

  public void setGazetteerInst(AbstractLanguageAnalyser gazetteerInst) {
    this.gazetteerInst = gazetteerInst;
  }

  // Gazetteer Runtime parameters
  private gate.Document document;

  private java.lang.String outputASName;

  private java.lang.String inputASName;

  // Flexible Gazetteer parameter
  private AbstractLanguageAnalyser gazetteerInst;

  private java.util.List<String> inputFeatureNames;
}