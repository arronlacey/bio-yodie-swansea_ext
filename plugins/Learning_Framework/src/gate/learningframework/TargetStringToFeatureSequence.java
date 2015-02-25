/*
 * TargetStringToFeatureSequence.java
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

import cc.mallet.types.*;
import cc.mallet.util.CharSequenceLexer;
import cc.mallet.pipe.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/*
 * As part of the endeavour to get all data prep neatly into one Mallet pipe,
 * this class splits a string into a sequence of features. Mallet offers
 * various functionality in this area, but not this specific one.
 */

public class TargetStringToFeatureSequence extends Pipe implements Serializable {
	CharSequenceLexer lexer;

	public TargetStringToFeatureSequence (Pattern regex) {
		super(null, new Alphabet());
		this.lexer = new CharSequenceLexer (regex);
	}

	public Instance pipe(Instance carrier) {
		if (! (carrier.getTarget() instanceof String)) {
			throw new IllegalArgumentException("Target must be of type String");
		}

		CharSequence string = (CharSequence) carrier.getTarget();
		lexer.setCharSequence(string);
		
		//Targets means labels
		List<String> targets = new ArrayList<String>();
		
		while(lexer.hasNext()){
			String t = (String)lexer.next();
			targets.add(t);
		}

		//Now we have the labels, but to create a FeatureSequence, we need the
		//indices. Pipes pass the same alphabet through, so we can use this
		//alphabet to map from label to index or add it if necessary. Alphabet
		//maps from label to label index.
		int[] indices = new int[targets.size()];

		for (int i=0; i<targets.size(); i++) {
			indices[i] = getTargetAlphabet().lookupIndex(targets.get(i), true);
		}

		//Create the new feature sequence.
		FeatureSequence target = new FeatureSequence(getTargetAlphabet(), indices);

		carrier.setTarget(target);
		
		return carrier;
	}

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 0;

}
