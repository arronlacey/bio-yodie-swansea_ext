
import gate.*
import gate.trendminer.lodie.utils.LodieUtils



def cli = new CliBuilder(
  usage: 'autogenerate-ranking-jape.sh docsDir')
cli.h(longOpt:"help", "Show usage information")

def options = cli.parse(args)

if(options.h) {
  cli.usage()
  System.out.println("docsDir: the directory of YODIE-processed documents to use to gather scores to be ranked.")
  return
}


def posArgs = options.arguments()

if(!(posArgs.size() == 1)) {
  cli.usage()
  System.exit(1)
}
File docDir = new File(posArgs[0])
if(!docDir.exists()) {
  System.err.println("Corpus directory does not exist: "+docDir)
  System.exit(1)
}

// 1) set up GATE
String gatehome= System.getenv()['GATE_HOME']
if(gatehome == null) {
  System.err.println("Environment variable GATE_HOME not set!")
  System.exit(1);
}
Gate.setGateHome(new File(gatehome))
Gate.runInSandbox(true)
Gate.init()

String outFormat="finf"
if(options.o) {
  if(options.o == "xml") {
    outFormat = "xml"
  } else if(options.o != "finf") {
    System.err.println("Output format must be xml or finf, not "+options.o)
    System.exit(1)
  }
}
gate.Utils.loadPlugin("Format_FastInfoset")
def docFormat = null
if(outFormat == "finf") {  
  docFormat = gate.DocumentFormat.getDocumentFormat(new gate.corpora.MimeType("application","fastinfoset"))
}  

featurenames = new HashSet<String>()

// 5) iterate over the documents in the directory
// for now, we only read files with an .xml or .finf extension
def extFilter = ~/.+\.(?:xml|finf)/
docDir.traverse(type: groovy.io.FileType.FILES, nameFilter: extFilter) { file ->
  // create a document from that file
  String fileName = file.getName()
  FeatureMap parms = Factory.newFeatureMap();
  parms.put(Document.DOCUMENT_ENCODING_PARAMETER_NAME, "UTF-8")
  parms.put("sourceUrl", file.toURI().toURL())
  Document doc = (Document) gate.Factory.createResource("gate.corpora.DocumentImpl", parms);
  doc.setName(fileName)

String docName = doc.getName()

println(docName)

AnnotationSet lookups = doc.getAnnotations().get("Lookup")

for(lookup in lookups){
 println(Utils.cleanString(doc, lookup))
 for(featurename in lookup.getFeatures().keySet()){
  println(featurename)
  featurenames.add(featurename)
 }
}

println(featurenames.size())
}
