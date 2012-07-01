package hudson.plugins.ec2;

import hudson.model.BuildListener;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * When added to a build, all the useful variables about the given machines are added to the build's environment.
 *
 * User: Joel Johnson
 * Date: 6/30/12
 * Time: 5:43 PM
 */
public class Ec2MachineVariables extends EnvironmentAction {
	public Ec2MachineVariables(Iterable<EC2Slave> machines, PrintStream listener) {
		super(getVariablesFromMachines(machines, listener));
	}

	private static Map<String, String> getVariablesFromMachines(Iterable<EC2Slave> machines, PrintStream listener) {
		Map<String, String> result = new HashMap<String, String>();
		StringBuilder instanceIds = new StringBuilder();
		for (EC2Slave slave : machines) {
			String id = slave.getInstanceId();
			instanceIds.append(id).append(' ');

			addVariable(result, listener, id + "_publicDns", slave.getPublicDNS());
			addVariable(result, listener, id + "_privateDns", slave.getPrivateDNS());
			addVariable(result, listener, id + "_jvmopts", slave.getJvmopts());
			addVariable(result, listener, id + "_remoteAdmin", slave.getRemoteAdmin());
		}
		addVariable(result, listener, "instances", instanceIds.toString().trim());

		return result;
	}

	/**
	 * Adds the given name/value pair to the map and also logs it to the given PrintStream
	 */
	private static void addVariable(Map<String, String> mapToAddTo, PrintStream listener, String name, String value) {
		if(value == null) {
			listener.println(name + " was " + value + ". Setting to empty string.");
			value = "";
		}
		listener.println("'" + name + "' => '" + value + "'");
		mapToAddTo.put(name, value);
	}
}
