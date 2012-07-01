package hudson.plugins.ec2;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Stops the given instances. Optionally terminates.
 * User: Joel Johnson
 * Date: 6/30/12
 * Time: 5:28 PM
 */
public class EndEc2Builder extends Builder {
	private String instances;
	private boolean terminate;

	@DataBoundConstructor
	public EndEc2Builder(String instances, boolean terminate) {
		this.instances = instances == null ? "" : instances;
		this.terminate = terminate;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		EnvVars environment = build.getEnvironment(listener);
		String instancesExpanded = environment.expand(this.instances);
		String[] instances = instancesExpanded.split("\\s+");
		EC2Cloud ec2Cloud = EC2Cloud.get(); //TODO: allow user to select which cloud service to use
		for (String instanceId : instances) {
			EC2Slave.terminate(ec2Cloud, instanceId, !terminate);
		}
		return true;
	}

	public String getInstances() {
		return instances;
	}

	public boolean getTerminate() {
		return terminate;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Stop EC2 Machines";
		}
	}
}
