package gate.learningframework;

import gate.creole.ResourceInstantiationException;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;


public class ConfigurationFile {

	private org.jdom.Document jdomDocConf = null;
	
	private List<Attribute> attributes = new ArrayList<Attribute>();
	
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
	
	public enum Datatype {
		nominal, numeric, unset;
	}
	
	public ConfigurationFile(URL configFileURL) throws ResourceInstantiationException{
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
		
		init();
	}

	private void init(){
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
		
	}
	

	public String getEngine(){
		Element rootElement = this.jdomDocConf.getRootElement();
		return rootElement.getChildText("ENGINE");
	}
	
	public String getEvaluationType(){
		Element rootElement = jdomDocConf.getRootElement();
		Element evaluationElement = rootElement.getChild("EVALUATIONMETHOD");
		return evaluationElement.getValue();
	}

	public double getTrainingPortion(){
		Element rootElement = jdomDocConf.getRootElement();
		Element evaluationElement = rootElement.getChild("TRAININGPORTION");
		return Double.parseDouble(evaluationElement.getValue());
	}

	public int getFolds(){
		Element rootElement = jdomDocConf.getRootElement();
		Element evaluationElement = rootElement.getChild("FOLDS");
		return Integer.parseInt(evaluationElement.getValue());
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
}
