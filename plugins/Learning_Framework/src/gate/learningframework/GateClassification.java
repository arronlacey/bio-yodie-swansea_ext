/*
 * GateClassification.java
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

public class GateClassification {
	private Annotation instance;
	private String classAssigned;
	private Double confidenceScore;
	private Integer seqSpanID;
	
	public GateClassification(Annotation instance, String classAssigned,
			Double confidenceScore) {
		this.instance = instance;
		this.classAssigned = classAssigned;
		this.confidenceScore = confidenceScore;
	}

	public GateClassification(Annotation instance, String classAssigned,
			Double confidenceScore, Integer sequenceSpanID) {
		this.instance = instance;
		this.classAssigned = classAssigned;
		this.confidenceScore = confidenceScore;
		this.seqSpanID = sequenceSpanID;
	}
	
	public Annotation getInstance() {
		return instance;
	}
	public void setInstance(Annotation instance) {
		this.instance = instance;
	}
	public String getClassAssigned() {
		return classAssigned;
	}
	public void setClassAssigned(String classAssigned) {
		this.classAssigned = classAssigned;
	}
	public Double getConfidenceScore() {
		return confidenceScore;
	}
	public void setConfidenceScore(Double confidenceScore) {
		this.confidenceScore = confidenceScore;
	}

	public Integer getSeqSpanID() {
		return seqSpanID;
	}

	public void setSeqSpanID(Integer sequenceSpanID) {
		this.seqSpanID = sequenceSpanID;
	}
}
