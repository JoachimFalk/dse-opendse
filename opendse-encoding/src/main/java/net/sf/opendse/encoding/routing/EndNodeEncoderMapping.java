package net.sf.opendse.encoding.routing;

import java.util.HashSet;
import java.util.Set;

import org.opt4j.satdecoding.Constraint;
import org.opt4j.satdecoding.Constraint.Operator;

import net.sf.opendse.encoding.constraints.Constraints;
import net.sf.opendse.encoding.variables.M;
import net.sf.opendse.encoding.variables.MappingVariable;
import net.sf.opendse.encoding.variables.Variable;
import net.sf.opendse.encoding.variables.Variables;
import net.sf.opendse.model.Architecture;
import net.sf.opendse.model.Communication;
import net.sf.opendse.model.Link;
import net.sf.opendse.model.Resource;
import net.sf.opendse.model.Task;
import net.sf.opendse.model.properties.ResourcePropertyService;

/**
 * The {@link EndNodeEncoderMapping} formulates {@link Constraint}s that place
 * the routing end-points on the mapping targets of the neighbor {@link Task}s
 * of the {@link Communication} that is being routed.
 * 
 * @author Fedor Smirnov
 *
 */
public class EndNodeEncoderMapping implements EndNodeEncoder {

	@Override
	public Set<Constraint> toConstraints(CommunicationFlow communicationFlow, Architecture<Resource, Link> routing,
			Set<MappingVariable> mappingVariables) {
		Set<Constraint> endNodeConstraints = new HashSet<Constraint>();
		Task srcTask = communicationFlow.getSourceDTT().getSourceTask();
		Task desTask = communicationFlow.getDestinationDTT().getDestinationTask();
		for (Resource res : routing) {
			Set<M> srcMappings = new HashSet<M>();
			Set<M> destMappings = new HashSet<M>();
			for (MappingVariable mappingVar : mappingVariables) {
				if (mappingVar instanceof M) {
					M mVar = (M) mappingVar;
					String proxyId = ResourcePropertyService.getProxyId(mVar.getMapping().getTarget());
					if (proxyId.equals(res.getId())) {
						if (mVar.getMapping().getSource().equals(srcTask)) {
							srcMappings.add(mVar);
						}
						if (mVar.getMapping().getSource().equals(desTask)) {
							destMappings.add(mVar);
						}
					}
				}
			}
			endNodeConstraints.addAll(makeEndNodeConstraints(communicationFlow, res, srcMappings, true));
			endNodeConstraints.addAll(makeEndNodeConstraints(communicationFlow, res, destMappings, false));
		}
		return endNodeConstraints;
	}

	/**
	 * Formulates the {@link Constraint}s stating that a {@link Resource} is an end
	 * node of the routing of a {@link CommunicationFlow} if the corresponding tasks
	 * (source or destination task of the communication flow are mapped onto it).
	 * 
	 * @param commFlow
	 *            the {@link CommunicationFlow} that is being routed
	 * @param res
	 *            the current {@link Resource}
	 * @param mappingVars
	 *            the {@link M} variables encoding the mappings of the neighbor
	 *            tasks of the communication onto the current resource
	 * @param source
	 *            {@code true} if the method is used to encode the source resources,
	 *            {@code false} if the method is used to encode the destination
	 *            resources
	 * @return the {@link Constraint}s stating that a {@link Resource} is an end
	 *         node of the routing of a {@link CommunicationFlow} if the
	 *         corresponding tasks (source or destination task of the communication
	 *         flow are mapped onto it)
	 */
	protected Set<Constraint> makeEndNodeConstraints(CommunicationFlow commFlow, Resource res, Set<M> mappingVars,
			boolean source) {
		Set<Constraint> result = new HashSet<Constraint>();
		Variable endNodeVariable = source ? Variables.varDDsR(commFlow, res) : Variables.varDDdR(commFlow, res);
		if (mappingVars.isEmpty()) {
			Constraint setToZero = new Constraint(Operator.EQ, 0);
			setToZero.add(Variables.p(endNodeVariable));
			result.add(setToZero);
		} else if (mappingVars.size() == 1) {
			M mappingVar = mappingVars.iterator().next();
			Set<Variable> conditions = new HashSet<Variable>();
			conditions.add(mappingVar);
			conditions.add(commFlow.getSourceDTT());
			conditions.add(commFlow.getDestinationDTT());
			result.addAll(Constraints.generateAndConstraints(conditions, endNodeVariable));
			
		}else {
			throw new IllegalArgumentException("More than one mapping between same task and resource");
		}
		return result;
	}
}