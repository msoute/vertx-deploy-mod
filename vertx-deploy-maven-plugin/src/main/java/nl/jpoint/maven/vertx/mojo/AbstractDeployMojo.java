package nl.jpoint.maven.vertx.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

import java.util.List;

abstract class AbstractDeployMojo extends AbstractMojo {


    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;
    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    Settings settings;
    private List<DeployConfiguration> deployConfigurations;
    @Parameter(defaultValue = "default", property = "deploy.activeTarget")
    private String activeTarget;
    @Parameter(defaultValue = "10", property = "deploy.requestTimeout")
    protected Integer requestTimeout;
    @Parameter(defaultValue = "6789")
    protected Integer port;
    @Parameter(defaultValue = "eu-west-1")
    protected String region;
    @Parameter(required = false, defaultValue = "", property = "deploy.exclusions")
    protected String exclusions;

    DeployConfiguration activeConfiguration;


    DeployConfiguration setActiveDeployConfig() throws MojoFailureException {

        if (deployConfigurations.size() == 1) {
            getLog().info("Found exactly one deploy config to activate.");
            activeConfiguration = deployConfigurations.get(0);
        } else {
            for (DeployConfiguration config : deployConfigurations) {
                if (activeTarget.equals(config.getTarget())) {
                    activeConfiguration = config;
                    break;
                }
            }
        }

        if (activeConfiguration == null) {
            getLog().error("No active deployConfig !");
            throw new MojoFailureException("No active deployConfig !, config should contain at least one config with scope default");
        }


        getLog().info("Deploy config with target " + activeConfiguration.getTarget() + " activated");

        activeConfiguration.withProjectVersion(projectVersionAsString());

        return activeConfiguration;
    }

    String projectVersionAsString() {
        return project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getPackaging() + ":" + project.getVersion();
    }
}
