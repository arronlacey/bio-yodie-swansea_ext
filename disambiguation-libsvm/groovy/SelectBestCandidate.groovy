
//For each set of same span disambiguation mentions, output the best.
//If the best doesn't cross the threshold, make a blank.

AnnotationSet defaultAS = doc.getAnnotations();

def confidenceThresholdString = System.getProperty("lodie.disambiguation-libsvm.SelectBestCandidate.confidenceThreshold")
def confidenceThreshold = 0.0
if(confidenceThresholdString != null) {
  confidenceThreshold = confidenceThresholdString.toDouble()
}

def frequencyThresholdString = System.getProperty("lodie.disambiguation-libsvm.SelectBestCandidate.frequencyThreshold")
def frequencyThreshold = 451
if(frequencyThresholdString != null) {
  frequencyThreshold = frequencyThresholdString.toInt()
}

def frequencyFeatureString = System.getProperty("lodie.disambiguation-libsvm.SelectBestCandidate.frequencyFeature")
def frequencyFeature = "scUriFreqInWp"
if(frequencyFeatureString != null) {
  frequencyFeature = frequencyFeatureString
}

defaultAS.get("LookupList").each{ ll ->
    AnnotationSet candidates = Utils.getCoextensiveAnnotations(inputAS, ll, "Mention_disamb");
    Annotation bestcandidate = null;
    Annotation commonestCandidate = null;
    double scoreofbestcandidate = -1.0F;
    int uriFreqOfCommonestCandidate = -1;
    
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

      int candidatefreq = -1;
      if (cand.getFeatures().get(frequencyFeature)!=null){
        candidatefreq = (int)cand.getFeatures().get(frequencyFeature);
      }

      if(candidatefreq>uriFreqOfCommonestCandidate || commonestCandidate==null){
        commonestCandidate = cand;
        uriFreqOfCommonestCandidate = candidatefreq;
      }

    }

    if(bestcandidate!=null && scoreofbestcandidate>confidenceThreshold){
      gate.Utils.addAnn(outputAS, bestcandidate, "Mention", Utils.toFeatureMap(bestcandidate.getFeatures()));
    } else if(commonestCandidate!=null && uriFreqOfCommonestCandidate>frequencyThreshold){
      gate.Utils.addAnn(outputAS, commonestCandidate, "Mention", Utils.toFeatureMap(commonestCandidate.getFeatures()));
    //} else {
    // TODO: check if we should create a NIL annotation here!
    //  gate.Utils.addAnn(outputAS, ll, "Mention", Factory.newFeatureMap());
    }
}



