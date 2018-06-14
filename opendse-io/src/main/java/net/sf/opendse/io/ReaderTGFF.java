package net.sf.opendse.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.sf.opendse.model.Application;
import net.sf.opendse.model.Communication;
import net.sf.opendse.model.Dependency;
import net.sf.opendse.model.Link;
import net.sf.opendse.model.LinkTypes;
import net.sf.opendse.model.Mapping;
import net.sf.opendse.model.Mappings;
import net.sf.opendse.model.Resource;
import net.sf.opendse.model.ResourceTypes;
import net.sf.opendse.model.SpecificationTypeBased;
import net.sf.opendse.model.Task;

/**
 * The {@link ReaderTGFF} imports an {@link Application}, {@link Mappings} and a
 * set of {@link Resource} types from a .tgff-{@link File}, as generated by Task
 * Graphs For Free (TGFF).
 * 
 * In particular, it serves to import benchmark task graphs, processor types and
 * corresponding mapping possibilities from the Embedded Systems Synthesis
 * Benchmarks Suite (E3S). See <a href=
 * "http://ziyang.eecs.umich.edu/~dickrp/e3s/">http://ziyang.eecs.umich.edu/~dickrp/e3s/</a>
 * 
 * @author Valentina Richthammer
 */
public class ReaderTGFF {

	public static final String TGFF_TYPE = "TGFF_TYPE";
	public static final String PERIOD = "PERIOD";
	public static final String MSG_SIZE = "MSG_SIZE";
	public static final String RES_ATTRIBUTES = "RES_ATTRIBUTES";
	public static final String RES_VALUES = "RES_VALUES";
	public static final String HARD_DEADLINE = "HARD_DEADLINE";
	public static final String SOFT_DEADLINE = "SOFT_DEADLINE";
	public static final String WIRE = "@WIRING";

	protected static final String HYPERPERIOD = "@HYPERPERIOD";
	protected static final String TASK_GRAPH = "@TASK_GRAPH";
	protected static final String COMMUN_QUANT = "@COMMUN_QUANT";
	protected static final String CORE = "@CORE";
	protected static final String CLIENT_PE = "@CLIENT_PE";
	protected static final String SERVER_PE = "@SERVER_PE";
	protected static final String PROC = "@PROC";

	protected static final String LINK = "LINK";
	protected static final String TASK = "TASK";
	protected static final String ARC = "ARC";

	protected static final String VALID = "valid";
	protected static final String TYPE = "type";

	protected static final String CLOSING = "}";
	protected static final String COMMENT = "#";
	protected static final String AT = "@";
	protected static final String SEPARATOR = "\\s+";
	protected static final String CONNECTOR = "_";
	protected static final String HEADER = "#---------";

	protected Map<String, String> properties;
	protected Map<String, Double> messageSizes;
	protected double hyperperiod;

	protected Map<String, List<Task>> tgffTypeMap = new HashMap<String, List<Task>>();

	/**
	 * Reads a {@link SpecificationTypeBased} from a tgff-file.
	 * 
	 * @param filename
	 *            the file name
	 * @return the specification
	 */
	public SpecificationTypeBased read(String filename) {
		return read(new File(filename));
	}

	/**
	 * Reads a {@link SpecificationTypeBased} from a {@link File}.
	 * 
	 * @param file
	 *            the file
	 * @return the specification
	 */
	public SpecificationTypeBased read(File file) {
		try {
			return read(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Reads a {@link SpecificationTypeBased} from an {@link InputStream}.
	 * 
	 * @param in
	 *            the input stream
	 * @return the specification
	 */
	public SpecificationTypeBased read(InputStream in) {

		List<String> tgffFile = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String currentLine;
			while ((currentLine = br.readLine()) != null) {
				tgffFile.add(currentLine);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return toSpecification(tgffFile);
	}

	/**
	 * Reads {@link Application}, {@link Mappings}, {@link ResourceTypes} and
	 * {@link LinkTypes} from a tgff-{@link File} and converts it into a
	 * {@link SpecificationTypeBased}.
	 * 
	 * @param in
	 *            the list of lines contained in the tgff-file
	 * @return the type-based specification
	 */
	public SpecificationTypeBased toSpecification(List<String> in) {

		Application<Task, Dependency> application = toApplication(in);
		ResourceTypes<Resource> resourceTypes = toResourceTypes(in);
		Mappings<Task, Resource> mappings = toMappings(in, resourceTypes);

		return new SpecificationTypeBased(application, resourceTypes, mappings, toLinkTypes(in));
	}

	/**
	 * Reads an application from a tgff-file.
	 * 
	 * @param in
	 *            the list of lines contained in the tgff-file
	 * @return the application
	 */
	protected Application<Task, Dependency> toApplication(List<String> in) {

		Application<Task, Dependency> application = new Application<Task, Dependency>();

		Iterator<String> it = in.iterator();
		String currentLine;

		while (it.hasNext()) {
			currentLine = it.next();

			// import hyperperiod
			if (currentLine.contains(HYPERPERIOD)) {
				this.hyperperiod = importHyperperiod(currentLine);
			}

			// import message sizes
			else if (currentLine.contains(COMMUN_QUANT)) {
				this.messageSizes = importMessageSizes(it);
			}

			// import application graphs
			else if (currentLine.contains(TASK_GRAPH)) {
				importTaskGraph(currentLine, it, application);
			}
		}
		return application;
	}

	/**
	 * Reads the database of {@link ResourceTypes} from a tgff-file.
	 * 
	 * @param in
	 *            the list of lines contained in the tgff-file
	 * @return the resource types
	 */
	protected ResourceTypes<Resource> toResourceTypes(List<String> in) {

		ResourceTypes<Resource> resourceTypes = new ResourceTypes<Resource>();

		Iterator<String> it = in.iterator();
		String currentLine;

		while (it.hasNext()) {
			currentLine = it.next();

			// import resources and mappings (only mappings to valid
			// resource types are created)
			if (currentLine.contains(CORE) || currentLine.contains(PROC) || currentLine.contains(CLIENT_PE)
					|| currentLine.contains(SERVER_PE)) {
				importCore(currentLine, it, resourceTypes);
			}
		}
		return resourceTypes;
	}

	/**
	 * Reads the task-to-type {@link Mappings} from a tgff-file.
	 * 
	 * @param in
	 *            the list of lines contained in the tgff-file
	 * @param resourceTypes
	 *            the resource types
	 * @return the mappings
	 */
	protected Mappings<Task, Resource> toMappings(List<String> in, ResourceTypes<Resource> resourceTypes) {

		Mappings<Task, Resource> mappings = new Mappings<Task, Resource>();

		Iterator<String> it = in.iterator();
		String currentLine;

		while (it.hasNext()) {
			currentLine = it.next();

			// import mappings (to valid resource types)
			if (currentLine.contains(CORE) || currentLine.contains(PROC) || currentLine.contains(CLIENT_PE)
					|| currentLine.contains(SERVER_PE)) {
				importMappings(currentLine, it, resourceTypes, mappings);
			}
		}
		return mappings;
	}

	/**
	 * Reads the {@link LinkTypes} from a tgff-file.
	 * 
	 * @param in
	 *            the list of lines contained in the tgff-file
	 * @return the link types
	 */
	protected LinkTypes<Link> toLinkTypes(List<String> in) {

		LinkTypes<Link> linkTypes = new LinkTypes<Link>();

		Iterator<String> it = in.iterator();
		String currentLine;

		while (it.hasNext()) {
			currentLine = it.next();
			// import -mocsyn wiring-properties as link
			if (currentLine.contains(WIRE)) {
				importLink(it, linkTypes);
			}
		}
		return linkTypes;
	}

	/**
	 * Imports a task graph.
	 * 
	 * @param name
	 *            the tgff-line containing the id of the task graph
	 * @param it
	 *            an iterator on the list of lines containing the task graph
	 * @param application
	 *            the application
	 */
	protected void importTaskGraph(String name, Iterator<String> it, Application<Task, Dependency> application) {

		String id = CONNECTOR + name.split(SEPARATOR)[1];

		String line = "";
		double period = -1;

		while (!isClosing(line) && it.hasNext()) {

			line = it.next();
			if (!isComment(line)) {

				if (line.contains(PERIOD)) {
					period = Double.parseDouble(line.replace(PERIOD, "").replaceAll(SEPARATOR, ""));
				}

				else if (line.contains(TASK)) {
					addTask(line, id, period, application);
				}

				else if (line.contains(ARC)) {
					addCommunication(line, id, period, application);
				}

				else if (line.contains(HARD_DEADLINE)) {
					addDeadline(line, id, application, HARD_DEADLINE);
				} else if (line.contains(SOFT_DEADLINE)) {
					addDeadline(line, id, application, SOFT_DEADLINE);
				}
			}
		}
	}

	/**
	 * Adds a {@link Task} node to the {@link Application}.
	 * 
	 * @param line
	 *            the tgff-line containing the arc between two connected tasks
	 * @param suffix
	 *            the suffix of the task name
	 * @param period
	 *            the period of the communication
	 * @param application
	 *            the application
	 */
	protected void addTask(String line, String suffix, double period, Application<Task, Dependency> application) {

		String[] entries = line.trim().split(SEPARATOR);
		assert entries.length >= 4 : "tgff-file \"" + TASK + "\": wrong number of entries";

		String id = entries[1] + suffix;
		String type = entries[3];

		Task task = new Task(id);
		task.setAttribute(PERIOD, period);
		task.setAttribute(TGFF_TYPE, type);

		// for more efficient generation of resource type mappings
		if (tgffTypeMap.containsKey(type)) {
			List<Task> taskList = tgffTypeMap.get(type);
			taskList.add(task);
		} else {
			LinkedList<Task> taskList = new LinkedList<Task>();
			taskList.add(task);

			tgffTypeMap.put(type, taskList);
		}
		application.addVertex(task);
	}

	/**
	 * Adds a {@link Communication} node (message) between two connected
	 * {@link Task}s of the {@link Application}.
	 * 
	 * @param line
	 *            the tgff-line containing the arc between two connected tasks
	 * @param suffix
	 *            the suffix of the task name
	 * @param period
	 *            the period of the communication
	 * @param application
	 *            application
	 */
	protected void addCommunication(String line, String suffix, double period,
			Application<Task, Dependency> application) {

		String[] entries = line.trim().split(SEPARATOR);
		assert entries.length == 8 : "tgff-file \"ARC\": wrong number of entries in line";

		String id = entries[1];
		String tgffType = entries[7];

		Communication comm = new Communication(id);
		comm.setAttribute(PERIOD, period);
		comm.setAttribute(TGFF_TYPE, tgffType);

		if (messageSizes != null && messageSizes.containsKey(tgffType)) {
			comm.setAttribute(MSG_SIZE, messageSizes.get(entries[7]));
		}

		Task t1 = application.getVertex(entries[3] + suffix);
		Task t2 = application.getVertex(entries[5] + suffix);

		application.addVertex(comm);
		application.addEdge(new Dependency(id + "_0"), t1, comm);
		application.addEdge(new Dependency(id + "_1"), comm, t2);
	}

	/**
	 * Adds a (soft or hard) deadline to a specified {@link Task}.
	 * 
	 * @param line
	 *            the tgff-line containing the deadline and task
	 * @param suffix
	 *            the suffix of the task name
	 * @param application
	 *            the application
	 * @param deadlineType
	 *            the type of deadline (soft or hard)
	 */
	protected void addDeadline(String line, String suffix, Application<Task, Dependency> application,
			String deadlineType) {

		String[] entries = line.trim().split(SEPARATOR);
		assert entries.length == 6 : "tgff-file \"" + deadlineType + "\": wrong number of entries";

		Task t = application.getVertex(entries[3] + suffix);
		assert t != null : "error in tgff file: task " + entries[3]
				+ " does not exist, so cannot be assigned a deadline.";

		t.setAttribute(deadlineType, Double.parseDouble(entries[5]));
	}

	/**
	 * Imports a type of {@link Resource}.
	 * 
	 * @param name
	 *            the tgff-line containing the name of the resource type
	 * @param it
	 *            an iterator on the list of lines containing the resource type
	 * @param resourceTypes
	 *            the resource types
	 */
	protected void importCore(String name, Iterator<String> it, ResourceTypes<Resource> resourceTypes) {

		// create resource (type)
		String id = "r" + name.split(SEPARATOR)[1];
		Resource res = new Resource(id);

		// first line contains attributes of resources
		String[] resAttributes;

		resAttributes = (it.next()).replace(COMMENT, "").trim().split(SEPARATOR);

		// second line contains attribute values
		String[] resValues = it.next().trim().split(SEPARATOR);

		assert resAttributes.length == resValues.length : "tgff-file \"" + CORE + "\": number of values is not "
				+ "equal to required number of resource attributes";

		for (int i = 0; i < resAttributes.length; i++) {
			res.setAttribute(resAttributes[i], resValues[i]);
		}
		resourceTypes.put(id, res);
	}

	/**
	 * Imports the task-to-type {@link Mappings}.
	 * 
	 * @param name
	 *            the tgff-line containing the name of the resource type
	 * @param it
	 *            an iterator on the list of lines containing the mappings
	 * @param resourceTypes
	 *            the resource types
	 * @param mappings
	 *            the mappings
	 */
	protected void importMappings(String name, Iterator<String> it, ResourceTypes<Resource> resourceTypes,
			Mappings<Task, Resource> mappings) {

		// create resource (type)
		String id = "r" + name.split(SEPARATOR)[1];
		Resource res = resourceTypes.get(id);

		// skip resource type information (already imported in
		// toResourceTypes())
		while (skip(it.next())) {
		}

		// create mappings
		String line;
		List<String> attributes = new LinkedList<String>();

		while (!isClosing((line = it.next()))) {

			// extract attributes of resource type
			if (line.contains(TYPE)) {
				attributes = new LinkedList<String>(Arrays.asList(line.replace(COMMENT, "").trim().split(SEPARATOR)));
			}
			// extract values for each attribute
			else if (!isComment(line) && line.length() > 0) {

				String[] values = line.trim().split(SEPARATOR);

				assert values.length == attributes.size() : "tgff-file \"" + CORE + "\": number of values is not "
						+ "equal to required number of attributes";

				String tgffType = values[0];

				// only add mappings to valid resource type
				boolean validMapping = false;

				if (attributes.contains(VALID)) {
					validMapping = values[attributes.indexOf(VALID)].equals("1") ? true : false;
				}

				// if tasks exist that can be mapped to current resource type
				if (validMapping && tgffTypeMap.containsKey(tgffType)) {

					for (Task task : tgffTypeMap.get(tgffType)) {
						String mappingID = "m" + CONNECTOR + task.getId() + CONNECTOR + res.getId();
						Mapping<Task, Resource> mapping = new Mapping<Task, Resource>(mappingID, task, res);

						// annotate extracted attributes and values
						for (int i = 0; i < values.length; i++) {
							mapping.setAttribute(attributes.get(i), values[i]);
						}
						mappings.add(mapping);
					}
				}
			}
		}
	}

	/**
	 * Imports the different message sizes, depending on the type of
	 * {@link Communication}.
	 * 
	 * @param it
	 *            an iterator on the list of lines containing the message sizes
	 * @return a map storing message types and corresponding message sizes
	 */
	protected Map<String, Double> importMessageSizes(Iterator<String> it) {

		Map<String, Double> sizes = new HashMap<String, Double>();
		String line;

		while (!isClosing(line = it.next())) {
			if (!isComment(line)) {
				String[] entries = line.trim().split(SEPARATOR);
				assert entries.length >= 2 : "tgff-file \"" + COMMUN_QUANT + "\": wrong number of entries";

				sizes.put(entries[0], Double.valueOf(entries[1]));
			}
		}
		return sizes;
	}

	/**
	 * Imports one type of {@link Link} and adds it to the {@link LinkTypes}.
	 * 
	 * @param it
	 *            an iterator on the list of lines containing a link type
	 * @param linkTypes
	 *            the linkTypes
	 */
	protected void importLink(Iterator<String> it, LinkTypes<Link> linkTypes) {

		Link link = new Link(WIRE);

		String currentLine;
		String property = "";

		while (!isClosing(currentLine = it.next())) {

			// get attribute name
			if (isComment(currentLine)) {
				property = currentLine.replace(COMMENT, "").trim();
			}
			// get corresponding attribute value
			else {
				link.setAttribute(property, currentLine);
			}
		}
		linkTypes.put(WIRE, link);
	}

	protected double importHyperperiod(String line) {
		return Double.parseDouble(line.replace(HYPERPERIOD, "").trim());
	}

	protected boolean isComment(String line) {
		return line.startsWith(COMMENT);
	}

	protected boolean isClosing(String line) {
		return (line.contains(CLOSING) && !line.contains(COMMENT));
	}

	protected boolean skip(String line) {
		return !(line.contains(HEADER));
	}
}
