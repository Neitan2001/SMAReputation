import java.io.Serializable;

import jade.core.AID;

public class ReputationObject implements Serializable{
	private static final long serialVersionUID = 1L;
	private AID sender;
	private Double satisfation;
	
	public ReputationObject(AID sender, Double satisfation) {
		super();
		this.sender = sender;
		this.satisfation = satisfation;
	}

	public AID getSender() {
		return sender;
	}

	public void setSender(AID sender) {
		this.sender = sender;
	}

	public Double getSatisfation() {
		return satisfation;
	}

	public void setSatisfation(Double satisfation) {
		this.satisfation = satisfation;
	}
	
	
}
