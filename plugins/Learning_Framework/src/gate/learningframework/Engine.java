/*
 * Engine.java
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.creole.ResourceInstantiationException;

public abstract class Engine {

	static final Logger logger = Logger.getLogger("Engine");
	
	private String engine;
	
	public static String info = "info";

	private static String savedConf = "conf";
	
	private FeatureSpecification savedConfFile;
	
	private File outputDirectory;
	
	private Mode mode;
	
	public abstract void train(FeatureSpecification conf, CorpusWriter trainingCorpus);
	
	//public abstract List<GateClassification> classify(
	//		List<Annotation> instanceAnnotations, 
	//		AnnotationSet inputAS, Document doc);
	
	public abstract Algorithm whatIsIt();
	
	public abstract void evaluateXFold(CorpusWriter evalCorpus, int folds);

	public abstract void evaluateHoldout(CorpusWriter evalCorpus, float trainingproportion);
	
	public void writeInfo(int exnum, int dataalph, int targalph){
		try {
			FileWriter fw = new FileWriter(new File(outputDirectory, info));
			fw.write(this.whatIsIt() + "\n" + new Date() + "\n");
			fw.write(this.getMode().toString() + "\n");
			fw.write(exnum + " instances\n");
			fw.write(dataalph + " features\n");
			fw.write(targalph + " labels\n");
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Store a copy of the configuration file used at training time with the
	 * saved model. This means that if the user changes their configuration file,
	 * it won't stuff up their ability to use their saved model.
	 * @param conf
	 */
	public void storeConf(FeatureSpecification conf){
		File confFile = new File(this.getOutputDirectory(), Engine.getSavedConf());

		try {
			FileUtils.copyFile(new File(conf.getUrl().getFile()), confFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		URL confURL = null;
		try {
			confURL = confFile.toURI().toURL();
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		if(confURL!=null){
			try {
				this.setSavedConfFile(new FeatureSpecification(confURL));
			} catch (ResourceInstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * Use the info file to correctly restore the learner from the saved
	 * model directory.
	 * @param savedModelDirectoryFile
	 * @return
	 */
	public static Engine restoreLearner(File savedModelDirectoryFile){
		//Restore previously trained model
		File infofile = new File(savedModelDirectoryFile, Engine.info);
		Engine learner = null;

		if(infofile.exists()){

			List<String> infostrings = null;
			try {
				infostrings = Files.readAllLines(
						infofile.toPath(), Charset.forName("UTF-8"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(infostrings!=null && infostrings.size()>2){
				String engine = infostrings.get(0).trim();
				Algorithm algo = Algorithm.valueOf(engine);
				String modestr = infostrings.get(2).trim();
				Mode mode = Mode.valueOf(modestr);
				switch(algo){
				case LIBSVM:
					learner = new EngineLibSVM(savedModelDirectoryFile, mode, true);
					break;
				case MALLET_CL_C45:
				case MALLET_CL_DECISION_TREE:
				case MALLET_CL_MAX_ENT:
				case MALLET_CL_NAIVE_BAYES_EM:
				case MALLET_CL_NAIVE_BAYES:
				case MALLET_CL_WINNOW:
					learner = new EngineMallet(savedModelDirectoryFile, mode, engine, true);
					break;
				case MALLET_SEQ_CRF:
					learner = new EngineMalletSeq(savedModelDirectoryFile, mode, true);
					break;
				case WEKA_CL_NUM_ADDITIVE_REGRESSION:
				case WEKA_CL_NAIVE_BAYES:
				case WEKA_CL_J48:
				case WEKA_CL_RANDOM_TREE:
				case WEKA_CL_IBK:
					learner = new EngineWeka(savedModelDirectoryFile, mode, engine, true);
					break;
				}
			}
		} else {
			//No learner to restore
		}
		
		return learner;
	}

	public String getInfo() {
		return info;
	}

	public void setOutputDirectory(File outputDirectory){
		this.outputDirectory = outputDirectory;
	}
	
	public File getOutputDirectory(){
		if(!outputDirectory.exists()){
			outputDirectory.mkdirs();
		}
		return this.outputDirectory;
	}
	
	public String getEngine() {
		return engine;
	}

	public void setEngine(String engine) {
		this.engine = engine;
	}

	public static String getSavedConf() {
		return savedConf;
	}

	public FeatureSpecification getSavedConfFile() {
		return savedConfFile;
	}

	public void setSavedConfFile(FeatureSpecification savedConfFile) {
		this.savedConfFile = savedConfFile;
	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}
}
