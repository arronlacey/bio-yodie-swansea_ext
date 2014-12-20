package gate.learningframework;

import gate.creole.ResourceInstantiationException;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;


public class FeatureSpecification {

	private org.jdom.Document jdomDocConf = null;
	
	private List<Attribute> attributes = new ArrayList<Attribute>();
	
	private List<Ngram> ngrams = new ArrayList<Ngram>();
	
	private List<AttributeList> attributelists = new ArrayList<AttributeList>();
	
	private URL url;
	
	public class Attribute{
		public Attribute(String type, String feature, Datatype datatype){
			this.type = type;
			this.feature = feature;
			this.datatype = datatype;
		}
		
		String type = null;
		String feature = null;
		Datatype datatype = Datatype.unset;
	}

	public class Ngram{
		public Ngram(int number, String type, String feature){
			this.number = number;
			this.type = type;
			this.feature = feature;
		}
		
		int number = -1;
		String type = null;
		String feature = null;
	}

	public class AttributeList{
		public AttributeList(String type, String feature, Datatype datatype, int from, int to){
			this.type = type;
			this.feature = feature;
			this.datatype = datatype;
			this.from = from;
			this.to = to;
		}
		
		String type = null;
		String feature = null;
		Datatype datatype = Datatype.unset;
		int from = 0;
		int to = 0;
	}
	
	public enum Datatype {
		nominal, numeric, unset;
	}
	
	public FeatureSpecification(URL configFileURL) throws ResourceInstantiationException{
		this.url = configFileURL;
		
		SAXBuilder saxBuilder = new SAXBuilder(false);
		try {
			try {
				this.jdomDocConf = saxBuilder.build(configFileURL);
			} catch(JDOMException jde){
				throw new ResourceInstantiationException(jde);
			}
		} catch (java.io.IOException ex) {
			throw new ResourceInstantiationException(ex);
		}

		Element rootElement = jdomDocConf.getRootElement();
		
		List<Element> attributeElements = rootElement.getChildren("ATTRIBUTE");

		for(int i=0;i<attributeElements.size();i++){
			Element attributeElement = attributeElements.get(i);
			Datatype dt = Datatype.valueOf("unset");
			if(attributeElement.getChildText("DATATYPE")!=null){
				dt = Datatype.valueOf(attributeElement.getChildText("DATATYPE"));
			}
			Attribute att = new Attribute(
					attributeElement.getChildText("TYPE"),
					attributeElement.getChildText("FEATURE"),
					dt
			);
			this.attributes.add(att);
		}

		List<Element> ngramElements = rootElement.getChildren("NGRAM");

		for(int i=0;i<ngramElements.size();i++){
			Element ngramElement = ngramElements.get(i);
			Ngram ng = new Ngram(
					Integer.parseInt(ngramElement.getChildText("NUMBER")),
					ngramElement.getChildText("TYPE"),
					ngramElement.getChildText("FEATURE")
			);
			this.ngrams.add(ng);
		}
		
		List<Element> attributeListElements = rootElement.getChildren("ATTRIBUTELIST");

		for(int i=0;i<attributeListElements.size();i++){
			Element attributeListElement = attributeListElements.get(i);
			Datatype dt = Datatype.valueOf("unset");
			if(attributeListElement.getChildText("DATATYPE")!=null){
				dt = Datatype.valueOf(attributeListElement.getChildText("DATATYPE"));
			}
			AttributeList al = new AttributeList(
					attributeListElement.getChildText("TYPE"),
					attributeListElement.getChildText("FEATURE"),
					dt,
					Integer.parseInt(attributeListElement.getChildText("FROM")),
					Integer.parseInt(attributeListElement.getChildText("TO"))
			);
			this.attributelists.add(al);
		}
	}
	
	public List<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(List<Attribute> attributes) {
		this.attributes = attributes;
	}

	public URL getUrl() {
		return url;
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public List<Ngram> getNgrams() {
		return ngrams;
	}

	public void setNgrams(List<Ngram> ngrams) {
		this.ngrams = ngrams;
	}

	public List<AttributeList> getAttributelists() {
		return attributelists;
	}

	public void setAttributelists(List<AttributeList> attributelists) {
		this.attributelists = attributelists;
	}
}
