package gate.creole.gazetteer.lucene;

import gate.Annotation;
import gate.AnnotationSet;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is the implementation of the resource Lucene based gazetteer.
 */
@CreoleResource(name = "Assign Super Class PR", comment = "assigns class feature")
public class AssignSuperClass extends AbstractLanguageAnalyser implements
		ProcessingResource {

	/**
	 * for serialisation
	 */
	private static final long serialVersionUID = 7434037183514920676L;

	/**
	 * List of the annotation types to be used for lookup.
	 */
	private List<String> annotationTypes;

	/**
	 * Name of the input annotation set name
	 */
	private String inputASName;

	private URL superClassFileURL;

	private Map<String, String> map;

	/** Initialise this resource, and return it. */
	public Resource init() throws ResourceInstantiationException {
		if (superClassFileURL == null) {
			throw new ResourceInstantiationException(
					"superClassFileURL parameter cannot be null");
		}

		map = new HashMap<String, String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(
					superClassFileURL.getFile()));
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] data = line.split("\t");
				if (data.length != 2)
					continue;
				map.put(data[0], data[1]);
			}
			br.close();
		} catch (Exception e) {
			throw new ResourceInstantiationException(e);
		}
		return this;
	} // init()

	public void execute() throws ExecutionException {

		// document sanity check
		if (document == null) {
			throw new ExecutionException("Document cannot be null");
		}

		// there must be at least one annotation type available
		if (annotationTypes == null || annotationTypes.isEmpty()) {
			throw new ExecutionException(
					"You must provide at least one annotation type");
		}

		// input annotation set
		AnnotationSet inputAS = document.getAnnotations(inputASName);

		// one annotation type at a time
		for (String at : annotationTypes) {
			AnnotationSet setOfSpecificType = inputAS.get(at);

			// one annotation at a time
			for (Annotation a : setOfSpecificType) {
				String cls = (String) a.getFeatures().get("cls");
				String superClass = map.get(cls);
				if (superClass == null)
					continue;
				a.getFeatures().put("superClass", superClass);
			}
		}
	}

	public List<String> getAnnotationTypes() {
		return annotationTypes;
	}

	@RunTime
	@CreoleParameter(defaultValue = "Sem_Person;Sem_Location;Sem_Organisation")
	public void setAnnotationTypes(List<String> annotationTypes) {
		this.annotationTypes = annotationTypes;
	}

	public String getInputASName() {
		return inputASName;
	}

	@RunTime
	@Optional
	@CreoleParameter
	public void setInputASName(String inputASName) {
		this.inputASName = inputASName;
	}

	public URL getSuperClassFileURL() {
		return superClassFileURL;
	}

	@CreoleParameter
	public void setSuperClassFileURL(URL superClassFileURL) {
		this.superClassFileURL = superClassFileURL;
	}

	// ############### getters and setter ###########

} // class LuceneGazPR
