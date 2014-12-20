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
