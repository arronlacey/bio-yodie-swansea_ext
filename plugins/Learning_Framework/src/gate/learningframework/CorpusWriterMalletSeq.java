/*
 * CorpusWriterMalletSeq.java
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
import gate.learningframework.FeatureSpecification.AttributeList;
import gate.learningframework.FeatureSpecification.Ngram;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import bsh.This;
import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.FeatureSequence2FeatureVector;
import cc.mallet.pipe.Input2CharSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.Target2LabelSequence;
import cc.mallet.pipe.TargetStringToFeatures;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelSequence;

public class CorpusWriterMalletSeq extends CorpusWriter{

	//SequenceSpan is a meaningful unit for the sequence. Mallet sequence
	//learners want to do an entire sequence, for example, a whole sentence
	//of tokens. So we need to define sequence span.
	private String sequenceSpan;

	private InstanceList instances; 
	
	private SerialPipes pipe;
	
	public CorpusWriterMalletSeq(FeatureSpecification conf, String inst, String inpas, 
			File outputFile, String sequenceSpan, Mode mode, String classType, 
			String classFeature, String identifierFeature){
		super(conf, inst, inpas, outputFile, mode, classType, classFeature, identifierFeature);
		this.sequenceSpan = sequenceSpan;

		/*
		 * Mallet requires data to be passed through a pipe to create an instance.
		 * The pipe not only ensures that instances have the same format at
		 * train and application time, but also ensures they have the same
		 * "alphabet". The alphabet maps from feature (such as string) to
		 * index in the vectors used, so it's really important to use the
		 * same pipe so as to use the same alphabet.
		 */

		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		
		//Split on # when we create the instances.
		Pattern tokenPattern =
				Pattern.compile("[^#]+");

		//Prepare the data as required
		pipeList.add(new Input2CharSequence("UTF-8"));
		//pipeList.add(new CharSequence2TokenSequence(tokenPattern));
		//pipeList.add(new TokenSequence2FeatureSequence());
		
		//The next element is a class I wrote for this application, not
		//part of standard Mallet
		pipeList.add(new FeatureValueString2FeatureVectorSequence(tokenPattern));

		//Prepare the target as required. I wrote this one too.
		pipeList.add(new TargetStringToFeatureSequence(tokenPattern));

		//pipeList.add(new PrintInputAndTarget());
		this.pipe = new SerialPipes(pipeList);
		
		this.instances = new InstanceList(this.pipe);
	}

	/**
	 * Prints instances list to the output location.
	 */
	public void append(Document doc){	
		//Not implemented
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

		AnnotationSet spans = inputAS.get(this.sequenceSpan);

		Iterator<Annotation> spansIt = spans.iterator();
		while(spansIt.hasNext()){
			Annotation span = spansIt.next();
			Instance inst = sequenceInstanceFromSpanAnnotation(
					this.getConf(), this.getInputASName(), doc, 
					span, this.getInstanceName(), this.getMode(), 
					this.getClassType(), this.getClassFeature());
			//ALWAYS add through the pipe, to get the instance right.
			
			this.instances.addThruPipe(inst);
		}
	}
	
	/**
	 * Builds a Mallet instance based on the config file and returns it.
	 */
	public static Instance sequenceInstanceFromSpanAnnotation(
			FeatureSpecification conf, String inputASname, 
			Document doc, Annotation spanAnn, String instanceAnn, 
			Mode mode, String type, String feature){

		AnnotationSet inputAS = doc.getAnnotations(inputASname);
		
		List<Annotation> instances = gate.Utils.getContainedAnnotations(
				inputAS, spanAnn, instanceAnn).inDocumentOrder();

		String data = "";
		String classEl = "";
		Iterator<Annotation> instanceAnnotationsIterator = instances.iterator();
		while(instanceAnnotationsIterator.hasNext()){
			Annotation instanceAnnotation = instanceAnnotationsIterator.next();

			//One class per instance, #-separated
			if(type!=null){
				switch(mode){
				case CLASSIFICATION:
					classEl = classEl + "#" + FeatureExtractor.extractClassForClassification(
							type, feature, inputASname, instanceAnnotation, 
							doc).replace("#", "[hash]");
					break;
				case NAMED_ENTITY_RECOGNITION:
					if(feature!=null){
						classEl = classEl + "#" + FeatureExtractor.extractClassNER(
							type, inputASname, instanceAnnotation, 
							doc);
					}
					break;
				}
			} else {
				//If we have no class type, we can't extract class.
				//This is probably being called in application mode.
			}

			String thisFeat = "";			
			//Arbitrary number of attributes and n-grams, space separated
			//within the instance
			List<Attribute> attributeList = conf.getAttributes();
			for(int i=0;i<attributeList.size();i++){
				Attribute att = attributeList.get(i);
				thisFeat = thisFeat + " " + FeatureExtractor.extractSingleFeature(
						att, inputASname, instanceAnnotation, 
						doc).replace("#", "[hash]");
			}

			List<Ngram> ngramList = conf.getNgrams();
			for(int i=0;i<ngramList.size();i++){
				Ngram ng = ngramList.get(i);
				thisFeat = thisFeat + " " + FeatureExtractor.extractNgramFeature(
						ng, inputASname, instanceAnnotation, 
						doc, " ").replace("#", "[hash]");
			}

			List<AttributeList> attributeListList = conf.getAttributelists();
			for(int i=0;i<attributeListList.size();i++){
				AttributeList al = attributeListList.get(i);
				thisFeat = thisFeat + " " + FeatureExtractor.extractRangeFeature(
						al, inputASname, instanceAnnotation, 
						doc, " ").replace("#", "[hash]");
			}
			
			//To distinguish between different instances, we use a
			//different separator.
			data = data + "#" + thisFeat.trim();
		}

		//Remove leading #
		if(classEl.length()>0){classEl = classEl.substring(1);}
		if(data.length()>0){data = data.substring(1);}
		return new Instance(data, classEl, "", "");
	}

	public void conclude(){
		//Doesn't need to do anything for Mallet output
	}

	public InstanceList getInstances() {
		return instances;
	}

}
