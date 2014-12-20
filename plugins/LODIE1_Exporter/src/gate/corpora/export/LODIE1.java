package gate.corpora.export;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.DocumentExporter;
import gate.FeatureMap;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Vector;

import com.thoughtworks.xstream.XStream;

@CreoleResource(name = "LODIE1 Exporter", tool = true, autoinstances = @AutoInstance)
public class LODIE1 extends DocumentExporter {

  private String annotationSetName;

  public String getAnnotationSetName() {
    return annotationSetName;
  }

  @RunTime
  @Optional
  @CreoleParameter
  public void setAnnotationSetName(String annotationSetName) {
    this.annotationSetName = annotationSetName;
  }

  public LODIE1() {
    super("LODIE1", "xml", "application/xml");
  }

  @Override
  public void export(Document doc, OutputStream out, FeatureMap options)
      throws IOException {

    AnnotationSet mentions =
        doc.getAnnotations((String)options.get("annotationSetName")).get(
            "Mention");

    SortedAnnotationList sortedMentions = new SortedAnnotationList();
    for(Annotation mention : mentions) {
      sortedMentions.addSortedExclusive(mention);
    }

    long insertPositionEnd;
    long insertPositionStart;

    String startTagPart_1 = "<Mention inst=\"";
    String startTagPart_2 = "\">";
    String endTag = "</Mention>";

    StringBuffer editableContent =
        new StringBuffer(doc.getContent().toString());

    for(int i = sortedMentions.size() - 1; i >= 0; --i) {
      Annotation mention = sortedMentions.get(i);
      insertPositionStart = mention.getStartNode().getOffset().longValue();
      insertPositionEnd = mention.getEndNode().getOffset().longValue();

      if(insertPositionEnd != -1 && insertPositionStart != -1) {
        editableContent.insert((int)insertPositionEnd, endTag);
        editableContent.insert((int)insertPositionStart, startTagPart_2);
        editableContent.insert((int)insertPositionStart, mention.getFeatures().get("inst"));
        editableContent.insert((int)insertPositionStart, startTagPart_1);
      } 
    } 
    
    ResponseObject ro = ResponseObject.success(editableContent.toString());
    
    XStream xstream = new XStream();
    xstream.alias("message", ResponseObject.class);
    xstream.toXML(ro,out);
    out.flush();   
  }
  
  private static class ResponseObject {
    public String text,msg,status;
    
    public static ResponseObject error(String msg) {
      ResponseObject ro = new ResponseObject();
      ro.status = "ERROR";
      ro.msg = msg;
      ro.text="";
      
      return ro;
    }
    
    public static ResponseObject success(String text) {
      ResponseObject ro = new ResponseObject();
      ro.status = "SUCCESS";
      ro.msg = "";
      ro.text= text;
      
      return ro;
    }
  }

  private static class SortedAnnotationList extends Vector<Annotation> {

    private static final long serialVersionUID = -3517593401660887655L;

    public SortedAnnotationList() {
      super();
    }

    public boolean addSortedExclusive(Annotation annot) {
      Annotation currAnot = null;

      for(int i = 0; i < size(); ++i) {
        currAnot = get(i);
        if(annot.overlaps(currAnot)) { return false; }
      }

      long annotStart = annot.getStartNode().getOffset().longValue();
      long currStart;

      for(int i = 0; i < size(); ++i) {
        currAnot = get(i);
        currStart = currAnot.getStartNode().getOffset().longValue();
        if(annotStart < currStart) {
          insertElementAt(annot, i);

          return true;
        }
      }

      int size = size();
      insertElementAt(annot, size);
      return true;
    }
  }
}
