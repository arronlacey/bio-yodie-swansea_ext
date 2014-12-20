package gate.learningframework;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Utils;
import gate.learningframework.FeatureSpecification.Attribute;
import gate.learningframework.FeatureSpecification.AttributeList;
import gate.learningframework.FeatureSpecification.Datatype;
import gate.learningframework.FeatureSpecification.Ngram;

import java.util.List;

import org.apache.log4j.Logger;

public class FeatureExtractor {

	static final Logger logger = Logger.getLogger("FeatureExtractor");

	/**
	 * Get the class for this instance.
	 */
	public static String extractClassForClassification(String type, 
			String feature, String inputASname, Annotation instanceAnnotation, 
			Document doc){
		String textToReturn = "null";

		if(type!=null && !type.equals("") 
				&& !instanceAnnotation.getType().equals(type)){
			List<Annotation> annotations = Utils.getContainedAnnotations(
					doc.getAnnotations(inputASname), instanceAnnotation, type).inDocumentOrder();

			if(annotations.size()>0){
				Annotation annotationToPrint = annotations.get(0);
				if(feature!=null) {
					String val = (String)annotationToPrint.getFeatures().get(feature);
					if(val!=null && !val.isEmpty() && !val.equals("")){
						textToReturn = val;
					}
				}
			}
		} else {
			if(feature!=null) {
				String val = (String)instanceAnnotation.getFeatures().get(feature);
				if(val!=null && !val.isEmpty() && !val.equals("")){
					textToReturn = val;
				}
			}
		}
		return textToReturn.replaceAll("[\\n\\s]+", "-");
	}

	/**
	 * Get the class for this instance.
	 */
	public static double extractNumericClassForClassification(String type, 
			String feature, String inputASname, Annotation instanceAnnotation, 
			Document doc){

		if(type!=null && !type.equals("") 
				&& !instanceAnnotation.getType().equals(type)){ //Not on instance
			List<Annotation> annotations = Utils.getContainedAnnotations(
					doc.getAnnotations(inputASname), instanceAnnotation, type).inDocumentOrder();

			if(annotations.size()>0){
				Annotation annotationToPrint = annotations.get(0);
				if(feature!=null) {
					Object toReturn = (Object)annotationToPrint.getFeatures().get(feature);
					if(toReturn instanceof String){
						return Double.parseDouble((String)toReturn);
					} else {
						return ((Number)toReturn).doubleValue();
					}
				}
			}
		} else { //On instance
			if(feature!=null) {
				Object toReturn = (Object)instanceAnnotation.getFeatures().get(feature);
				if(toReturn instanceof String){
					return Double.parseDouble((String)toReturn);
				} else {
					return ((Number)toReturn).doubleValue();
				}
			}
		}
		logger.warn("LearningFramework: Failed to retrieve class.");
		return 0.0;
	}

	/**
	 * In the case of named entity recognition, we construct the class based
	 * on the instance's position relative to the class annotation type. If it
	 * occurs at the beginning of the class annotation, it's a "beginning". In
	 * the middle or at the end, it's an "inside". Instances that don't occur
	 * in the span of a class annotation are an "outside".
	 */
	public static String extractClassNER(String type, String inputASname, 
			Annotation instanceAnnotation, 
			Document doc){

		if(type!=null && !type.equals("")){
			String textToReturn = "";
			List<Annotation> annotations = Utils.getOverlappingAnnotations(
					doc.getAnnotations(inputASname), instanceAnnotation, type).inDocumentOrder();

			if(annotations.size()>0){
				//Pick a mention to focus on--there should be only one by rights.
				Annotation mention = annotations.get(0);

				if(mention.getStartNode().getOffset()==
						instanceAnnotation.getStartNode().getOffset()){
					textToReturn = "beginning";
				} else {
					textToReturn = "inside";
				}
			} else { //No overlapping mentions so it's an outside
				textToReturn = "outside";
			}
			return textToReturn;
		} else {
			return null;
		}
	}

	/**
	 * Identifier gets used by mallet and maybe some others to define the
	 * instance. It might be useful in the case that we write out the corpus
	 * for identifying what is going on in there. Mostly it just does't matter
	 * though. Here, we extract it from the document for our instance.
	 */
	public static String extractIdentifier(String type, 
			String feature, String inputASname, Annotation instanceAnnotation, 
			Document doc){
		String textToReturn = "null";

		if(type!=null && !type.equals("") 
				&& !instanceAnnotation.getType().equals(type)){
			List<Annotation> annotations = Utils.getOverlappingAnnotations(
					doc.getAnnotations(inputASname), instanceAnnotation, type).inDocumentOrder();

			if(annotations.size()>0){
				Annotation annotationToPrint = annotations.get(0);
				if(feature != null) {
					if(annotationToPrint.getFeatures().get(feature)!=null){
						textToReturn = annotationToPrint.getFeatures().get(feature).toString();
					} else {
						textToReturn = "null";
					}
				} else {
					//No feature specified so we'll just use the text of the annotation.
					String annotationText = "";
					try {
						annotationText = gate.Utils.cleanStringFor(doc, annotationToPrint);
					} catch(Exception e){
						e.printStackTrace();
					}
					if(annotationText.length()>0){
						textToReturn = annotationText;
					} else {
						textToReturn = "null";
					}
				}
			}
		} else {
			if(feature != null) {
				if(instanceAnnotation.getFeatures().get(feature)!=null){
					textToReturn = instanceAnnotation.getFeatures().get(feature).toString();
				} else {
					textToReturn = "null";
				}
			} else {
				//No feature specified so we'll just use the text of the annotation.
				String annotationText = "";
				try {
					annotationText = gate.Utils.cleanStringFor(doc, instanceAnnotation);
				} catch(Exception e){
					e.printStackTrace();
				}
				if(annotationText.length()>0){
					textToReturn = annotationText;
				} else {
					textToReturn = "null";
				}
			}
		}
		return textToReturn.replaceAll("[\\n\\s]+", "-");
	}

	/**
	 * Given an annotation and optional feature, find the feature
	 * or just the text of the annotation in the instance and 
	 * return it.
	 * 
	 * If no feature is specified, just return the text of the
	 * annotation.
	 * 
	 * If no annotation is specified, use the instance.
	 * 
	 * If it's a numeric feature, return in the format name=value,
	 * which gets used further down the line;
	 * 
	 * If more than one of the annotation occurs within the span
	 * of the instance, the first one will be returned.
	 */
	public static String extractSingleFeature(
			Attribute att, String inputASname, Annotation instanceAnnotation, 
			Document doc){
		if(att==null){
			return "null";
		}

		Datatype datatype = att.datatype;

		String type = att.type;
		String feature = att.feature;
		
		String feat = extractFeatureString(type, feature, datatype, inputASname, 
				instanceAnnotation, doc);

		if(type==null){type=instanceAnnotation.getType();};
		if(feature==null){feature="cleanstring";};
		
		if(datatype==Datatype.nominal){
			return type + ":" + feature + ":" + feat;
		} else {
			try {
				double df = Double.parseDouble(feat);
				return type + ":" + feature + "=" + df;
			} catch (NumberFormatException e){
				logger.warn("LearningFramework: Failed to format numeric feature "
						+ feat + " as double. Treating as string.");
				return type + ":" + feature + ":" + feat;
			}
		}
	}


	public static String extractFeatureString(
			String type, String feature, Datatype datatype, 
			String inputASname, Annotation instanceAnnotation, 
			Document doc){
		String feat = "null";

		/*Although the user needn't specify the annotation type if it's the
		 * same as the instance, they may do so. It's intuitive that if they
		 * do so, they mean to extract the feature from the instance, not just
		 * the first colocated same type annotation. This matters in
		 * disambiguation, where we have many colocated same type annotations.
		 * Fix it up front by wiping out type if it's the same as the instance.
		 */
		if(instanceAnnotation.getType().equals(type)){
			type = null;
		}

		if(type==null){
			if(feature != null) {
				if(instanceAnnotation.getFeatures().get(feature)!=null){
					feat = instanceAnnotation.getFeatures().get(feature).toString();
				}
			} else {
				//No feature specified so we'll just use the text of the annotation.
				String annotationText = "";
				try {
					annotationText = gate.Utils.cleanStringFor(doc, instanceAnnotation);
				} catch(Exception e){
					e.printStackTrace();
				}
				if(annotationText.length()>0){
					feat = annotationText;
				}
			}
		} else { //Type does not equal null--feature is not on the instance
			List<Annotation> annotations = Utils.getOverlappingAnnotations(
					doc.getAnnotations(inputASname), instanceAnnotation, type).inDocumentOrder();

			if(annotations.size()>0){
				Annotation annotationToPrint = annotations.get(0);
				if(feature != null) {
					if(annotationToPrint.getFeatures().get(feature)!=null){
						feat = annotationToPrint.getFeatures().get(feature).toString();
					}
				} else {
					//No feature specified so we'll just use the text of the annotation.
					String annotationText = "";
					try {
						annotationText = gate.Utils.cleanStringFor(doc, annotationToPrint);
					} catch(Exception e){
						e.printStackTrace();
					}
					if(annotationText.length()>0){
						feat = annotationText;
					}
				}
			}
		}

		//Replace "=" because it will cause the feature to get parsed as a
		//numeric when the instance gets piped.
		feat = feat.replaceAll("=", "[equals]");

		//Replace spaces because they'll cause splits in the wrong place
		//when the instance gets piped.
		feat = feat.replaceAll("[\\n\\s]+", "-");

		return feat;
	}

	/*
	 * 
	 */
	public static String extractNgramFeature(Ngram ng, String inputASname, 
			Annotation instanceAnnotation, Document doc, String separator){
		String textToReturn = "";

		int number = ng.number;
		String type = ng.type;
		String feature = ng.feature;

		AnnotationSet inputAS = doc.getAnnotations(inputASname);

		if(number>0 && type!=null && feature!=null){
			String[] gram = new String[number];

			List<Annotation> al = Utils.getContainedAnnotations(
					inputAS, instanceAnnotation, type).inDocumentOrder();
			if(al.size()<number){
				return "";
			} else {
				//Set up the array with the first few items
				for(int i=1;i<number;i++){
					gram[i] = extractFeatureString(type, feature, Datatype.nominal, 
							inputASname, al.get(i-1), doc);
				}

				for(int i=number-1;i<al.size();i++){					
					//Slide the window along
					for(int j=1;j<number;j++){
						gram[j-1] = gram[j];
					}

					//Add the new item
					gram[number-1] = extractFeatureString(type, feature, Datatype.nominal, 
							inputASname, al.get(i), doc);

					//Write out the ngram
					String thisngram = gram[0]; //There will always be at least one in it.
					for(int j=1;j<number;j++){
						thisngram = thisngram + "-" + gram[j];
					}
					textToReturn = textToReturn + separator 
							+ type + ":" + feature + ":ng:" 
							+ thisngram;
				}
			}
		}
		if(textToReturn.length()>1){ //Trim off the leading separator
			textToReturn = textToReturn.substring(1);
		}
		return textToReturn;
	}

	public static String extractRangeFeature(AttributeList al, 
			String inputASname, Annotation instanceAnnotation, Document doc, 
			String separator){
		String textToReturn = "";

		if(al==null){
			return "null";
		}

		Datatype datatype = al.datatype;
		String type = al.type;
		String feature = al.feature;
		int from = al.from;
		int to = al.to;

		AnnotationSet as = doc.getAnnotations(inputASname);
		long centre = instanceAnnotation.getStartNode().getOffset();

		List<Annotation> annlistforward = 
				as.get(type, centre, doc.getContent().size()).inDocumentOrder();

		List<Annotation> annlistbackward = 
				as.get(type, 0L, centre).inDocumentOrder();

		for(int i=from;i<to;i++){
			Annotation ann;
			if(i<0){
				if(-i<=annlistbackward.size()){
					ann = annlistbackward.get(annlistbackward.size()+i);
					String feat = extractFeatureString(type, feature, datatype, inputASname, ann, doc);
					
					if(datatype==Datatype.nominal){
						textToReturn = textToReturn + separator + type + ":" 
							+ feature + ":r" + i + ":"
							+ feat;
					} else {
						try {
							double df = Double.parseDouble(feat);
							textToReturn = textToReturn + separator + type + ":" 
									+ feature + ":r" + i + "="
									+ df;
						} catch (NumberFormatException e){
							logger.warn("LearningFramework: Failed to format numeric feature "
									+ feat + " as double. Treating as string.");
							textToReturn = textToReturn + separator + type + ":" 
									+ feature + ":r" + i + ":"
									+ feat;
						}
					}
					
					
				}
			} else {
				if(i<annlistforward.size()){
					ann = annlistforward.get(i);
					textToReturn = textToReturn + separator + type 
							+ ":" + feature + ":r" + i + ":"
							+ extractFeatureString(type, feature, datatype, inputASname, ann, doc);
				}
			}
		}
		if(textToReturn.length()>1){ //Trim off the leading separator
			textToReturn = textToReturn.substring(1);
		}
		return textToReturn;
	}
}
