/*
 * CorpusWriterArffNumericClass.java
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

public class CorpusWriterArffNumericClass extends CorpusWriterArff {

	public CorpusWriterArffNumericClass(FeatureSpecification conf,
			String inst, String inpas, File outputDirectory, Mode mode,
			String classType, String classFeature, String identifierFeature,
			SerialPipes savedPipe) {
		super(conf, inst, inpas, outputDirectory, mode, classType, classFeature,
				identifierFeature, null);
		
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

		if(savedPipe==null){ //We need to create one
			//Prepare the data as required
			pipeList.add(new Input2CharSequence("UTF-8"));
			pipeList.add(new FeatureValueString2FeatureVector());
			
			//pipeList.add(new PrintInputAndTarget());
			this.setPipe(new SerialPipes(pipeList));
		} else {
			pipeList = savedPipe.pipes();

			this.pipe = new SerialPipes(pipeList);
			this.pipe.getDataAlphabet().stopGrowth();
			
			outputfile = outputfilenamearffpipe;
		}

		this.setInstances(new InstanceList(this.getPipe()));
	}

	/*
	 * For sparse format ARFF, all attributes are numeric.
	 * The header isn't sparse. We have to write them all out which is tedious
	 * but trivial.
	 */
	public void conclude(){
		this.initializeOutputStream(outputfile);
		//First the header
		this.getOutputStream(outputfile).print("@relation gate\n\n");

		for(int i=0;i<this.getPipe().getDataAlphabet().size();i++){
			String attributeName = (String)this.getPipe().getDataAlphabet().lookupObject(i);

			//Replace characters that arff doesn't like
			attributeName = attributeName.replace("\"", "[quote]");
			attributeName = attributeName.replace("\\", "[backslash]");
			
			this.getOutputStream(outputfile).print("@attribute \"" 
					+ attributeName + "\" numeric\n");
		}
		
		//The class attribute is nominal
		this.getOutputStream(outputfile).print("@attribute class numeric\n\n");
		
		//Now the data
		this.getOutputStream(outputfile).print("@data\n");
		
		Iterator<Instance> instit = this.getInstances().iterator();
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

			double target = (Double)inst.getTarget();
			//Use the data alphabet size as the index for class because it is surely free
			if(data.getIndices().length>0){
				output = output + ", ";
			}
			output = output + this.getPipe().getDataAlphabet().size() + " " + target + "}\n";
			
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

	/**
	 * Overrides add method to assume numeric target.
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
			Instance inst = 
					CorpusWriterMallet.instanceFromInstanceAnnotationNumericClass(
					this.getConf(), instanceAnnotation, this.getInputASName(), doc,
					this.getMode(), this.getClassType(), this.getClassFeature(),
					this.getIdentifierFeature());

			//Always add instances to the instance list through the pipe.
			try{
				this.getInstances().addThruPipe(inst);
			} catch(Exception e){
				System.out.println(inst.getData().toString());
				e.printStackTrace();
			}
		}
	}

	/**Convert the Mallet instances into Weka instances.
	 * Assumes numeric class.
	 */
	public Instances getWekaInstances(){
		Instances wekaInstances = 
				CorpusWriterArffNumericClass.malletPipeToWekaDataset(this.getPipe());
		
		Iterator<Instance> instit = this.getInstances().iterator();
		while(instit.hasNext()){
			Instance inst = instit.next();
			weka.core.Instance wekaInstance = 
					malletInstance2WekaInstanceNumericClass(inst, wekaInstances);
			wekaInstances.add(wekaInstance);
		}
		return wekaInstances;
	}
	
	public static weka.core.Instance malletInstance2WekaInstanceNumericClass(
			cc.mallet.types.Instance malletInstance, weka.core.Instances dataset){		
		
		weka.core.Instance wekaInstance = malletInstance2WekaInstanceNoTarget(
				malletInstance, dataset);
				
		//Can we format the target as a double?
		double target = 0.0;
		try{
			target = (Double)malletInstance.getTarget();
		} catch (ClassCastException e){
			logger.warn("LearningFramework: Failed to cast target "
					+ "to double for classifier requiring numeric target.");
		}
		wekaInstance.setValue(dataset.classIndex(), target);
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
		
		weka.core.Attribute classatt = new weka.core.Attribute("class");
		atts.addElement(classatt);
		
		Instances wekaInstances = new Instances("GATE", atts, 0);
		wekaInstances.setClass(classatt);
		return wekaInstances;
	}
}
