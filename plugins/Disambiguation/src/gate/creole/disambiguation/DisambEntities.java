package gate.creole.disambiguation;

import gate.Annotation;
import gate.AnnotationSet;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.RunTime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@CreoleResource(name = "Disambiguate Entities PR")
public class DisambEntities extends AbstractLanguageAnalyser implements
  ProcessingResource {
  private List<String> annotTypes;

  private String inputASName;

  public Resource init() throws ResourceInstantiationException {
    return this;
  }

  public void reInit() throws ResourceInstantiationException {
    // first clean up all the resources
    cleanup();
    init();
  } // reInit()

  @Override
  public void execute() throws ExecutionException {
    // step 1
    // for all do
    AnnotationSet inputAS = document.getAnnotations(inputASName);
    AnnotationSet annotsSet = inputAS.get(new HashSet<String>(annotTypes));
    List<Annotation> tokensList =
      gate.Utils.inDocumentOrder(inputAS.get("Token"));
    List<Long> startOffsets = new ArrayList<Long>();
    for(Annotation aToken : tokensList) {
      startOffsets.add(gate.Utils.start(aToken));
    }
    // a string containing all the entities
    StringBuilder annotSB = new StringBuilder();
    for(Annotation annot : annotsSet) {
      annotSB.append(gate.Utils.stringFor(document, annot) + " ");
    }
    document.getFeatures().put("entitiesContext", annotSB.toString().trim());
    // tokens
    for(Annotation annot : annotsSet) {
      StringBuilder sb = new StringBuilder();
      annot.getFeatures().put(
        "string",
        CommonUtils.normaliseAccentedChars(gate.Utils
          .stringFor(document, annot)));
      //
      Long startOffset = gate.Utils.start(annot);
      int index = startOffsets.indexOf(startOffset);
      if(index < 0) {
        continue;
      }
      int stIndex = index - 30;
      if(stIndex < 0) stIndex = 0;
      int enIndex = index + 30;
      if(enIndex > tokensList.size()) {
        enIndex = tokensList.size();
      }
      // only consider tokens that have NN* features
      for(int i = stIndex; i < enIndex; i++) {
        Annotation aToken = tokensList.get(i);
        String cat = (String)aToken.getFeatures().get("category");
        if(cat.startsWith("NN")) {
          sb.append(gate.Utils.stringFor(document, aToken) + " ");
        }
      }
      // put the context string as a feature
      annot.getFeatures().put("tokenContextStr",
        CommonUtils.normaliseAccentedChars(sb.toString().trim()));
    }
    // now lets disambiguate entities within this document
    for(String annotType : annotTypes) {
      AnnotationSet atSet = inputAS.get(annotType);
      List<Annotation> annotsList = gate.Utils.inDocumentOrder(atSet);
      for(int i = 0; i < annotsList.size() - 1; i++) {
        Annotation annot1 = annotsList.get(i);
        String annot1Str = (String)annot1.getFeatures().get("string");
        String annot1ContextStr =
          (String)annot1.getFeatures().get("tokenContextStr");
        if(annot1ContextStr == null) continue;
        float maxScore = -1;
        int maxId = -1;
        for(int j = i + 1; j < annotsList.size(); j++) {
          Annotation annot2 = annotsList.get(j);
          String annot2Str = (String)annot2.getFeatures().get("string");
          String annot2ContextStr =
            (String)annot2.getFeatures().get("tokenContextStr");
          if(annot2ContextStr == null) continue;
          float strScore = CommonUtils.match(annot1Str, annot2Str, false);
          float contextScore =
            CommonUtils.match(annot1ContextStr, annot2ContextStr, false);
          float finalScore = (float)(strScore * 0.70 + contextScore * 0.30);
          if(finalScore > maxScore) {
            maxScore = finalScore;
            maxId = j;
          }
        }
        if(maxScore >= 0.3) {
          String feat =
            "http://www.gate.ac.uk/ontology#" +
              java.util.Calendar.getInstance().getTimeInMillis();
          Annotation anotherAnnot = annotsList.get(maxId);
          String inst1 = (String)annot1.getFeatures().get("inst");
          String inst2 = (String)anotherAnnot.getFeatures().get("inst");
          if(inst1 == null && inst2 == null) {
            annot1.getFeatures().put("inst", feat);
            anotherAnnot.getFeatures().put("inst", feat);
          } else if(inst1 != null) {
            anotherAnnot.getFeatures().put("inst", inst1);
          } else if(inst2 != null) {
            annot1.getFeatures().put("inst", inst2);
          } else {
            annot1.getFeatures().put("sameAs", inst2);
            anotherAnnot.getFeatures().put("sameAs", inst1);
          }
        }
      }
    }
    // that's the last document
    if(corpus.indexOf(document) == corpus.size() - 1) {
    }
  }

  public List<String> getAnnotTypes() {
    return annotTypes;
  }

  @CreoleParameter(defaultValue = "Person;Location;Organisation")
  @RunTime
  public void setAnnotTypes(List<String> annotTypes) {
    this.annotTypes = annotTypes;
  }

  public String getInputASName() {
    return inputASName;
  }

  @CreoleParameter
  @RunTime
  public void setInputASName(String inputASName) {
    this.inputASName = inputASName;
  }
}
