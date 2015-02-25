/*
 * Operation.java
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

public enum Operation {
	TRAIN, 
	APPLY_CURRENT_MODEL, 
	EVALUATE_X_FOLD, 
	EVALUATE_HOLDOUT, 
	EXPORT_ARFF, 
	EXPORT_ARFF_THRU_CURRENT_PIPE, 
	EXPORT_ARFF_NUMERIC_CLASS,
	EXPORT_ARFF_NUMERIC_CLASS_THRU_CURRENT_PIPE;
}
