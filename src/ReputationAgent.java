import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

public class ReputationAgent extends Agent {

	private static final long serialVersionUID = 1L;
	
	private Hashtable<AID, List<Double>> reputationLog;
	private Hashtable<AID, Double> reputationNumber;
	
	protected void setup() {
		reputationLog = new Hashtable<AID, List<Double>>();
		reputationNumber = new Hashtable<AID, Double>();
		
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("reputation");
		sd.setName("JADE-basic-reputation");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		addBehaviour(new InicialReputationServer());
		addBehaviour(new UpdateReputationServer());
		addBehaviour(new ConsultReputationList());
	}
	
	private class InicialReputationServer extends CyclicBehaviour {

		private static final long serialVersionUID = 1L;
		
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				AID sender = msg.getSender();
				reputationLog.put(sender, new ArrayList<Double>(Arrays.asList(5.0)));
				reputationNumber.put(sender, 5.0);
				System.out.println("Reputação do agente " + sender.getLocalName() + " é " + reputationNumber.get(sender) );
			} else {
				block();
			}
		}
		
	}
	
	private class ConsultReputationList extends CyclicBehaviour {

		private static final long serialVersionUID = 1L;

		public void action() {
			MessageTemplate mt = MessageTemplate.and(
				    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
				    MessageTemplate.MatchOntology("consulta")
			);
			ACLMessage msg = myAgent.receive(mt);
			
			if (msg != null) {
				ACLMessage consulta = new ACLMessage(ACLMessage.CONFIRM);
				consulta.addReceiver(msg.getSender());
				try {
					consulta.setContentObject(reputationNumber);
				} catch (IOException e) {
					e.printStackTrace();
				}
				consulta.setOntology("consulta");
				send(consulta);
			} else {
				block();
			}
			
		}
		
	}
	
	private class UpdateReputationServer extends CyclicBehaviour {
		
		private static final long serialVersionUID = 1L;
		
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
				    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
				    MessageTemplate.MatchOntology("avaliacao")
			);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				try {
					ReputationObject obj = (ReputationObject) msg.getContentObject();
					List<Double> reputacoes = reputationLog.get(obj.getSender());
					reputacoes.add(obj.getSatisfation());
					
					// Calcula a média das reputações
			        double soma = 0.0;
			        for (double reputacao : reputacoes) {
			            soma += reputacao;
			        }
			        
			        double media = soma / reputacoes.size();
			        reputationNumber.put(obj.getSender(), media);
			        System.out.println("A nova reputação do agente " + obj.getSender().getLocalName() + " é " + reputationNumber.get(obj.getSender()));
				} catch (UnreadableException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				block();
			}
		}
	}
	
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		System.out.println("Reputation-agent "+getAID().getName()+" terminating.");
	}

}
