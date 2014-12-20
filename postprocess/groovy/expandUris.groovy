// expand any non-expanded URI in the inputAS
// = If the GroovyPR parameter annotationTypes is specified, the value is assumed
// to be a semicolon-separated list of Types
// otherwise, all annotations in the set are processed
// = If the GroovyPR parameter features is specified, the value is assumed
// to ba a semicolon-separated list of feature names,
// otherwise, the feature inst is processed
// = If the GroovyPR parameter what is specified, the value should be 
// "base", "ns" or "both" and the script will attempt to do either
// just baseURI based lengthening, or just namespace-expansion or both.
// Otherwise, both is attempted
// NOTE: if the feature value of an annotation is null/missing it is ignored
// !!!NOTE (changed 20140904): if the feature value is the empty string it
// is NOT expanded and left untouched (this is so we can keep our way of how
// NILs are represented at the moment)

import java.util.HashSet

def annotationTypes = new HashSet<String>()
def annotationTypesParm = scriptParams.get("annotationTypes")
if(annotationTypesParm != null) {
  annotationTypesParm.split(";").each { annotationTypes.add(it) }
}

def features = []
def featuresParm = scriptParams.get("features")
if(featuresParm != null) {
  featuresParm.split(";").each { features.add(it) }
} else {
  features.add("inst")
}

def what = scriptParams.get("what")
if(what == null) {
  what = "both"
}

def nsMap = [
    "dbpo:" : "http://dbpedia.org/ontology/",
    "owl:"  : "http://www.w3.org/2002/07/owl#",
    "dbpp:" : "http://dbpedia.org/property/",
    "foaf:" : "http://xmlns.com/foaf/0.1/",
    "rdf:"  : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs:" : "http://www.w3.org/2000/01/rdf-schema#",
    "skos:" : "http://www.w3.org/2004/02/skos/core#",
    "yago:" : "http://mpii.de/yago/resource/"
]

def baseMap = [  "" : "http://dbpedia.org/resource/" ]

AnnotationSet anns = null
if(annotationTypes.isEmpty()) {
  anns = inputAS
} else {
  anns = inputAS.get(annotationTypes)
}

anns.each { ann ->
  FeatureMap fm = ann.getFeatures()
  features.each { feature ->
    String value = fm.get(feature)
    String newvalue = value
    if(value != null && !value.isEmpty()) {
      if(what == "both" || what == "base") {
        // try to expand the base URI
        newvalue = gate.Utils.expandUriString(value,baseMap)
      }
      if(value == newvalue && (what == "both" || what == "ns")) {
        try {
          newvalue = gate.Utils.expandUriString(value,nsMap)
        } catch(Exception ex) {
          System.err.println("Problem uri-expanding value: "+value)
        }
      }
      if(value != newvalue) {
        fm.put(feature,newvalue)
      }
    }
  }
}