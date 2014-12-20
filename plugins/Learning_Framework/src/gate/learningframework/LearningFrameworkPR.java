/*
 *  
 *
 */

package gate.learningframework;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import gate.AnnotationSet;
import gate.Annotation;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.ResourceInstantiationException;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.InvalidOffsetException;


/**
 * <p>Training, evaluation and application of Mallet ML in GATE.</p>
 */

@CreoleResource(name = "Learning Framework", comment = "Training, evaluation and application of ML in GATE.")
public class LearningFrameworkPR extends AbstractLanguageAnalyser implements
ProcessingResource,
Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	static final Logger logger = Logger.getLogger("LearningFrameworkPR");
	
	/**
	 * The configuration file.
	 * 
	 */
	private java.net.URL featureSpecURL;

	/**
	 * The directory to which data will be saved, including models and corpora.
	 * 
	 */
	private java.net.URL saveDirectory;

	/**
	 * The name of the annotation set to be used as input.
	 * 
	 */
	private String inputASName;

	/**
	 * The name of the output annotation set.
	 * 
	 */
	private String outputASName;

	/**
	 * The annotation type to be treated as instance. Leave blank to use the document as instance.
	 * 
	 */
	private String instanceName;

	/**
	 * The annotation type defining the unit for sequence tagging.
	 * 
	 */
	private String sequenceSpan;

	/**
	 * The operation to be done; data prep, training, evaluation or application.
	 * 
	 */
	private Operation operation;

	/**
	 * The number of folds for cross-validation.
	 * 
	 */
	private int foldsForXVal;

	/**
	 * The proportion of training data for holdout evaluation.
	 * 
	 */
	private float trainingproportion;

	/**
	 * The implementation to be used, such as Mallet.
	 * 
	 */
	private Algorithm trainingAlgo;

	/**
	 * Whether to do classification or named entity recognition.
	 * 
	 */
	private Mode mode;

	/**
	 * Annotation type containing/indicating the class.
	 * 
	 */
	private String classType;

	/**
	 * Annotation feature containing the class. Ignored for NER.
	 * 
	 */
	private String classFeature;

	/**
	 * The feature of the instance that can be used as an
	 * identifier for that instance.
	 * 
	 */
	private String identifierFeature;

	/**
	 * The confidence threshold for applying an annotation. In
	 * the case of NER, the confidence threshold is applied to
	 * the average for the entire entity.
	 * 
	 */
	private Double confidenceThreshold;

	/**
	 * Some of the learners take parameters. Parameters can be
	 * entered here. For example, the LibSVM supports parameters.
	 */
	private String learnerParams;



	@RunTime
	@CreoleParameter(comment = "The feature specification file.")
	public void setFeatureSpecURL(URL featureSpecURL) {
		if(!featureSpecURL.equals(this.featureSpecURL)){
			this.featureSpecURL = featureSpecURL;
			try {
				this.conf = new FeatureSpecification(featureSpecURL);
			} catch (ResourceInstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public URL getFeatureSpecURL() {
		return featureSpecURL;
	}


	@RunTime
	@CreoleParameter(comment = "The directory to which data will be saved, including models and corpora.")
	public void setSaveDirectory(URL output) {
		this.saveDirectory = output;

		savedModelDirectoryFile = new File(
				gate.util.Files.fileFromURL(saveDirectory), savedModelDirectory);

		evaluationModelDirectoryFile = new File(
				gate.util.Files.fileFromURL(saveDirectory), evaluationModelDirectory);

		this.applicationLearner = Engine.restoreLearner(savedModelDirectoryFile);
	}

	public URL getSaveDirectory() {
		return this.saveDirectory;
	}


	@RunTime
	@Optional
	@CreoleParameter
	public void setInputASName(String iasn) {
		this.inputASName = iasn;
	}

	public String getInputASName() {
		return this.inputASName;
	}

	@RunTime
	@Optional
	@CreoleParameter(defaultValue = "LearningFramework")
	public void setOutputASName(String oasn) {
		this.outputASName = oasn;
	}

	public String getOutputASName() {
		return this.outputASName;
	}


	@RunTime
	@Optional
	@CreoleParameter(defaultValue = "3", comment = "The number of folds for "
			+ "cross-validation.")
	public void setFoldsForXVal(Integer folds) {
		this.foldsForXVal = folds.intValue();
	}

	public Integer getFoldsForXVal() {
		return new Integer(this.foldsForXVal);
	}

	@RunTime
	@Optional
	@CreoleParameter(defaultValue = "0.5", comment = "The proportion of the "
			+ "data to use for training in holdout evaluation.")
	public void setTrainingProportion(Float trainingproportion) {
		this.trainingproportion = trainingproportion.floatValue();
	}

	public Float getTrainingProportion() {
		return new Float(this.trainingproportion);
	}


	@RunTime
	@CreoleParameter(defaultValue = "Token", comment = "The annotation type to "
			+ "be treated as instance.")
	public void setInstanceName(String inst) {
		this.instanceName = inst;
	}

	public String getInstanceName() {
		return this.instanceName;
	}

	@RunTime
	@Optional
	@CreoleParameter(comment = "For sequence learners, an annotation type "
			+ "defining a meaningful sequence span. Ignored by non-sequence "
			+ "learners. Needs to be in the input AS.")
	public void setSequenceSpan(String seq) {
		this.sequenceSpan = seq;
	}

	public String getSequenceSpan() {
		return this.sequenceSpan;
	}

	@RunTime
	@CreoleParameter(defaultValue = "TRAIN", comment = "The operation to be "
			+ "done; training, evaluation or application.")
	public void setOperation(Operation operation) {
		this.operation = operation;
	}

	public Operation getOperation() {
		return this.operation;
	}

	@RunTime
	@Optional
	@CreoleParameter(comment = "The algorithm to be used for training. Ignored at "
			+ "application time.")
	public void setTrainingAlgo(Algorithm algo) {
		this.trainingAlgo = algo;
	}

	public Algorithm getTrainingAlgo() {
		return this.trainingAlgo;
	}

	@RunTime
	@CreoleParameter(defaultValue = "CLASSIFICATION", comment = "Whether to do "
			+ "classification or named entity recognition.")
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public Mode getMode() {
		return this.mode;
	}

	@RunTime
	@CreoleParameter(comment = "Annotation type containing/indicating the class.")
	public void setClassType(String classType) {
		this.classType = classType;
	}

	public String getClassType() {
		return this.classType;
	}

	@RunTime
	@Optional
	@CreoleParameter(comment = "For classification, the feature "
			+ "containing the class. Ignored for NER, where type only is used.")
	public void setClassFeature(String classFeature) {
		this.classFeature = classFeature;
	}

	public String getClassFeature() {
		return this.classFeature;
	}

	@RunTime
	@Optional
	@CreoleParameter(comment = "The feature of the instance that "
			+ "can be used as an identifier for that instance.")
	public void setIdentifierFeature(String identifierFeature) {
		this.identifierFeature = identifierFeature;
	}

	public String getIdentifierFeature() {
		return this.identifierFeature;
	}

	@RunTime
	@CreoleParameter(defaultValue = "0.0", comment = "The minimum "
			+ "confidence/probability for including "
			+ "an annotation at application time. In the case of NER, the confidence "
			+ "threshold is applied to the average for the entire entity.")
	public void setConfidenceThreshold(Double confidenceThreshold) {
		this.confidenceThreshold = confidenceThreshold;
	}

	public Double getConfidenceThreshold() {
		return this.confidenceThreshold;
	}

	@RunTime
	@Optional
	@CreoleParameter(comment = "Some of the learners take parameters. Parameters "
			+ "can be entered here. For example, the LibSVM supports parameters.")
	public void setLearnerParams(String learnerParams) {
		this.learnerParams = learnerParams;
	}

	public String getLearnerParams() {
		return this.learnerParams;
	}




	//Loaded from save directory, replaced with training learner 
	//after training completes.
	private Engine applicationLearner; 

	//Used at training time.
	private Engine trainingLearner;

	//Separate learner for evaluation, not to be mixed up with the others
	private Engine evaluationLearner;

	//These corpora will be added to on each document so they need to be globals
	private CorpusWriter trainingCorpus = null;
	private CorpusWriter testCorpus = null;
	private CorpusWriter exportCorpus = null;

	private FeatureSpecification conf = null;

	//Some file names, mostly not used at the mo since the corpora don't need
	//to be written out. The arff one gets used.
	private static String trainfilenamemallet = "train.mallet";
	private static String testfilenamemallet = "test.mallet";
	private static String trainfilenamemalletseq = "train.seq.mallet";
	private static String testfilenamemalletseq = "test.seq.mallet";
	private static String trainfilenamearff = "train.arff";
	private static String testfilenamearff = "test.arff";
	private static String outputfilenamearff = "output.arff";

	//Some directory names. The evaluation one doesn't get used at the mo.
	private static String savedModelDirectory = "savedModel";
	private static String evaluationModelDirectory = "evaluationModel";

	private File savedModelDirectoryFile;
	private File evaluationModelDirectoryFile;

	private static String outputClassFeature = "LF_class";
	private static String outputProbFeature = "LF_confidence";
	private static String outputSequenceSpanIDFeature = "LF_seq_span_id";
	
	//In the case of NER, output instance annotations to temporary
	//AS, to keep them separate.
	private static String tempOutputASName = "tmp_ouputas_for_ner12345";

	@Override
	public Resource init() throws ResourceInstantiationException {
		//Load the configuration file for training from the location given.
		//If the user changes the file location at runtime, no prob, but if
		//they change the file contents, they need to reinitialize to load it.
		if(featureSpecURL!=null){
			this.conf = new FeatureSpecification(featureSpecURL);
		}

		return this;
	}

	private Engine createLearner(Algorithm algo, File savedModelFile){
		if(algo!=null){
			String spec = algo.toString();
			switch(algo){
			case MALLET_CL_C45:
			case MALLET_CL_DECISION_TREE:
			case MALLET_CL_MAX_ENT:
			case MALLET_CL_NAIVE_BAYES_EM:
			case MALLET_CL_NAIVE_BAYES:
			case MALLET_CL_WINNOW:
				return new EngineMallet(savedModelFile, mode, learnerParams, spec, false);
			case MALLET_SEQ_CRF:
				return new EngineMalletSeq(savedModelFile, mode, false);
			case LIBSVM:
				return new EngineLibSVM(
						savedModelFile, mode, learnerParams, false);
			case WEKA_CL_NUM_ADDITIVE_REGRESSION:
			case WEKA_CL_NAIVE_BAYES:
			case WEKA_CL_J48:
			case WEKA_CL_RANDOM_TREE:
			case WEKA_CL_IBK:
				return new EngineWeka(
						savedModelFile, mode, learnerParams, spec, false);
			}
		}
		return null;
	}

	@Override
	public void execute() throws ExecutionException {	 
		Document doc = getDocument();

		switch(this.getOperation()){
		case TRAIN:	
			//Do this once only on the first document
			if(corpus.indexOf(document)==0) {
				if(trainingAlgo==null){
					logger.warn("LearningFramework: Please select an algorithm!");
					trainingLearner=null;
					break;
				} else {
					trainingLearner = this.createLearner(this.trainingAlgo, this.savedModelDirectoryFile);

					switch(this.getTrainingAlgo()){
					case LIBSVM: //Yes we are making a mallet corpus writer for use with libsvm ..
					case MALLET_CL_C45:
					case MALLET_CL_DECISION_TREE:
					case MALLET_CL_MAX_ENT:
					case MALLET_CL_NAIVE_BAYES_EM:
					case MALLET_CL_NAIVE_BAYES:
					case MALLET_CL_WINNOW:
						File trainfilemallet = new File(
								gate.util.Files.fileFromURL(saveDirectory), trainfilenamemallet);
						trainingCorpus = new CorpusWriterMallet(this.conf, this.instanceName, 
								this.inputASName, trainfilemallet, mode, classType, 
								classFeature, identifierFeature);
						break;
					case MALLET_SEQ_CRF:
						File trainfilemalletseq = new File(
								gate.util.Files.fileFromURL(saveDirectory), trainfilenamemalletseq);
						trainingCorpus = new CorpusWriterMalletSeq(this.conf, this.instanceName, 
								this.inputASName, trainfilemalletseq, this.sequenceSpan, 
								mode, classType, classFeature, identifierFeature);
						break;
					case WEKA_CL_NUM_ADDITIVE_REGRESSION:
						File trainfileweka = new File(
								gate.util.Files.fileFromURL(saveDirectory), trainfilenamearff);
						trainingCorpus = new CorpusWriterArffNumericClass(this.conf, this.instanceName, 
								this.inputASName, trainfileweka, 
								mode, classType, classFeature, identifierFeature);
						break;
					case WEKA_CL_NAIVE_BAYES:
					case WEKA_CL_J48:
					case WEKA_CL_RANDOM_TREE:
					case WEKA_CL_IBK:
						trainfileweka = new File(
								gate.util.Files.fileFromURL(saveDirectory), trainfilenamearff);
						trainingCorpus = new CorpusWriterArff(this.conf, this.instanceName, 
								this.inputASName, trainfileweka, 
								mode, classType, classFeature, identifierFeature);
						break;
					}

					logger.info("LearningFramework: Preparing training data ...");
				}
			}

			if(trainingLearner!=null){
				this.trainingCorpus.add(doc);
			}

			//Do this once only, on the last document.
			if(corpus.indexOf(document)==(corpus.size()-1) && trainingLearner!=null) {	
				//Ready to go
				logger.info("LearningFramework: Training " 
						+ trainingLearner.whatIsIt().toString() + " ...");

				trainingLearner.train(conf, trainingCorpus);
				this.applicationLearner = trainingLearner;
				logger.info("LearningFramework: Training complete!");				
			}
			break;
		case APPLY_CURRENT_MODEL:
			//Do this once only on the first document
			if(corpus.indexOf(document)==0) {
				//this.applicationLearner = Engine.restoreLearner(savedModelDirectoryFile);
				if(this.applicationLearner==null){
					logger.warn("LearningFramework: Begin by training a model!");
					break;
				} else {
					logger.info("LearningFramework: Applying " 
							+ this.applicationLearner.whatIsIt().toString());
					if(applicationLearner.getMode()!=this.getMode()){
						logger.warn("LearningFramework: Warning! Applying "
								+ "model trained in " + applicationLearner.getMode() 
								+ " mode in " + this.getMode() + " mode!");
					}
				}
			}

			if(applicationLearner!=null){
				List<GateClassification> gcs = null;

				switch(applicationLearner.whatIsIt()){
				case LIBSVM:
					gcs = ((EngineLibSVM)applicationLearner).classify(
							this.instanceName, this.inputASName, doc);
					break;
				case MALLET_CL_C45:
				case MALLET_CL_DECISION_TREE:
				case MALLET_CL_MAX_ENT:
				case MALLET_CL_NAIVE_BAYES_EM:
				case MALLET_CL_NAIVE_BAYES:
				case MALLET_CL_WINNOW:
					gcs = ((EngineMallet)applicationLearner).classify(
							this.instanceName, this.inputASName, doc);
					break;
				case MALLET_SEQ_CRF:
					gcs = ((EngineMalletSeq)applicationLearner).classify(
							this.instanceName, this.inputASName, doc, this.sequenceSpan);
					break;
				case WEKA_CL_NUM_ADDITIVE_REGRESSION:
				case WEKA_CL_NAIVE_BAYES:
				case WEKA_CL_J48:
				case WEKA_CL_RANDOM_TREE:
				case WEKA_CL_IBK:
					gcs = ((EngineWeka)applicationLearner).classify(
							this.instanceName, this.inputASName, doc);
					break;
				}

				addClassificationAnnotations(doc, gcs);
				if(this.getMode()==Mode.NAMED_ENTITY_RECOGNITION){
					//We need to make the surrounding annotations
					addSurroundingAnnotations(doc);
				}
			}
			break;
		case EVALUATE_X_FOLD:
			//Do this once only on the first document
			if(corpus.indexOf(document)==0) {
				if(trainingAlgo==null){
					logger.warn("LearningFramework: Please select an algorithm!");
					evaluationLearner=null;
					break;
				} else {
					evaluationLearner = this.createLearner(this.trainingAlgo, this.evaluationModelDirectoryFile);

					switch(this.getTrainingAlgo()){
					case LIBSVM: //Yes we are making a mallet corpus writer for use with libsvm ..
					case MALLET_CL_C45:
					case MALLET_CL_DECISION_TREE:
					case MALLET_CL_MAX_ENT:
					case MALLET_CL_NAIVE_BAYES_EM:
					case MALLET_CL_NAIVE_BAYES:
					case MALLET_CL_WINNOW:
						File testfilemallet = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamemallet);
						testCorpus = new CorpusWriterMallet(this.conf, this.instanceName, 
								this.inputASName, testfilemallet, mode, classType, 
								classFeature, identifierFeature);
						break;
					case MALLET_SEQ_CRF:
						File testfilemalletseq = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamemalletseq);
						testCorpus = new CorpusWriterMalletSeq(this.conf, this.instanceName, 
								this.inputASName, testfilemalletseq, this.sequenceSpan, 
								mode, classType, classFeature, identifierFeature);
						break;
					case WEKA_CL_NUM_ADDITIVE_REGRESSION:
						File testfileweka = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamearff);
						testCorpus = new CorpusWriterArffNumericClass(this.conf, this.instanceName, 
								this.inputASName, testfileweka, mode, classType, classFeature, 
								identifierFeature);
						break;
					case WEKA_CL_NAIVE_BAYES:
					case WEKA_CL_J48:
					case WEKA_CL_RANDOM_TREE:
					case WEKA_CL_IBK:
						testfileweka = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamearff);
						testCorpus = new CorpusWriterArff(this.conf, this.instanceName, 
								this.inputASName, testfileweka, mode, classType, classFeature, 
								identifierFeature);
						break;
					}
				}
			}

			if(evaluationLearner!=null){
				this.testCorpus.add(doc);
			}

			//Do this once only, on the last document.
			if(corpus.indexOf(document)==(corpus.size()-1) && evaluationLearner!=null) {	
				//Ready to evaluate
				logger.info("LearningFramework: Evaluating ..");
				evaluationLearner.evaluateXFold(testCorpus, this.foldsForXVal);
			}
			break;
		case EVALUATE_HOLDOUT:
			//Do this once only on the first document
			if(corpus.indexOf(document)==0) {
				if(trainingAlgo==null){
					logger.warn("LearningFramework: Please select an algorithm!");
					evaluationLearner=null;
					break;
				} else {
					evaluationLearner = this.createLearner(this.trainingAlgo, this.evaluationModelDirectoryFile);

					switch(this.getTrainingAlgo()){
					case LIBSVM: //Yes we are making a mallet corpus writer for use with libsvm ..
					case MALLET_CL_C45:
					case MALLET_CL_DECISION_TREE:
					case MALLET_CL_MAX_ENT:
					case MALLET_CL_NAIVE_BAYES_EM:
					case MALLET_CL_NAIVE_BAYES:
					case MALLET_CL_WINNOW:
						File testfilemallet = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamemallet);
						testCorpus = new CorpusWriterMallet(this.conf, this.instanceName, 
								this.inputASName, testfilemallet, mode, classType, 
								classFeature, identifierFeature);
						break;
					case MALLET_SEQ_CRF:
						File testfilemalletseq = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamemalletseq);
						testCorpus = new CorpusWriterMalletSeq(this.conf, this.instanceName, 
								this.inputASName, testfilemalletseq, this.sequenceSpan, 
								mode, classType, classFeature, identifierFeature);
						break;
					case WEKA_CL_NUM_ADDITIVE_REGRESSION:
						File testfileweka = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamearff);
						testCorpus = new CorpusWriterArffNumericClass(this.conf, this.instanceName, 
								this.inputASName, testfileweka, mode, classType, classFeature, 
								identifierFeature);
						break;
					case WEKA_CL_NAIVE_BAYES:
					case WEKA_CL_J48:
					case WEKA_CL_RANDOM_TREE:
					case WEKA_CL_IBK:
						testfileweka = new File(
								gate.util.Files.fileFromURL(saveDirectory), testfilenamearff);
						testCorpus = new CorpusWriterArff(this.conf, this.instanceName, 
								this.inputASName, testfileweka, mode, classType, classFeature, 
								identifierFeature);
						break;
					}
				}
			}

			if(evaluationLearner!=null){
				this.testCorpus.add(doc);
			}

			//Do this once only, on the last document.
			if(corpus.indexOf(document)==(corpus.size()-1) && evaluationLearner!=null) {	
				//Ready to evaluate
				logger.info("LearningFramework: Evaluating ..");
				evaluationLearner.evaluateHoldout(
						testCorpus, this.trainingproportion);
			}
			break;
		case EXPORT_ARFF:
			//Do this once only on the first document
			if(corpus.indexOf(document)==0) {
				File outputfilearff = new File(
						gate.util.Files.fileFromURL(saveDirectory), outputfilenamearff);
				exportCorpus = new CorpusWriterArff(this.conf, this.instanceName, this.inputASName, 
						outputfilearff, mode, classType, classFeature, identifierFeature);
				exportCorpus.initializeOutputStream();
			}

			//Every document
			exportCorpus.add(document);

			//Do this once only, on the last document.
			if(corpus.indexOf(document)==(corpus.size()-1)) {
				exportCorpus.conclude();
			}
			break;
		case EXPORT_ARFF_NUMERIC_CLASS:
			//Do this once only on the first document
			if(corpus.indexOf(document)==0) {
				File outputfilearff = new File(
						gate.util.Files.fileFromURL(saveDirectory), outputfilenamearff);
				exportCorpus = new CorpusWriterArffNumericClass(this.conf, this.instanceName, this.inputASName, 
						outputfilearff, mode, classType, classFeature, identifierFeature);
				exportCorpus.initializeOutputStream();
			}

			//Every document
			exportCorpus.add(document);

			//Do this once only, on the last document.
			if(corpus.indexOf(document)==(corpus.size()-1)) {
				exportCorpus.conclude();
			}
			break;
		} 
	}

	/*
	 * Having received a list of GateClassifications from the learner, we
	 * then write them onto the document if they pass the confidence threshold.
	 * If we are doing NER, we don't apply the confidence threshold.
	 */
	private void addClassificationAnnotations(Document doc, List<GateClassification> gcs){

		Iterator<GateClassification> gcit = gcs.iterator();

		AnnotationSet outputAnnSet = doc.getAnnotations(this.outputASName);
		//Unless we are doing NER, in which case we want to use the temp
		if(this.getMode()==Mode.NAMED_ENTITY_RECOGNITION){
			outputAnnSet = doc.getAnnotations(tempOutputASName);
		}

		while(gcit.hasNext()){
			GateClassification gc = gcit.next();

			if(this.getMode()==Mode.CLASSIFICATION
					&& gc.getConfidenceScore()<this.getConfidenceThreshold()){
				//Skip it
			} else {
				//We have a valid classification. Now write it onto the document.
				FeatureMap fm = Factory.newFeatureMap();
				fm.putAll(gc.getInstance().getFeatures());
				fm.put(outputClassFeature, gc.getClassAssigned());
				fm.put(outputProbFeature, gc.getConfidenceScore());
				//fm.put(this.conf.getIdentifier(), identifier);
				if(gc.getSeqSpanID()!=null){
					fm.put(outputSequenceSpanIDFeature, gc.getSeqSpanID());
				}
				outputAnnSet.add(gc.getInstance().getStartNode(), 
						gc.getInstance().getEndNode(),
						gc.getInstance().getType(), fm);
			}

		}
	}

	/*
	 * In the case of NER, we replace the instance annotations with
	 * spanning annotations for the desired entity type. We apply a confidence
	 * threshold to the average for the whole entity. We write the average
	 * confidence for the entity onto the entity.
	 */
	private void addSurroundingAnnotations(Document doc){
		AnnotationSet fromset = doc.getAnnotations(tempOutputASName);
		AnnotationSet toset = doc.getAnnotations(this.outputASName);
		List<Annotation> insts = fromset.get(this.instanceName).inDocumentOrder();
		
		class AnnToAdd{
			long thisStart = -1;
			long thisEnd = -1;
			int len = 0;
			double conf = 0.0;
		}

		Map<Integer, AnnToAdd> annsToAdd = new HashMap<Integer, AnnToAdd>();

		Iterator<Annotation> it = insts.iterator();
		while(it.hasNext()){
			Annotation inst = it.next();

			//Do we have an annotation in progress for this sequence span ID?
			//If we didn't use sequence learning, just use the same ID repeatedly.
			Integer sequenceSpanID = (Integer)inst.getFeatures().get(outputSequenceSpanIDFeature);
			if(sequenceSpanID==null) sequenceSpanID=0;
			AnnToAdd thisAnnToAdd = annsToAdd.get(sequenceSpanID);

			//B, I or O??
			String status = (String)inst.getFeatures().get(outputClassFeature);
			if(status==null){
				status = "outside";
			}

			if(thisAnnToAdd!=null && (status.equals("beginning") || status.equals("outside"))){
				//If we've found a beginning or an end, this indicates that a current
				//incomplete annotation is now completed. We should write it on and
				//remove it from the map.
				double entityconf = thisAnnToAdd.conf/thisAnnToAdd.len;

				if(thisAnnToAdd.thisStart!=-1 && thisAnnToAdd.thisEnd!=-1
						&& entityconf>=this.getConfidenceThreshold()){
					FeatureMap fm = Factory.newFeatureMap();
					fm.put(outputProbFeature, entityconf);
					if(sequenceSpanID!=null){
						fm.put(outputSequenceSpanIDFeature, sequenceSpanID);
					}
					try {
						toset.add(
								thisAnnToAdd.thisStart, thisAnnToAdd.thisEnd, 
								this.getClassType(), fm);
					} catch (InvalidOffsetException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				annsToAdd.remove(sequenceSpanID);
			}


			if(status.equals("beginning")){
				AnnToAdd ata = new AnnToAdd();
				ata.thisStart = inst.getStartNode().getOffset();
				//Update the end on the offchance that this is it
				ata.thisEnd = inst.getEndNode().getOffset();
				ata.conf = (Double)inst.getFeatures().get(outputProbFeature);
				ata.len++;
				annsToAdd.put(sequenceSpanID, ata);
			}

			if(status.equals("inside") && thisAnnToAdd!=null){
				thisAnnToAdd.conf += (Double)inst.getFeatures().get(outputProbFeature);
				thisAnnToAdd.len++;
				//Update the end on the offchance that this is it
				thisAnnToAdd.thisEnd = inst.getEndNode().getOffset();
			}

			//Remove each inst ann as we consume it
			fromset.remove(inst);
		}
		
		//Add any hanging entities at the end.
		Iterator<Integer> atait = annsToAdd.keySet().iterator();
		while(atait.hasNext()){
			Integer sequenceSpanID = (Integer)atait.next();
			AnnToAdd thisAnnToAdd = annsToAdd.get(sequenceSpanID);
			double entityconf = thisAnnToAdd.conf/thisAnnToAdd.len;

			if(thisAnnToAdd.thisStart!=-1 && thisAnnToAdd.thisEnd!=-1
					&& entityconf>=this.getConfidenceThreshold()){
				FeatureMap fm = Factory.newFeatureMap();
				fm.put(outputProbFeature, entityconf);
				if(sequenceSpanID!=null){
					fm.put(outputSequenceSpanIDFeature, sequenceSpanID);
				}
				try {
					toset.add(
							thisAnnToAdd.thisStart, thisAnnToAdd.thisEnd, 
							this.getClassType(), fm);
				} catch (InvalidOffsetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		doc.removeAnnotationSet(tempOutputASName);
	}

	@Override
	public synchronized void interrupt() {
		super.interrupt();
	}

}
