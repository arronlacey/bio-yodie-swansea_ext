// Assign default document features, unless they are already set
// Also set the derived run-... features based on the final values
// of the descriptive document features, e.g. run-preprocess-en-twitter
// to true if the language is en and docType is tweet

def features = doc.getFeatures()

// if the language feature is not set, assume the document is English
def lang = features.get("lang")
if(lang == null || lang.isEmpty()) { lang = "en" }
features.put("lang",lang)

// if the docType feature is not set, assume generic
// (other possibilities: "tweet", "news" 
def docType = features.get("docType")
def docTypeOrig = docType
if(docType == null || docType.isEmpty()) { docType = "generic" }
features.put("docType",docType)

////// set derived features
// Note that "boolean"  features used for the conditional pipelines have 
// to be Strings.

// the default values are based on the defaults for the descriptive features
def run_preprocess_en = "true" 
def run_preprocess_en_twitter = "false" 

if(lang == "en" && docType == "tweet") {
  run_preprocess_en = "false" 
  run_preprocess_en_twitter = "true"
}

System.out.println("document "+doc.getName()+", docType="+docType+
  ", docTypeOrig="+docTypeOrig+
  ", run_preprocess_en="+run_preprocess_en+
  ", run_preprocess_en_twitter="+run_preprocess_en_twitter)

features.put("run-preprocess-en", run_preprocess_en)
features.put("run-preprocess-en-twitter",run_preprocess_en_twitter)


