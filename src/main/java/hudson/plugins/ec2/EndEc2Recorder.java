package hudson.plugins.ec2;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * User: Joel Johnson
 * Date: 6/30/12
 * Time: 10:59 PM
 */
public class EndEc2Recorder extends Recorder {
	private String instances;
	private boolean terminate;

	@DataBoundConstructor
	public EndEc2Recorder(String instances, boolean terminate) {
		this.instances = instances == null ? "" : instances;
		this.terminate = terminate;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		EndEc2Builder.tearDownInstances(instances, terminate, build, listener);
		return true;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getInstances() {
		return instances;
	}

	public boolean getTerminate() {
		return terminate;
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Tear down EC2 Instances";
		}
	}
}
