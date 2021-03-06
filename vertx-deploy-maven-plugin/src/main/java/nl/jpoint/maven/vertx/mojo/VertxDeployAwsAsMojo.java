package nl.jpoint.maven.vertx.mojo;

import nl.jpoint.maven.vertx.request.Request;
import nl.jpoint.maven.vertx.service.AutoScalingDeployService;
import nl.jpoint.maven.vertx.utils.DeployType;
import nl.jpoint.maven.vertx.utils.DeployUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.List;

@SuppressWarnings("unused")
@Mojo(name = "deploy-single-as", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class VertxDeployAwsAsMojo extends AbstractDeployMojo {

    @Parameter(required = true, property = "deploy.as.id")
    private String autoScalingGroup;
    @Parameter(required = true, property = "deploy.as.strategy")
    private String strategy;
    @Parameter(defaultValue = "1", property = "deploy.as.max")
    private Integer maxGroupSize;
    @Parameter(defaultValue = "0", property = "deploy.as.min")
    private Integer minGroupSize;
    @Parameter(defaultValue = "false", property = "deploy.as.elb")
    private boolean useElb;
    @Parameter(defaultValue = "true", property = "deploy.as.private")
    private boolean usePrivateIp;
    @Parameter(defaultValue = "false", property = "deploy.as.test")
    private boolean isTestScope;
    @Parameter(defaultValue = "true", property = "deploy.as.config")
    private boolean deployConfig;
    @Parameter(defaultValue = "true", property = "deploy.as.restart")
    private boolean doRestart;
    @Parameter(defaultValue = "true", property = "deploy.as.decrement")
    private boolean decrementCapacity;
    @Parameter(defaultValue = "true", property = "deploy.as.ignoreInStandby")
    private boolean ignoreInStandby;
    @Parameter(defaultValue = "false", property = "deploy.as.allowSnapshots")
    private boolean deploySnapshots;
    @Parameter(defaultValue = "false", property = "deploy.as.stickiness")
    private boolean enableStickiness;
    @Parameter(property = "deploy.as.properties")
    private String properties;
    @Parameter(property = "deploy.auth.token")
    private String authToken;
    @Parameter(defaultValue = "true", property = "deploy.as.spindown")
    private boolean spindown;
    @Parameter(defaultValue = "default", property = "deploy.type")
    private String deployType;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final DeployUtils utils = new DeployUtils(getLog(), project, remoteRepos, repoSystem, repoSession);

        activeConfiguration = this.createConfiguration();
        activeConfiguration.getExclusions().addAll(utils.parseExclusions(exclusions));
        activeConfiguration.getAutoScalingProperties().addAll(utils.parseProperties(properties));
        final List<Request> deployModuleRequests = utils.createDeployApplicationList(activeConfiguration);
        final List<Request> deployArtifactRequests = utils.createDeployArtifactList(activeConfiguration);
        final List<Request> deployConfigRequests = utils.createDeployConfigList(activeConfiguration);

        getLog().info("Constructed deploy request with '" + deployConfigRequests.size() + "' configs, '" + deployArtifactRequests.size() + "' artifacts and '" + deployModuleRequests.size() + "' modules");
        getLog().info("Executing deploy request, waiting for Vert.x to respond.... (this might take some time)");

        AutoScalingDeployService service = new AutoScalingDeployService(activeConfiguration, region, port, requestTimeout, getLog(), project.getProperties());
        service.deploy(deployModuleRequests, deployArtifactRequests, deployConfigRequests);

    }

    private DeployConfiguration createConfiguration() {
        return new DeployConfiguration()
                .withAutoScalingGroup(autoScalingGroup)
                .withStrategy(strategy)
                .withMaxGroupSize(maxGroupSize)
                .withMinGroupSize(minGroupSize)
                .withElb(useElb)
                .withPrivateIp(usePrivateIp)
                .withTestScope(isTestScope)
                .withConfig(deployConfig)
                .withRestart(doRestart)
                .withDecrementCapacity(decrementCapacity)
                .withIgnoreInStandby(ignoreInStandby)
                .withDeploySnapshots(deploySnapshots)
                .withAuthToken(authToken)
                .withStickiness(useElb && enableStickiness)
                .withPort(port)
                .withSpinDown(spindown)
                .withMetricsConfiguration(MetricsConfiguration.buildMetricsConfiguration(metricNamespace, metricApplication, metricEnvironment))
                .withProjectVersion(projectVersionAsString())
                .withDeployType(DeployType.valueOf(deployType.toUpperCase()));
    }
}
