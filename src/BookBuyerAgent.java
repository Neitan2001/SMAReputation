/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
 *****************************************************************/

//package examples.bookTrading;

import jade.core.Agent;

import java.io.IOException;
import java.util.Hashtable;

import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class BookBuyerAgent extends Agent {

	private static final long serialVersionUID = 1L;
	// The title of the book to buy
	private String targetBookTitle;
	// The list of known seller agents
	private AID[] sellerAgents;
	private Double satisfation;

	// Put agent initializations here
	protected void setup() {
		// Printout a welcome message
		System.out.println("Hallo! Buyer-agent "+getAID().getName()+" is ready.");

		// Get the title of the book to buy as a start-up argument
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetBookTitle = (String) args[0];
			satisfation = Double.parseDouble((String) args[1]);
			System.out.println("Target book is "+targetBookTitle);

			// Add a TickerBehaviour that schedules a request to seller agents every 10 seconds
			addBehaviour(new TickerBehaviour(this, 10000) {

				private static final long serialVersionUID = 1L;

				protected void onTick() {
					System.out.println("Trying to buy "+targetBookTitle);
					// Update the list of seller agents
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("book-selling");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						System.out.println("Found the following seller agents:");
						sellerAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							sellerAgents[i] = result[i].getName();
							System.out.println(sellerAgents[i].getName());
						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// Perform the request
					myAgent.addBehaviour(new RequestPerformer());
				}
			} );
		}
		else {
			// Make the agent terminate
			System.out.println("No target book title specified");
			doDelete();
		}
	}


	/**
	   Inner class RequestPerformer.
	   This is the behaviour used by Book-buyer agents to request seller 
	   agents the target book.
	 */
	private class RequestPerformer extends Behaviour {

		private static final long serialVersionUID = 1L;
		private AID bestSeller; // The agent who provides the best offer 
		private int bestPrice;  // The best offered price
		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;
		private DFAgentDescription[] reputationAgent;
		private Hashtable<AID, Double> reputationNumber = new Hashtable<AID, Double>();

		public void action() {
			switch (step) {
			case 0:
				// Procurar o agente de reputação
				
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd1 = new ServiceDescription();
				sd1.setType("reputation");
				template.addServices(sd1);
				
				try {
					reputationAgent = DFService.search(myAgent, template);
				} catch (FIPAException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
				} 
				cfp.setContent(targetBookTitle);
				cfp.setConversationId("book-trade");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// mandar requisição para obter lista de reputação
				
				ACLMessage consulta = new ACLMessage(ACLMessage.REQUEST);
				consulta.addReceiver(reputationAgent[0].getName());
				consulta.setOntology("consulta");
				send(consulta);
				step = 2;
				break;
			case 2:
				MessageTemplate mtReputation = MessageTemplate.and(
						MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
						MessageTemplate.MatchOntology("consulta"));
				ACLMessage consultResponse = myAgent.receive(mtReputation);
				
				if (consultResponse != null) {
					try {
						reputationNumber = (Hashtable<AID, Double>) consultResponse.getContentObject();
					} catch (UnreadableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					block();
				}
				step = 3;
				break;
			case 3:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 
						int price = Integer.parseInt(reply.getContent());
						double reputation = reputationNumber.getOrDefault(reply.getSender(), 0.0);
						if (reputation > 4 && (bestSeller == null || price < bestPrice)) {
							// This is the best offer at present
							bestPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= sellerAgents.length) {
						// We received all replies
						step = 4; 
					}
				}
				else {
					block();
				}
				break;
			case 4:
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetBookTitle);
				order.setConversationId("book-trade");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 5;
				break;
			case 5:      
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						// Registrar reputação
						
						try {
							ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
							msg.addReceiver(reputationAgent[0].getName());
							ReputationObject obj = new ReputationObject(reply.getSender(), satisfation);
							msg.setContentObject(obj);
							msg.setOntology("avaliacao");
							send(msg);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						System.out.println(targetBookTitle+" successfully purchased from agent "+reply.getSender().getName());
						System.out.println("Price = "+bestPrice);
						myAgent.doDelete();
					}
					else {
						System.out.println("Attempt failed: requested book already sold.");
					}

					step = 6;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			
			if (step == 4 && bestSeller == null) {
				System.out.println("Attempt failed: "+targetBookTitle+" not available for sale");
			}
			
			boolean bookIsNotAvailable = (step == 4 && bestSeller == null);
			boolean negotiationIsConcluded = (step == 6);
			
			boolean isDone = false;
			if (bookIsNotAvailable || negotiationIsConcluded) {
				isDone = true;
			}
			else {
				isDone = false;
			}
			
			return isDone;
			//return ((step == 2 && bestSeller == null) || step == 4);
		}
	}  // End of inner class RequestPerformer
	
	
	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Buyer-agent "+getAID().getName()+" terminating.");
	}
	
}

