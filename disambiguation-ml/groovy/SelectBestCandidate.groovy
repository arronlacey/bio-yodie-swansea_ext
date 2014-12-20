
//For each set of same span disambiguation mentions, output the best.
//If the best doesn't cross the threshold, make a blank.

AnnotationSet defaultAS = doc.getAnnotations();

defaultAS.get("LookupList").each{ ll ->
    AnnotationSet candidates = Utils.getCoextensiveAnnotations(inputAS, ll, "Mention_disamb");
    Annotation bestcandidate = null;
    double scoreofbestcandidate = -1.0F;
    
    //Compete the candidates
    for(Annotation cand : candidates){

      double candidatescore = -1.0F;
      if (cand.getFeatures().get("LF_class")!=null){
        candidatescore = Double.parseDouble(cand.getFeatures().get("LF_class"));
      }

      if(candidatescore>scoreofbestcandidate || bestcandidate==null){
        bestcandidate = cand;
        scoreofbestcandidate = candidatescore;
      }

    }

    if(bestcandidate!=null && scoreofbestcandidate>-0.9){
      gate.Utils.addAnn(outputAS, bestcandidate, "Mention", bestcandidate.getFeatures());
    //} else {
    //  gate.Utils.addAnn(outputAS, ll, "Mention", Factory.newFeatureMap());
    }
}



