import gate.*
import gate.corpora.DocumentContentImpl
def doc = Gate.getCreoleRegister().getAllInstances("gate.Document").find{ it.getName().startsWith("testdoc") }
if(doc == null || doc.getContent().size() < 100) {
  println("Load some document with at least 5 characters and rename it to 'testdoc' first, then run this script first without looking at the document in the GUI and then with first looking at it")
  return
}
def defaultAS = doc.getAnnotations()
Utils.addAnn(defaultAS,0,5,"Ann1",Factory.newFeatureMap())
//Gate.getUserConfig().put(GateConstants.DOCEDIT_INSERT_PREPEND,true);
def size = doc.getContent().size()
doc.edit(size,size,new DocumentContentImpl("\n\nSome New Text 1"))
