package gate.learningframework;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.learningframework.FeatureSpecification.Attribute;
import gate.learningframework.FeatureSpecification.AttributeList;
import gate.learningframework.FeatureSpecification.Ngram;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import libsvm.svm_node;
import libsvm.svm_problem;
import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.FeatureSequence2FeatureVector;
import cc.mallet.pipe.FeatureValueString2FeatureVector;
import cc.mallet.pipe.Input2CharSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.SparseVector;

public class CorpusWriterMallet extends CorpusWriter{

	private InstanceList instances;
	
	private SerialPipes pipe;
	
	public CorpusWriterMallet(FeatureSpecification conf, String inst, String inpas, 
			File outputFile, Mode mode, String classType, String classFeature,
			String identifierFeature){
		super(conf, inst, inpas, outputFile, mode, classType, classFeature, 
				identifierFeature);
		
		/*
		 * Mallet requires data to be passed through a pipe to create an instance.
		 * The pipe not only ensures that instances have the same format at
		 * train and application time, but also ensures they have the same
		 * "alphabet". The alphabet maps from feature (such as string) to
		 * index in the vectors used, so it's really important to use the
		 * same pipe so as to use the same alphabet.
		 */

		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		
		//Prepare the data as required
		pipeList.add(new Input2CharSequence("UTF-8"));
		pipeList.add(new FeatureValueString2FeatureVector());

		//Prepare the target as required
		pipeList.add(new Target2Label());

		//pipeList.add(new PrintInputAndTarget());
		this.pipe = new SerialPipes(pipeList);
		
		this.instances = new InstanceList(this.pipe);
	}

	/**
	 * Given a document, adds the required instances to the instance list.
	 */
	public void add(Document doc){
		//  Get the output annotation set
		AnnotationSet inputAS = null;
		if(this.getInputASName() == null || this.getInputASName().equals("")) {
			inputAS = doc.getAnnotations();
		} else {
			inputAS = doc.getAnnotations(this.getInputASName());
		}

		List<Annotation> instances = inputAS.get(this.getInstanceName()).inDocumentOrder();
		Iterator<Annotation> instanceAnnotationsIterator = instances.iterator();
		while(instanceAnnotationsIterator.hasNext()){
			Annotation instanceAnnotation = instanceAnnotationsIterator.next();
			Instance inst = instanceFromInstanceAnnotation(
					this.getConf(), instanceAnnotation, this.getInputASName(), doc,
					this.getMode(), this.getClassType(), this.getClassFeature(),
					this.getIdentifierFeature());
			
			//Always add instances to the instance list through the pipe.
			try{
				this.instances.addThruPipe(inst);
			} catch(Exception e){
				System.out.println(inst.getData().toString());
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Prints instances list to the output location.
	 */
	public void append(Document doc){	 
		String output = "";  

		//  Get the output annotation set
		AnnotationSet inputAS = null;
		if(this.getInputASName() == null || this.getInputASName().equals("")) {
			inputAS = doc.getAnnotations();
		} else {
			inputAS = doc.getAnnotations(this.getInputASName());
		}

		List<Annotation> instances = inputAS.get(this.getInstanceName()).inDocumentOrder();
		Iterator<Annotation> instanceAnnotationsIterator = instances.iterator();
		while(instanceAnnotationsIterator.hasNext()){
			Annotation instanceAnnotation = instanceAnnotationsIterator.next();
			output = instanceStringFromInstanceAnnotation(
					instanceAnnotation, this.getInputASName(), doc, " ",
					this.getMode(), this.getClassType(), this.getClassFeature(),
					this.getIdentifierFeature());
			//System.out.println(">>" + output);
			this.getOutputStream().print(output + "\n");
		}
		this.getOutputStream().flush();
	}

	/**
	 * Builds a Mallet instance based on the config file and returns it.
	 * The instance is unpiped. It's just a bunch of strings that needs more
	 * work later on before it can be used as input.
	 */
	public static Instance instanceFromInstanceAnnotationNoClass(
			FeatureSpecification conf, Annotation instanceAnnotation, 
			String inputASname, Document doc, Mode mode, String identifierFeature){
		String data = "";
		String identifier = "";
		
		identifier = FeatureExtractor.extractIdentifier(
				instanceAnnotation.getType(), identifierFeature, inputASname, 
				instanceAnnotation, doc);

		List<Attribute> attributeList = conf.getAttributes();
		for(int i=0;i<attributeList.size();i++){
			Attribute att = attributeList.get(i);
			data = data + " " + FeatureExtractor.extractSingleFeature(
					att, inputASname, instanceAnnotation, doc);
		}

		List<Ngram> ngramList = conf.getNgrams();
		for(int i=0;i<ngramList.size();i++){
			Ngram ng = ngramList.get(i);
			String str = FeatureExtractor.extractNgramFeature(
					ng, inputASname, instanceAnnotation, doc, " ");
			data = data + " " + str;
		}

		List<AttributeList> attributeListList = conf.getAttributelists();
		for(int i=0;i<attributeListList.size();i++){
			AttributeList al = attributeListList.get(i);
			String str = FeatureExtractor.extractRangeFeature(
					al, inputASname, instanceAnnotation, doc, " ");
			data = data + " " + str;
		}
		data = data.trim();

		Instance instance = new Instance(data, "", identifier, "");
		return instance;
	}

	/**
	 * Builds a Mallet instance based on the config file and returns it.
	 * The instance is unpiped. It's just a bunch of strings that needs more
	 * work later on before it can be used as input. Class is included;
	 * this method is used to prepare training data.
	 */
	public static Instance instanceFromInstanceAnnotation(
			FeatureSpecification conf, Annotation instanceAnnotation, 
			String inputASname, Document doc, Mode mode, String classType, 
			String classFeature, String identifierFeature){
		String classEl = "";

		if(classType!=null && classFeature!=null){
			switch(mode){
			case CLASSIFICATION:
				classEl = FeatureExtractor.extractClassForClassification(
						classType, classFeature, inputASname, instanceAnnotation, doc);
				break;
			case NAMED_ENTITY_RECOGNITION:
				classEl = FeatureExtractor.extractClassNER(
						classType, inputASname, instanceAnnotation, doc);
				break;
			}
		}
		
		Instance instance = instanceFromInstanceAnnotationNoClass(
				conf, instanceAnnotation, inputASname, doc, mode, identifierFeature);

		instance.setTarget(classEl);
		return instance;
	}

	/**
	 * Builds a Mallet instance based on the config file and returns it.
	 * The instance is unpiped. It's just a bunch of strings that needs more
	 * work later on before it can be used as input. Class is included;
	 * this method is used to prepare training data.
	 */
	public static Instance instanceFromInstanceAnnotationNumericClass(
			FeatureSpecification conf, Annotation instanceAnnotation, 
			String inputASname, Document doc, Mode mode, String classType, 
			String classFeature, String identifierFeature){
		double classEl = 0.0;

		if(classType!=null && classFeature!=null){
			switch(mode){
			case CLASSIFICATION:
				classEl = FeatureExtractor.extractNumericClassForClassification(
						classType, classFeature, inputASname, instanceAnnotation, doc);
				break;
			case NAMED_ENTITY_RECOGNITION:
				logger.warn("LearningFramework: Algorithm requiring numeric "
						+ "class cannot be used in NER mode.");
				break;
			}
		}
		
		Instance instance = instanceFromInstanceAnnotationNoClass(
				conf, instanceAnnotation, inputASname, doc, mode, identifierFeature);

		instance.setTarget(classEl);
		return instance;
	}
	
	/**
	 * Builds an instance as a string based on the config file and returns it.
	 * Untested since adding ngrams. Can't handle numeric classes.
	 */
	public String instanceStringFromInstanceAnnotation(Annotation instanceAnnotation, 
			String inputASname, Document doc, String separator, Mode mode, 
			String classType, String classFeature, String identifierFeature){
		String output = "";

		output = output + FeatureExtractor.extractIdentifier(
				instanceAnnotation.getType(), identifierFeature, inputASname, 
				instanceAnnotation, doc);

		switch(mode){
		case CLASSIFICATION:
			output = output + separator + FeatureExtractor.extractClassForClassification(
					classType, classFeature, inputASname, instanceAnnotation, doc);
			break;
		case NAMED_ENTITY_RECOGNITION:
			output = output + separator + FeatureExtractor.extractClassNER(
					classType, inputASname, instanceAnnotation, doc);
			break;
		}
		
		List<Attribute> attributes = this.getConf().getAttributes();
		for(int i=0;i<attributes.size();i++){
			Attribute att = attributes.get(i);
			output = output + separator + FeatureExtractor.extractSingleFeature(
					att, inputASname, instanceAnnotation, doc);
		}

		List<Ngram> ngrams = this.getConf().getNgrams();
		for(int i=0;i<ngrams.size();i++){
			Ngram ng = ngrams.get(i);
			output = output + separator + FeatureExtractor.extractNgramFeature(
					ng, inputASname, instanceAnnotation, doc, separator);
		}

		List<AttributeList> attributelists = this.getConf().getAttributelists();
		for(int i=0;i<attributelists.size();i++){
			AttributeList al = attributelists.get(i);
			output = output + separator + FeatureExtractor.extractRangeFeature(
					al, inputASname, instanceAnnotation, doc, separator);
		}

		return output;
	}

	/**
	 * Create and return a set of Mallet instances from the
	 * GATE data saved to file. Should probably improve this
	 * at some point to have a more unique separator, but it
	 * isn't currently being used anywhere so it's low priority.
	 */
	public InstanceList getInstancesFromFile(Pipe pipe){

		InstanceList instances = new InstanceList(pipe);

		Reader reader = null;
		try {
			reader = new FileReader(this.getOutputFile());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Pattern pat = Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$");
		//Pattern pat = Pattern.compile("(\\w+)\\s+(\\w+)\\s+(.*)");

		CsvIterator csvit = new CsvIterator(reader, pat, 3, 2, 1);
		instances.addThruPipe(csvit);

		//File savefile = new File(this.outputURL.getFile() + datafilename);
		//instances.save(savefile);
		return instances;
	}

	/*
	 * Creating an svm_problem for LibSVM works best as an add-on
	 * for the Mallet because Mallet has so much great feature
	 * prep stuff and it isn't hard to convert between the two
	 * sparse vector formats.
	 */
	public svm_problem getLibSVMProblem(){		
		svm_problem prob = new svm_problem();
		int numTrainingInstances = instances.size();
		prob.l=numTrainingInstances;
		prob.y=new double[prob.l];
		prob.x=new svm_node[prob.l][];
		
		for(int i=0;i<numTrainingInstances;i++){
			Instance instance = instances.get(i);
			
			//Labels
			prob.y[i] = ((Label)instance.getTarget()).getIndex();
			
			//Features
			SparseVector data = (SparseVector)instance.getData();
			int[] indices = data.getIndices();
			double[] values = data.getValues();
			prob.x[i]=new svm_node[indices.length];
			for(int j=0;j<indices.length;j++){
				svm_node node = new svm_node();
				node.index = indices[j];
				node.value = values[j];
				prob.x[i][j]=node;
			}
		}
		return prob;
	}
	
	/*
	 * Make LibSVM problems for training and test.
	 */
	public svm_problem[] getLibSVMProblemSplit(float trainingproportion){		
		svm_problem tr = new svm_problem();	
		svm_problem te = new svm_problem();
		int numTrainingInstances = Math.round(instances.size()*trainingproportion);
		int numTestInstances = instances.size()-numTrainingInstances;
		if(numTrainingInstances<1 || numTestInstances<1){
			logger.warn("LearningFramework: Empty test or training set.");
		}
		
		tr.l=numTrainingInstances;
		tr.y=new double[tr.l];
		tr.x=new svm_node[tr.l][];

		te.l=numTestInstances;
		te.y=new double[te.l];
		te.x=new svm_node[te.l][];
		
		InstanceList localinstances = (InstanceList)instances.clone();
		localinstances.shuffle(new Random(1)); //Better shuffle a copy, not the main one
		
		for(int i=0;i<numTrainingInstances;i++){
			Instance instance = localinstances.get(i);
			
			//Labels
			tr.y[i] = ((Label)instance.getTarget()).getIndex();
			
			//Features
			SparseVector data = (SparseVector)instance.getData();
			int[] indices = data.getIndices();
			double[] values = data.getValues();
			tr.x[i]=new svm_node[indices.length];
			for(int j=0;j<indices.length;j++){
				svm_node node = new svm_node();
				node.index = indices[j];
				node.value = values[j];
				tr.x[i][j]=node;
			}
		}
		
		for(int i=0;i<numTestInstances;i++){
			Instance instance = localinstances.get(i+numTrainingInstances); //Where we left off
			
			//Labels
			te.y[i] = ((Label)instance.getTarget()).getIndex();
			
			//Features
			SparseVector data = (SparseVector)instance.getData();
			int[] indices = data.getIndices();
			double[] values = data.getValues();
			te.x[i]=new svm_node[indices.length];
			for(int j=0;j<indices.length;j++){
				svm_node node = new svm_node();
				node.index = indices[j];
				node.value = values[j];
				te.x[i][j]=node;
			}
		}
		
		return new svm_problem[]{tr, te};
	}
	
	/*
	 * Making a single LibSVM node array for classification from a
	 * Mallet instance is easier than making it from scratch.
	 */
	public static svm_node[] getLibSVMVectorForInst(Instance instance){
		SparseVector data = (SparseVector)instance.getData();
		int[] indices = data.getIndices();
		double[] values = data.getValues();
		svm_node[] nodearray = new svm_node[indices.length];
		int index = 0;
		for(int j=0;j<indices.length;j++){
			svm_node node = new svm_node();
			node.index = indices[j];
			node.value = values[j];
			nodearray[index]=node;
			index++;
		}
		return nodearray;
	}
	
	public void conclude(){
		//Doesn't need to do anything for Mallet output
	}

	public InstanceList getInstances() {
		return instances;
	}

	public void setInstances(InstanceList instances) {
		this.instances = instances;
	}
}
