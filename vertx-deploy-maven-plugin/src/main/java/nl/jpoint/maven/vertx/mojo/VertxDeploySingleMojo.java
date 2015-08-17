package nl.jpoint.maven.vertx.mojo;

import nl.jpoint.maven.vertx.request.DeployArtifactRequest;
import nl.jpoint.maven.vertx.request.DeployConfigRequest;
import nl.jpoint.maven.vertx.request.DeployModuleRequest;
import nl.jpoint.maven.vertx.request.Request;
import nl.jpoint.maven.vertx.service.AutoScalingDeployService;
import nl.jpoint.maven.vertx.service.DefaultDeployService;
import nl.jpoint.maven.vertx.service.OpsWorksDeployService;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collections;
import java.util.List;

@Mojo(name = "deploy-single")
class VertxDeploySingleMojo extends AbstractDeployMojo {

    @Parameter(property = "deploy.single.type", required = true)
    private String type;
    @Parameter(property = "deploy.single.groupId", required = true)
    private String groupId;
    @Parameter(property = "deploy.single.artifactId", required = true)
    private String artifactId;
    @Parameter(property = "deploy.single.classifier", required = false)
    private String classifier;
    @Parameter(property = "deploy.single.version", required = true)
    private String version;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        setActiveDeployConfig();

        if (activeConfiguration.useAutoScaling() && activeConfiguration.useOpsWorks()) {
            throw new MojoFailureException("ActiveConfiguration " + activeConfiguration.getTarget() + " has both OpsWorks and Autoscaling enabled");
        }

        final List<Request> deployModuleRequests = MODULE_CLASSIFIER.equals(type) ? createModuleRequest() : Collections.emptyList();
        final List<Request> deployArtifactRequests = SITE_CLASSIFIER.equals(type) ? createArtifactRequest() : Collections.emptyList();
        final List<Request> deployConfigRequests = CONFIG_TYPE.equals(type) ? createConfigRequest() : Collections.emptyList();

        getLog().info("Constructed deploy request with '" + deployConfigRequests.size() + "' configs, '" + deployArtifactRequests.size() + "' artifacts and '" + deployModuleRequests.size() + "' modules");
        getLog().info("Executing deploy request, waiting for Vert.x to respond.... (this might take some time)");

        if (activeConfiguration.useAutoScaling()) {
            AutoScalingDeployService service = new AutoScalingDeployService(activeConfiguration, region, port, requestTimeout, getServer(), getLog());
            service.deployWithAutoScaling(deployModuleRequests, deployArtifactRequests, deployConfigRequests);
        } else if (activeConfiguration.useOpsWorks()) {
            OpsWorksDeployService service = new OpsWorksDeployService(activeConfiguration, region, port, requestTimeout, getServer(), getLog());
            service.deployWithOpsWorks(deployModuleRequests, deployArtifactRequests, deployConfigRequests);
        } else {
            DefaultDeployService service = new DefaultDeployService(activeConfiguration, port, requestTimeout, getLog());
            service.normalDeploy(deployModuleRequests, deployArtifactRequests, deployConfigRequests);
        }
    }

    private List<Request> createModuleRequest() {
        return Collections.singletonList(new DeployModuleRequest(groupId, artifactId, version, type, 1, activeConfiguration.doRestart()));
    }

    private List<Request> createArtifactRequest() {
        return Collections.singletonList(new DeployArtifactRequest(groupId, artifactId, version, classifier, type));
    }

    private List<Request> createConfigRequest() {
        return Collections.singletonList(new DeployConfigRequest(groupId, artifactId, version, classifier, type));
    }
}