package net.sf.opendse.encoding.routing;

import java.util.HashSet;
import java.util.Set;

import org.opt4j.satdecoding.Constraint;

import net.sf.opendse.encoding.variables.ApplicationVariable;
import net.sf.opendse.encoding.variables.MappingVariable;
import net.sf.opendse.encoding.variables.T;
import net.sf.opendse.model.Architecture;
import net.sf.opendse.model.Link;
import net.sf.opendse.model.Resource;

public abstract class CommunicationRoutingEncoderAbstract implements CommunicationRoutingEncoder {

	protected final OneDirectionEncoder oneDirectionEncoder;
	protected final CycleBreakEncoder cycleBreakEncoder;
	protected final CommunicationHierarchyEncoder hierarchyEncoder;
	protected final CommunicationFlowRoutingManager communicationFlowRoutingManager;
	protected final AdditionalRoutingConstraintsEncoder additionalConstraintsEncoder;
	protected final ProxyEncoder proxyEncoder;

	public CommunicationRoutingEncoderAbstract(OneDirectionEncoder oneDirectionEncoder,
			CycleBreakEncoder cycleBreakEncoder, CommunicationHierarchyEncoder hierarchyEncoder,
			CommunicationFlowRoutingManager communicationFlowRoutingManager, ProxyEncoder proxyEncoder,
			AdditionalRoutingConstraintsEncoder additionalConstraintsEncoder) {
		this.oneDirectionEncoder = oneDirectionEncoder;
		this.cycleBreakEncoder = cycleBreakEncoder;
		this.hierarchyEncoder = hierarchyEncoder;
		this.additionalConstraintsEncoder = additionalConstraintsEncoder;
		this.proxyEncoder = proxyEncoder;
		this.communicationFlowRoutingManager = communicationFlowRoutingManager;
	}

	@Override
	public Set<Constraint> toConstraints(T communicationVariable, Set<CommunicationFlow> commFlows,
			Architecture<Resource, Link> routing, Set<MappingVariable> mappingVariables,
			Set<ApplicationVariable> applicationVariables) {
		Set<Constraint> routingConstraints = new HashSet<Constraint>();
		// Ensures that links are used in one direction only.
		routingConstraints.addAll(oneDirectionEncoder.toConstraints(communicationVariable, routing));
		// Ensures cycle freedom.
		routingConstraints.addAll(cycleBreakEncoder.toConstraints(communicationVariable, routing));
		// Encodes the variable hierarchy.
		routingConstraints.addAll(hierarchyEncoder.toConstraints(communicationVariable, commFlows, routing));
		routingConstraints.addAll(proxyEncoder.toConstraints(communicationVariable.getTask(), routing, mappingVariables,applicationVariables));
		// Gets the appropriate Encoder for each communication flow.
		for (CommunicationFlow communicationFlow : commFlows) {
			CommunicationFlowRoutingEncoder commFlowEncoder = communicationFlowRoutingManager
					.getEncoder(communicationFlow);
			routingConstraints.addAll(commFlowEncoder.toConstraints(communicationFlow, routing, mappingVariables));
		}
		// Encodes additional constraints
		routingConstraints
				.addAll(additionalConstraintsEncoder.toConstraints(communicationVariable, commFlows, routing));
		return routingConstraints;
	}
}