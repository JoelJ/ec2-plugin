package hudson.plugins.ec2;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.plugins.ec2.ssh.EC2UnixLauncher;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: Joel Johnson
 * Date: 6/30/12
 * Time: 5:28 PM
 */
public class StartEc2Builder extends Builder {
	private List<SlaveTemplate> templates;

	@DataBoundConstructor
	public StartEc2Builder(List<SlaveTemplate> templates) {
		this.templates = templates == null ? Collections.<SlaveTemplate>emptyList() : templates;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		List<EC2Slave> newMachines = new ArrayList<EC2Slave>(templates.size());
		PrintStream logger = listener.getLogger();
		try {
			for (SlaveTemplate template : templates) {
				template.parent = EC2Cloud.get(); //TODO: allow user to select which cloud service to use
				EC2Slave newMachine = template.provision(listener);
				newMachines.add(newMachine);

				logger.println("Created machine " + newMachine.getInstanceId());
			}
		} catch(Exception e) {
			listener.error("Failed to start a machine. Attempting to terminate.");
			e.printStackTrace(logger);

			silentlyTerminate(newMachines, listener);
			return false;
		}

		waitForAllMachinesAddress(newMachines, logger);
		waitForAllMachinesSsh(newMachines, logger);

		logger.println("Adding variables to the environment");
		build.addAction(new Ec2MachineVariables(newMachines, listener.getLogger()));

		return true;
	}

	private void waitForAllMachinesAddress(List<EC2Slave> newMachines, PrintStream logger) throws InterruptedException {
		logger.println("Waiting for all machines to acquire an address.");
		int timeWaited = 0;
		for (EC2Slave newMachine : newMachines) {
			while(timeWaited < 10 * 60 * 1000) { //10 minutes TODO: make this user defined
				if(newMachine.getPublicDNS() == null || newMachine.getPublicDNS().isEmpty()) {
					timeWaited += waiting(logger);
					continue;
				}

				logger.println("Machine acquired address: " + newMachine.getInstanceId());
				logger.println("\t- public dns: " + newMachine.getPublicDNS());
				logger.println("\t- private dns: " + newMachine.getPrivateDNS());
				break;
			}
		}
	}

	private void waitForAllMachinesSsh(List<EC2Slave> newMachines, PrintStream logger) throws InterruptedException {
		logger.println("Waiting for SSH to come up on all machines.");
		int timeWaited = 0;
		for (EC2Slave newMachine : newMachines) {
			while(timeWaited < 10 * 60 * 1000) { //10 minutes TODO: make this user defined
				try {
					EC2UnixLauncher.testConnection(logger, newMachine.getPublicDNS(), newMachine.getSshPort());
					logger.println(newMachine.getInstanceId() + " is up and ready for action: " + newMachine.getPublicDNS() + ":" + newMachine.getSshPort());
					break;
				} catch (IOException e) {
					timeWaited += waiting(logger);
				}
			}
		}
	}

	private int waiting(PrintStream logger) throws InterruptedException {
		logger.println("Waiting for SSH to come up. Sleeping 5.");
		Thread.sleep(5000);
		return 5000;
	}

	private void silentlyTerminate(List<EC2Slave> newMachines, BuildListener listener) {
		for (EC2Slave newMachine : newMachines) {
			listener.error("terminating " + newMachine.getInstanceId());
			newMachine.terminate();
		}
	}

	public List<SlaveTemplate> getTemplates() {
		return templates;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Create EC2 Machines";
		}
	}
}
