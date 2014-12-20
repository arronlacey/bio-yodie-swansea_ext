package gate.learningframework;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.log4j.Logger;

import cc.mallet.types.FeatureVector;
import gate.Document;

public abstract class CorpusWriter {

	static final Logger logger = Logger.getLogger("CorpusWriter");
	
	/**
	 * The annotation set from which to draw the annotations.
	 */
	private String inputASName;

	/**
	 * The annotation type to be treated as instance.
	 */
	private String instanceName;

	private PrintStream outputStream;

	public void setOutputFile(File outputFile) {
		this.outputFile = outputFile;
	}

	public File getOutputFile() {
		return this.outputFile;
	}


	public void setInputASName(String iasn) {
		this.inputASName = iasn;
	}

	public String getInputASName() {
		return this.inputASName;
	}


	public void setInstanceName(String inst) {
		this.instanceName = inst;
	}

	public String getInstanceName() {
		return this.instanceName;
	}

	private FeatureSpecification conf = null;

	private File outputFile;
	
	private Mode mode;
	
	private String classType;
	
	private String classFeature;
	
	private String identifierFeature;

	public CorpusWriter(FeatureSpecification conf, String inst, String inpas, 
			File outputFile, Mode mode, String classType, String classFeature,
			String identifierFeature){
		this.conf = conf;
		this.instanceName = inst;
		this.inputASName = inpas;
		this.outputFile = outputFile;
		this.mode = mode;
		this.classType = classType;
		this.classFeature = classFeature;
		this.identifierFeature = identifierFeature;
	}



	public void initializeOutputStream() {
		if(outputFile.exists()){
			outputFile.delete();
		}
		try {
			outputFile.createNewFile();
		} catch (Exception e){
			e.printStackTrace();
		}
		try {
			outputStream = new PrintStream(new FileOutputStream(outputFile, true));
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public static FeatureVector[] trim(FeatureVector[] array){
		if(array.length>0 && array[0]==null){
			FeatureVector[] arraytoreturn = new FeatureVector[array.length-1];
			for(int i=1;i<array.length;i++){
				arraytoreturn[i-1]=array[i];
			}
			return trim(arraytoreturn);
		} else {
			return array;
		}
	}
	
	public abstract void add(Document doc);
	
	public abstract void conclude();

	public PrintStream getOutputStream() {
		return outputStream;
	}

	public void setOutputStream(PrintStream outputStream) {
		this.outputStream = outputStream;
	}

	public FeatureSpecification getConf() {
		return conf;
	}

	public void setConf(FeatureSpecification conf) {
		this.conf = conf;
	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public String getClassType() {
		return classType;
	}

	public void setClassType(String classType) {
		this.classType = classType;
	}

	public String getClassFeature() {
		return classFeature;
	}

	public void setClassFeature(String classFeature) {
		this.classFeature = classFeature;
	}

	public String getIdentifierFeature() {
		return identifierFeature;
	}

	public void setIdentifierFeature(String identifierFeature) {
		this.identifierFeature = identifierFeature;
	}
}
