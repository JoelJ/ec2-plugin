package hudson.plugins.ec2;

import com.trilead.ssh2.Connection;
import hudson.EnvVars;
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
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Map<EC2Slave, SlaveTemplate> newMachines = new LinkedHashMap<EC2Slave, SlaveTemplate>();
        PrintStream logger = listener.getLogger();
        try {
            int cloudNumber = 0;
            for (SlaveTemplate template : templates) {
                int count = getCountFromTags(template.getTags(), logger);
                for (int i = 0; i < count; i++) {
                    template.parent = EC2Cloud.get(); //TODO: allow user to select which cloud service to use
                    EnvVars envVars = build.getEnvironment(listener);
                    envVars.put("CLOUD_NUMBER", "" + (cloudNumber++)); //Allow the user to use this in their tags
                    EC2Slave newMachine = template.provision(envVars, listener);
                    newMachines.put(newMachine, template);

                    logger.println("Created machine " + newMachine.getInstanceId());
                }
            }
        } catch (Exception e) {
            //TODO: If we get throttled on a request minute, sleep and then retry. I don't know what the limit is or what exception is thrown
            listener.error("Failed to start a machine. Attempting to terminate.");
            e.printStackTrace(logger);

            silentlyTerminate(newMachines.keySet(), listener);
            return false;
        }

        waitForAllMachinesAddress(newMachines.keySet(), logger);

        logger.println("Adding variables to the environment");
        build.addAction(new Ec2MachineVariables(newMachines.keySet(), listener.getLogger()));

        waitForAllMachinesSsh(newMachines.keySet(), logger);
        executeInitScripts(newMachines.keySet(), build.getEnvironment(listener), logger);
        createClientConnections(newMachines, build.getEnvironment(listener), logger);

        return true;
    }

    private void createClientConnections(Map<EC2Slave, SlaveTemplate> newMachines, EnvVars environment, PrintStream logger) {

        for (Map.Entry<EC2Slave, SlaveTemplate> newMachine : newMachines.entrySet()) {
            String privateDnsVar = newMachine.getValue().privateDns;
            if (privateDnsVar != null) {
                String privateDns = environment.expand(privateDnsVar);
                if(!privateDns.isEmpty() && !privateDnsVar.equals(privateDns)) {
                    PrintWriter pw = null;
                    Socket socket = null;
                    try {
                        String publicDNS = newMachine.getKey().publicDNS;
                        logger.println("Connecting to " + publicDNS);
                        socket = new Socket(publicDNS, 40000);
                        pw = new PrintWriter(socket.getOutputStream());
                        pw.println(privateDns);
                        pw.flush();
                        pw.close();
                        socket.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        if (pw != null) {
                            pw.flush();
                            pw.close();
                        }
                    }
                } else {
                    logger.printf("privateDns was '%s'%n", privateDns);
                }

            }
        }
    }

    private void executeInitScripts(Set<EC2Slave> newMachines, EnvVars var, PrintStream logger) {
        for (EC2Slave newMachine : newMachines) {
            String initScriptWithVars = newMachine.initScript;
            String initScript = var.expand(initScriptWithVars);
            Connection connection = null;
            try {
                if(initScript != null && !initScript.isEmpty()) {
                    connection = EC2UnixLauncher.getConnection(newMachine, logger);
                    EC2UnixLauncher.executeInitScript(connection, newMachine, initScript, logger);
                }
            } catch (InterruptedException e) {
                e.printStackTrace(logger);
            } catch (IOException e) {
                e.printStackTrace(logger);
            }
            finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }
    }

    private void waitForAllMachinesAddress(Set<EC2Slave> newMachines, PrintStream logger) throws InterruptedException {
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

	/**
	 * Allow the user to define how many machines to create of each instance by using the Tags.
	 * @param tags
	 * @param logger
	 * @return Always 1 or more.
	 */
	private int getCountFromTags(Iterable<EC2Tag> tags, PrintStream logger) {
		int count = 1;
		for (EC2Tag tag : tags) {
			if("count".equalsIgnoreCase(tag.getName())) {
				try {
					String value = tag.getValue();
					logger.println("Found '" + tag.getName() + "' going to make " + value + " instances of image.");
					int temp = Integer.parseInt(value);
					if(temp > 1) {
						count = temp;
					}
				} catch (java.lang.NumberFormatException ignore) {
					//count isn't a valid number!
					logger.println("'" + tag.getName() + "' must be a valid integer to use it to define how many instances to create.");
				}
			}
		}
		return count <= 0 ? 1 : count; //just double check that we're not returning an value less than 1
	}

	private void waitForAllMachinesSsh(Set<EC2Slave> newMachines, PrintStream logger) throws InterruptedException {
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
		logger.println("Waiting for machine to come up. Sleeping 5.");
		Thread.sleep(5000);
		return 5000;
	}

	private void silentlyTerminate(Set<EC2Slave> newMachines, BuildListener listener) {
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
