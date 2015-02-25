/*
 * EngineMallet.java
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
import gate.creole.ResourceInstantiationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import cc.mallet.classify.BalancedWinnowTrainer;
import cc.mallet.classify.C45Trainer;
import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.DecisionTreeTrainer;
import cc.mallet.classify.MCMaxEntTrainer;
import cc.mallet.classify.MaxEntGERangeTrainer;
import cc.mallet.classify.MaxEntGETrainer;
import cc.mallet.classify.MaxEntPRTrainer;
import cc.mallet.classify.MaxEntTrainer;
import cc.mallet.classify.NaiveBayesEMTrainer;
import cc.mallet.classify.NaiveBayesTrainer;
import cc.mallet.classify.WinnowTrainer;
import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.FeatureSequence2FeatureVector;
import cc.mallet.pipe.Input2CharSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PrintInputAndTarget;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.types.InstanceList.CrossValidationIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;

public class EngineMallet extends Engine {

	private Classifier classifier = null;

	/**
	 * The name of the classifier location.
	 */
	private static String classifiername = new String("my.classifier");
	
	private String params;

	public EngineMallet(File savedModel, Mode mode, String params, String engine, boolean restore){	
		this(savedModel, mode, engine, restore);
		this.params = params;
	}

	public EngineMallet(File savedModel, Mode mode, String engine, boolean restore){

		this.setOutputDirectory(savedModel);
		this.setEngine(engine);
		this.setMode(mode);

		//Restore the classifier and the saved copy of the configuration file
		//from train time.
		if(restore){
			File clf = new File(this.getOutputDirectory(), classifiername);
			if(clf.exists()){	
				try {
					this.classifier = loadClassifier(clf);
				} catch(Exception e){
					e.printStackTrace();
				}

				URL confURL = null;
				try {
					confURL = new URL(
							this.getOutputDirectory().toURI().toURL(), 
							Engine.getSavedConf());
				} catch (MalformedURLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				try {
					this.setSavedConfFile(new FeatureSpecification(confURL));
				} catch (ResourceInstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			} else {
				//If we can't restore the classifier, no point trying to restore
				//anything else. It's a failed restore. Ah well.
			}
		}
	}

	/**
	 * The standard way to save classifiers and Mallet data 
	 * for repeated use is through Java serialization. 
	 * Here we load a serialized classifier from a file.
	 * 
	 */
	public Classifier loadClassifier(File serializedFile)
			throws FileNotFoundException, IOException, ClassNotFoundException {

		if(serializedFile.exists()){
			Classifier classifier;

			ObjectInputStream ois =
					new ObjectInputStream (new FileInputStream(serializedFile));
			classifier = (Classifier) ois.readObject();
			ois.close();

			return classifier;
		} else {
			return null;
		}
	}

	/**
	 * Get the right trainer depending on what the user specified.
	 * @return
	 */
	public ClassifierTrainer getTrainer(){

		ClassifierTrainer trainer = null;
		if(this.getEngine().equals("MALLET_CL_BALANCED_WINNOW")){
			double epsilon = -1.0;
			double delta = -1.0;
			int maxiterations = -1;
			double coolingrate = -1.0;
			if(params!=null && !params.isEmpty()){
				String[] p = params.split("\\s+");
				if(p.length==4){
					try {
						epsilon = Double.parseDouble(p[0]);
						delta = Double.parseDouble(p[1]);
						maxiterations = Integer.parseInt(p[2]);
						coolingrate = Double.parseDouble(p[3]);
					} catch(NumberFormatException e){
						logger.warn("LearningFramework: Failed to format parameter "
								+ "as double for Winnow trainer: " + e + " Proceeding "
										+ "with no parameters.");
						trainer = new BalancedWinnowTrainer(epsilon, delta, 
								maxiterations, coolingrate);
					}
					trainer = new BalancedWinnowTrainer();
				} else {
					logger.warn("LearningFramework: Incorrect number of parameters "
							+ "for Balanced Winnow trainer: " + p.length + ". Proceeding with "
									+ "no parameters.");
					trainer = new BalancedWinnowTrainer();
				}
			} else {
				trainer = new BalancedWinnowTrainer();
			}
		} else if(this.getEngine().equals("MALLET_CL_C45")){
			int maxdepth = -1;
			if(params!=null && !params.isEmpty()){
				try{
					maxdepth = Integer.parseInt(params);
				} catch(NumberFormatException e){
					logger.warn("LearningFramework: Failed to format parameter "
							+ "as integer for max depth of C45 trainer: " + e);
				}
			}
			if(maxdepth!=-1){
				trainer = new C45Trainer(maxdepth);
			} else {
				trainer = new C45Trainer();
			}
		} else if(this.getEngine().equals("MALLET_CL_DECISION_TREE")){
			int maxdepth = -1;
			if(params!=null && !params.isEmpty()){
				try{
					maxdepth = Integer.parseInt(params);
				} catch(NumberFormatException e){
					logger.warn("LearningFramework: Failed to format parameter "
							+ "as integer for max depth of decision tree trainer: " + e);
				}
			}
			if(maxdepth!=-1){
				trainer = new DecisionTreeTrainer(maxdepth);
			} else {
				trainer = new DecisionTreeTrainer();
			}
		} else if(this.getEngine().equals("MALLET_CL_MAX_ENT")){
			double gaussianprior = -1.0;
			int maxiterations = -1;
			if(params!=null && !params.isEmpty()){
				String[] p = params.split("\\s+");
				if(p.length!=2){
					logger.warn("LearningFramework: Incorrect number of parameters "
							+ "for MaxEnt. Ignoring.");
				} else {
					try{
						gaussianprior = Double.parseDouble(p[0]);
					} catch(NumberFormatException e){
						logger.warn("LearningFramework: Failed to format parameter "
								+ "as double for gaussian prior of MaxEnt trainer: " + e);
					}
					try{
						maxiterations = Integer.parseInt(p[1]);
					} catch(NumberFormatException e){
						logger.warn("LearningFramework: Failed to format parameter "
								+ "as int for max iterations of MaxEnt trainer: " + e);
					}
				}
			}
			if(gaussianprior!=-1.0){
				trainer = new MaxEntTrainer(gaussianprior);
			} else {
				trainer = new MaxEntTrainer();
			}
			if(maxiterations!=-1){
				((MaxEntTrainer)trainer).setNumIterations(maxiterations);
			}
		} else if(this.getEngine().equals("MALLET_CL_MC_MAX_ENT")){
			double gaussianPriorVariance = -1.0;
			boolean useMultiConditionalTraining = false;
			double hyperbolicPriorSlope = -1.0;
			double hyperbolicPriorSharpness = -1.0;
			if(params!=null && !params.isEmpty()){
				String[] p = params.split("\\s+");
				if(p.length==2){
					if(p[1].equals("true") || p[1].equals("false")){
						try{
							gaussianPriorVariance = Double.parseDouble(p[0]);
							useMultiConditionalTraining = Boolean.parseBoolean(p[1]);
						} catch(NumberFormatException e){
							logger.warn("LearningFramework: Failed to format parameters "
									+ "for MC MaxEnt trainer: " + e + " Using no arguments.");
							trainer = new MCMaxEntTrainer();
						}
						trainer = new MCMaxEntTrainer(gaussianPriorVariance, useMultiConditionalTraining);
					} else {
						try{
							hyperbolicPriorSlope = Double.parseDouble(p[0]);
							hyperbolicPriorSharpness = Double.parseDouble(p[1]);
						} catch(NumberFormatException e){
							logger.warn("LearningFramework: Failed to format parameters "
									+ "for MC MaxEnt trainer: " + e + " Using no arguments.");
							trainer = new MCMaxEntTrainer();
						}
						trainer = new MCMaxEntTrainer(hyperbolicPriorSlope, hyperbolicPriorSharpness);
					}
				} else if(p.length==1){
					try{
						gaussianPriorVariance = Double.parseDouble(p[0]);
					} catch(NumberFormatException e){
						logger.warn("LearningFramework: Failed to format parameters "
								+ "for MC MaxEnt trainer: " + e + " Using no arguments.");
						trainer = new MCMaxEntTrainer();
					}
					trainer = new MCMaxEntTrainer(gaussianPriorVariance);
				} else {
					trainer = new MCMaxEntTrainer();
				}
			}
		} else if(this.getEngine().equals("MALLET_CL_NAIVE_BAYES_EM")){
			trainer = new NaiveBayesEMTrainer();
		} else if(this.getEngine().equals("MALLET_CL_NAIVE_BAYES")){
			trainer = new NaiveBayesTrainer();
		} else if(this.getEngine().equals("MALLET_CL_WINNOW")){
			double a = -1.0;
			double b = -1.0;
			double nfact = -1.0;
			if(params!=null && !params.isEmpty()){
				String[] p = params.split("\\s+");
				if(p.length==2){
					try {
						a = Double.parseDouble(p[0]);
						b = Double.parseDouble(p[1]);
					} catch(NumberFormatException e){
						logger.warn("LearningFramework: Failed to format parameter "
								+ "as double for Winnow trainer: " + e + " Proceeding "
										+ "with no parameters.");
						trainer = new WinnowTrainer();
					}
					trainer = new WinnowTrainer(a, b);
				} else if(p.length==3){
					try {
						a = Double.parseDouble(p[0]);
						b = Double.parseDouble(p[1]);
						nfact = Double.parseDouble(p[2]);
					} catch(NumberFormatException e){
						logger.warn("LearningFramework: Failed to format parameter "
								+ "as double for Winnow trainer: " + e + " Proceeding "
								+ "with no parameters.");
						trainer = new WinnowTrainer(a, b, nfact);
					}
					trainer = new WinnowTrainer();
				} else {
					logger.warn("LearningFramework: Incorrect number of parameters "
							+ "for Winnow trainer: " + p.length + ". Proceeding with "
									+ "no parameters.");
					trainer = new WinnowTrainer();
				}
			} else {
				trainer = new WinnowTrainer();
			}
		} else {
			logger.warn("LearningFramework: Unrecognised engine option " 
					+ this.getEngine() + ". Training failed.");
		}
		return trainer;
	}


	/**
	 * Train the classifier, given a training corpus.
	 */
	public void train(FeatureSpecification conf, CorpusWriter trainingCorpus){
		//Start by clearing out the previous saved model.
		File[] files = this.getOutputDirectory().listFiles();
		if(files!=null) {
			for(File f: files) {
				f.delete();
			}
		}

		CorpusWriterMallet trMal = (CorpusWriterMallet)trainingCorpus;
		//InstanceList instances = trMal.getInstancesFromFile(pipe);
		InstanceList instances = trMal.getInstances();

		if(instances==null){
			logger.warn("LearningFramework: No training instances!");
		} else {

			//Sanity check--what data do we have?
			logger.info("LearningFramework: Instances: " + instances.size());
			logger.info("LearningFramework: Data labels: " + instances.getDataAlphabet().size());
			logger.info("LearningFramework: Target labels: " + instances.getTargetAlphabet().size());

			ClassifierTrainer trainer = this.getTrainer();
			
			if(trainer!=null){
				this.classifier = trainer.train(instances);

				//Save the classifier so we don't have to retrain if we
				//restart GATE
				try {
					ObjectOutputStream oos = new ObjectOutputStream
							(new FileOutputStream(this.getOutputDirectory()
									+ "/" + classifiername));
					oos.writeObject(classifier);
					oos.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

				//We also save a copy of the configuration file, so the user can
				//change their copy without stuffing up their ability to apply this
				//model. We also save some information necessary to restore the model.
				this.storeConf(conf);
				this.writeInfo(
						instances.size(),
						instances.getDataAlphabet().size(),
						instances.getTargetAlphabet().size());
			}
		}
	}


	/**
	 * At application time, we expect to apply the model to the entire document.
	 * This is because different learners have different needs regarding the unit
	 * of input. Sequence classifiers, for example, need more than one instance at
	 * a time.
	 * @param instanceAnn
	 * @param inputASname
	 * @param doc
	 * @return
	 */
	public List<GateClassification> classify(String instanceAnn, 
			String inputASname, Document doc){
		List<GateClassification> gcs = new ArrayList<GateClassification>();

		AnnotationSet inputAS = doc.getAnnotations(inputASname);

		List<Annotation> instanceAnnotations = inputAS.get(instanceAnn).inDocumentOrder();

		Iterator<Annotation> it = instanceAnnotations.iterator();

		while(it.hasNext()){
			Annotation instanceAnnotation = it.next();

			Instance instance = CorpusWriterMallet.instanceFromInstanceAnnotation(
					this.getSavedConfFile(), instanceAnnotation, inputASname, doc,
					this.getMode(), "", "", "");
			
			//Instance needs to go through the pipe for this classifier, so that
			//it gets mapped using the same alphabet, and the text is in the
			//expected format.
			instance = this.classifier.getInstancePipe().instanceFrom(instance);

			Classification classification = classifier.classify(instance);
			String bestLabel = classification.getLabeling().getBestLabel().toString();
			Double confidence = classification.getLabeling().getBestValue();

			GateClassification gc = new GateClassification(
					instanceAnnotation, bestLabel, confidence);

			gcs.add(gc);
		}
		return gcs;
	}

	/*
	 * This makes a fresh trainer, trains and evaluates each fold and returns
	 * an average accuracy.
	 */
	public void evaluateXFold(CorpusWriter evalCorpus, int folds){
		CorpusWriterMallet cwm = (CorpusWriterMallet)evalCorpus;
		ClassifierTrainer trainer = this.getTrainer();
		double accuracyaccumulator = 0.0;
		
		CrossValidationIterator cvi = cwm.getInstances().crossValidationIterator(folds);
		
		while(cvi.hasNext()){
			InstanceList[] il = cvi.nextSplit();
			InstanceList training = il[0];
			InstanceList test = il[1];
			Classifier classifier = trainer.train(training);
			accuracyaccumulator += classifier.getAccuracy(test);
		}

		logger.info("LearningFramework: " + folds
				+ " fold cross-validation accuracy: " + accuracyaccumulator/folds);
	}
	
	/*
	 * Makes a fresh trainer, trains, evaluates.
	 */
	public void evaluateHoldout(CorpusWriter evalCorpus, float trainingproportion){
		CorpusWriterMallet cwm = (CorpusWriterMallet)evalCorpus;
		ClassifierTrainer localtrainer = this.getTrainer();
		
		InstanceList[] sets = cwm.getInstances().split(new Random(1),
				new double[]{trainingproportion, 1-trainingproportion});
				
		Classifier cl = localtrainer.train(sets[0]);
		double accuracy = cl.getAccuracy(sets[1]);
		
		logger.info("LearningFramework: Holdout accuracy training on " 
				+ trainingproportion + " of the data: " + accuracy);
	}
	
	public Algorithm whatIsIt(){
		return Algorithm.valueOf(this.getEngine());
	}
}
