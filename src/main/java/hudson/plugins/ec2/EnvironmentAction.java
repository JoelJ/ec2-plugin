package hudson.plugins.ec2;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;

import java.util.Map;

/**
 * When added to a build, all the values in the map are added to that build's environment
 * User: Joel Johnson
 * Date: 6/30/12
 * Time: 5:42 PM
 */
public class EnvironmentAction implements EnvironmentContributingAction {
	private final Map<String, String> variables;

	public EnvironmentAction(Map<String, String> variables) {
		this.variables = variables;
	}

	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		env.putAll(variables);
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return null;
	}
}
