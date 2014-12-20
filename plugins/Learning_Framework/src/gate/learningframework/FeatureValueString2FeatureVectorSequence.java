package gate.learningframework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.util.CharSequenceLexer;

/*
 * I wrote this class because Mallet doesn't offer this particular weird thing ..
 * We need to take a sequence of features and split them up into a sequence of
 * bunches of features. It's because we are doing multifeature sequence
 * classification. Often people just use the one feature for sequence
 * classification, such as the string, but we want to offer multiple features,
 * for example, POS etc. Mallet CRF is happy to take more features
 * for training and application, but for data prep we want to create a pipe 
 * that does the whole job in one go and gets saved neatly with the model, 
 * for future use, so that's why I wrote this class.
 */

public class FeatureValueString2FeatureVectorSequence extends Pipe implements Serializable {
	CharSequenceLexer lexer;

	public FeatureValueString2FeatureVectorSequence (Pattern regex) {
		super(new Alphabet(), null);
		this.lexer = new CharSequenceLexer (regex);
	}

	public Instance pipe(Instance carrier) {
		if (! (carrier.getData() instanceof CharSequence)) {
			throw new IllegalArgumentException("Target must be of type CharSequence");
		}

		lexer.setCharSequence(carrier.getData().toString());

		List<String> instances = new ArrayList<String>();

		while(lexer.hasNext()){
			String t = (String)lexer.next();
			instances.add(t);
		}

		FeatureVector[] fva = new FeatureVector[instances.size()];

		for(int i=0;i<instances.size();i++){
			String instance = instances.get(i);

			String[] fs = instance.split(" ");

			//Make arrays of features and values for this new FeatureVector
			//FeatureVector supports numeric features

			Object[] featureNames = new Object[fs.length];
			double[] featureValues = new double[fs.length];

			for(int j=0;j<fs.length;j++){
				String feature = fs[j];
				if (feature.contains("=")) {
					String[] subFields = feature.split("=");
					featureNames[j] = subFields[0];
					featureValues[j] = Double.parseDouble(subFields[1]);
				} else {
					featureNames[j] = feature;
					featureValues[j] = 1.0;
				}
			}

			//Make a new FeatureVector and include the data alphabet
			FeatureVector fv = new FeatureVector(
					getDataAlphabet(), featureNames, featureValues);
			fva[i] = fv;
		}

		//Make the new FeatureVectorSequence
		FeatureVectorSequence data = new FeatureVectorSequence(fva);

		carrier.setData(data);
		return carrier;
	}

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;
}
