
//For each set of same span disambiguation mentions, output the best.
//If the best doesn't cross the threshold, make a blank.

AnnotationSet defaultAS = doc.getAnnotations();

def confidenceThresholdString = System.getProperty("lodie.disambiguation-libsvm.SelectBestCandidate.confidenceThreshold")
def confidenceThreshold = -0.75
if(confidenceThresholdString != null) {
  confidenceThreshold = confidenceThresholdString.toDouble()
}

defaultAS.get("LookupList").each{ ll ->
    AnnotationSet candidates = Utils.getCoextensiveAnnotations(inputAS, ll, "Mention_disamb");
    Annotation bestcandidate = null;
    double scoreofbestcandidate = -1.0F;
    
    //Compete the candidates
    for(Annotation cand : candidates){

      double candidatescore = -1.0F;
      if (cand.getFeatures().get("LF_confidence")!=null){
        candidatescore = (double)cand.getFeatures().get("LF_confidence");
        if (cand.getFeatures().get("LF_class").equals("false")){
          candidatescore*=-1.0F;
        }
      }

      if(candidatescore>scoreofbestcandidate || bestcandidate==null){
        bestcandidate = cand;
        scoreofbestcandidate = candidatescore;
      }

    }

    if(bestcandidate!=null && scoreofbestcandidate>confidenceThreshold){
      gate.Utils.addAnn(outputAS, bestcandidate, "Mention", Utils.toFeatureMap(bestcandidate.getFeatures()));
    //} else {
    // TODO: check if we should create a NIL annotation here!
    //  gate.Utils.addAnn(outputAS, ll, "Mention", Factory.newFeatureMap());
    }
}



