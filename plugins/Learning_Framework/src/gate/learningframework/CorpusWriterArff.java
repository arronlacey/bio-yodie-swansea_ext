/*
 * CorpusWriterArff.java
 *  
 * Copyright (c) 1995-2015, The University of Sheffield. See the file
 * COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 * Copyright 2015 South London and Maudsley NHS Trust and King's College London
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 *
 * Genevieve Gorrell, 9 Jan 2015
 */

package gate.learningframework;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.learningframework.FeatureSpecification.Attribute;
import gate.learningframework.FeatureSpecification.Datatype;
import gate.learningframework.FeatureSpecification.Ngram;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;

import weka.core.FastVector;
import weka.core.Instances;
import cc.mallet.pipe.FeatureValueString2FeatureVector;
import cc.mallet.pipe.Input2CharSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public class CorpusWriterArff extends CorpusWriter{

	private InstanceList instances;
	
	SerialPipes pipe;

	public CorpusWriterArff(FeatureSpecification conf, String inst, String inpas, 
			File outputDirectory, Mode mode, String classType, String classFeature,
			String identifierFeature, SerialPipes savedPipe){
		super(conf, inst, inpas, outputDirectory, mode, classType, classFeature, identifierFeature);

		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		if(savedPipe==null){ //We need to create one
			/*
			 * Again we are using Mallet's feature preparation functionality.
			 * This pipe will turn a bunch of strings into something we can
			 * work with.
			 * 
			 * Weka has similar functionality but we run into problems with n-gram
			 * features, which are arbitrary in number. Ultimately, ARFF wants to 
			 * know how many features there are, so we need to know. ARFF will take
			 * string features but it still wants to know how many there are going to
			 * be. So we'll not use it.
			 * 
			 * One down side to expanding every different string out into a separate
			 * feature is that we lose groupings. Three of our features might be
			 * alternate string values for one feature in the specification file, and
			 * are mutually exclusive--only one of them will be "1.0" and the rest
			 * will be zeros. We lose that information. Not sure how much of a problem
			 * that is, and it isn't clear that Weka has a solution to that anyway.
			 */
			
			//Prepare the data as required
			pipeList.add(new Input2CharSequence("UTF-8"));
			pipeList.add(new FeatureValueString2FeatureVector());
	
			//Prepare the target as required
			pipeList.add(new Target2Label());
			
			//pipeList.add(new PrintInputAndTarget());
			this.pipe = new SerialPipes(pipeList);
		} else { //Reusing existing pipe
			pipeList = savedPipe.pipes();

			this.pipe = new SerialPipes(pipeList);
			this.pipe.getDataAlphabet().stopGrowth();
			
			if(this.pipe.getTargetAlphabet()==null){
				logger.warn("LearningFramework: Target alphabet missing, perhaps "
						+ "because this pipe was created on data with a numerical "
						+ "target?");
			} else {
				this.pipe.getTargetAlphabet().stopGrowth();
			}
			
			outputfile = outputfilenamearffpipe;
		}

		this.instances = new InstanceList(this.pipe);
	}

	//For ARFF, we need to list all the possible attribute values in the header,
	//so we accumulate them in this map and paste them in the top at the end.
	private Map<String, Set<String>> nominalAttributeMap = new HashMap<String, Set<String>>();

	public void resetNominalValues(){
		this.nominalAttributeMap = new HashMap<String, Set<String>>();
	}

	/*
	 * For sparse format ARFF, all attributes are numeric, aside from class.
	 * The header isn't sparse. We have to write them all out which is tedious
	 * but trivial.
	 */
	public void conclude(){
		this.initializeOutputStream(outputfile);
		//First the header
		this.getOutputStream(outputfile).print("@relation gate\n\n");

		for(int i=0;i<pipe.getDataAlphabet().size();i++){
			String attributeName = (String)pipe.getDataAlphabet().lookupObject(i);
			
			//Replace characters that arff doesn't like
			attributeName = attributeName.replace("\"", "[quote]");
			attributeName = attributeName.replace("\\", "[backslash]");
			
			this.getOutputStream(outputfile).print("@attribute \"" 
					+ attributeName + "\" numeric\n");
		}
		
		//The class attribute is nominal
		this.getOutputStream(outputfile).print("@attribute class {");

		String clkey = this.getClassType() + "-" + this.getClassFeature();
		
		Set<String> clvalues = this.nominalAttributeMap.get(clkey);
		List<String> ordered = new ArrayList<String>();
		ordered.addAll(clvalues);
		Collections.sort(ordered);
		Iterator<String> oit = ordered.iterator();
		if(oit.hasNext()){
			this.getOutputStream(outputfile).print(oit.next());
		}
		while(oit.hasNext()){
			this.getOutputStream(outputfile).print(", " + oit.next());
		}
		this.getOutputStream(outputfile).print("}\n\n");
		
		//Now the data
		this.getOutputStream(outputfile).print("@data\n");
		
		Iterator<Instance> instit = this.instances.iterator();
		while(instit.hasNext()){
			Instance inst = instit.next();
			
			String output = "{";
			FeatureVector data = (FeatureVector)inst.getData();
			if(data.getIndices().length>0){
				output = "{" + data.getIndices()[0] + " " + data.getValues()[0];
			}
			for(int i=1;i<data.getIndices().length;i++){
				int index = data.getIndices()[i];
				double value = data.getValues()[i];
				output = output + ", " + index + " " + value;
			}

			String target = inst.getTarget().toString();
			//Use the data alphabet size as the index for class because it is surely free
			if(data.getIndices().length>0){
				output = output + ", ";
			}
			output = output + pipe.getDataAlphabet().size() + " " + target + "}\n";
			
			this.getOutputStream(outputfile).print(output);
		}
		
		this.getOutputStream(outputfile).flush();
		
		//Save the pipe
		try {
			File pf = new File(this.getOutputDirectory(), pipefilenamearff);
			ObjectOutputStream oos = new ObjectOutputStream
					(new FileOutputStream(pf));
			oos.writeObject(this.pipe);
			oos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


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
			Instance inst = CorpusWriterMallet.instanceFromInstanceAnnotation(
					this.getConf(), instanceAnnotation, this.getInputASName(), doc,
					this.getMode(), this.getClassType(), this.getClassFeature(),
					this.getIdentifierFeature());

			//Need to total up all the possible classes
			String clkey = this.getClassType() + "-" + this.getClassFeature();
			Set<String> clvalues = this.nominalAttributeMap.get(clkey);
			if(clvalues==null){
				clvalues = new HashSet<String>();
				this.nominalAttributeMap.put(clkey, clvalues);
			}
			clvalues.add((String)inst.getTarget());
			
			//Always add instances to the instance list through the pipe.
			try{
				this.instances.addThruPipe(inst);
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public static weka.core.Instance malletInstance2WekaInstanceNoTarget(
			cc.mallet.types.Instance malletInstance, weka.core.Instances dataset){		
		//Mallet sparse format
		FeatureVector data = (FeatureVector)malletInstance.getData();
		int[] indices = data.getIndices();
		double[] vals = data.getValues();

		weka.core.Instance wekaInstance = new weka.core.Instance(dataset.numAttributes());

		//Initialize to 0 otherwise Weka will assume missing where
		//Mallet assumes a known negative for missing atts in the sparse
		//vector. This sucks because now it isn't sparse. Can't see any
		//way to get a sparse vector with zero default in Weka.
		for(int i=0;i<dataset.numAttributes();i++){
			wekaInstance.setValue(i, 0);
		}

		for(int i=0;i<indices.length;i++){
			int index = indices[i];
			double value = vals[i];
			
			//At apply time, we might find ourselves with a new feature.
			//If so, we'll just ignore it. It's no use for anything.
			if(index<dataset.numAttributes()){
				wekaInstance.setValue(index, value);
			}
		}

		wekaInstance.setDataset(dataset);
		return wekaInstance;
	}

	/*
	 * Continuing our policy of using Mallet for all feature prep then
	 * doing a trivial conversion to get the requirements for other
	 * libraries ..
	 * Ultimately all these libraries want to know what the feature
	 * set is, even at apply time. Mallet keeps that stuff in a pipe
	 * (the "alphabet"). Weka has a concept of a dataset. It seems 
	 * strange to have to have to have an instance at classification 
	 * time belonging to a dataset but that's just where Weka gets the 
	 * alphabet from. We're already storing the pipe so we have it
	 * available at apply time, but we need to turn it into a Weka
	 * dataset.
	 */
	public static Instances malletPipeToWekaDataset(Pipe staticPipe){
		FastVector atts = new FastVector();
		for(int i=0;i<staticPipe.getDataAlphabet().size();i++){
			String attributeName = (String)staticPipe.getDataAlphabet().lookupObject(i);
			atts.addElement(new weka.core.Attribute(attributeName));
		}
		
		//Nominal class should be fully expanded out
		FastVector classVals = new FastVector();
		for(int i=0;i<staticPipe.getTargetAlphabet().size();i++){
			classVals.addElement((String)staticPipe.getTargetAlphabet().lookupObject(i));
		}
		weka.core.Attribute classatt = new weka.core.Attribute("class", classVals);
		atts.addElement(classatt);
		
		Instances wekaInstances = new Instances("GATE", atts, 0);
		wekaInstances.setClass(classatt);
		return wekaInstances;
	}
		
	public Instances[] splitWekaInstances(Instances all, float trainingproportion){
		int numTrainingInstances = Math.round(instances.size()*trainingproportion);
		int numTestInstances = instances.size()-numTrainingInstances;
		if(numTrainingInstances<1 || numTestInstances<1){
			logger.warn("LearningFramework: Empty test or training set.");
		}
		
		Instances toShuffle = new Instances(all);
		toShuffle.randomize(new Random(1));
		Instances trainingInstances = new Instances(toShuffle, 0, numTrainingInstances);
		Instances testInstances = new Instances(toShuffle, numTrainingInstances, numTestInstances);
		
		return new Instances[]{trainingInstances, testInstances};
	}

	/**Convert the Mallet instances into Weka instances.
	 * Assumes nominal class.
	 */
	public Instances getWekaInstances(){
		Instances newSet = malletPipeToWekaDataset(this.getInstances().getPipe());
		
		Iterator<Instance> instit = this.getInstances().iterator();
		while(instit.hasNext()){
			Instance inst = instit.next();
			weka.core.Instance wekaInstance = 
					malletInstance2WekaInstance(inst, newSet);
			newSet.add(wekaInstance);
		}
		return newSet;
	}

	public static weka.core.Instance malletInstance2WekaInstance(
			cc.mallet.types.Instance malletInstance, weka.core.Instances dataset){		
		
		weka.core.Instance wekaInstance = malletInstance2WekaInstanceNoTarget(
				malletInstance, dataset);
		
		wekaInstance.setValue(dataset.classIndex(), malletInstance.getTarget().toString());
		wekaInstance.setDataset(dataset);
		return wekaInstance;
	}
	
	public static SerialPipes getArffPipe(File outputDirectory){
		File pf = new File(outputDirectory, pipefilenamearff);
		SerialPipes pipe = null;
		if(pf.exists()){
			try {
				ObjectInputStream ois =
						new ObjectInputStream (new FileInputStream(pf));
				pipe = (SerialPipes) ois.readObject();
				ois.close();
			} catch(Exception e){
				e.printStackTrace();
			}
		}
		return pipe;
	}
	
	public InstanceList getInstances() {
		return instances;
	}

	public void setInstances(InstanceList instances) {
		this.instances = instances;
	}

	public SerialPipes getPipe() {
		return pipe;
	}

	public void setPipe(SerialPipes pipe) {
		this.pipe = pipe;
	}

	public Map<String, Set<String>> getNominalAttributeMap() {
		return nominalAttributeMap;
	}

	public void setNominalAttributeMap(Map<String, Set<String>> nominalAttributeMap) {
		this.nominalAttributeMap = nominalAttributeMap;
	}
}
