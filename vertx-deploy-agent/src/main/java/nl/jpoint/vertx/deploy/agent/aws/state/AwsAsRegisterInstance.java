package nl.jpoint.vertx.deploy.agent.aws.state;

import io.vertx.core.Vertx;
import nl.jpoint.vertx.deploy.agent.DeployConfig;
import nl.jpoint.vertx.deploy.agent.aws.AwsAutoScalingUtil;
import nl.jpoint.vertx.deploy.agent.aws.AwsState;
import nl.jpoint.vertx.deploy.agent.command.Command;
import nl.jpoint.vertx.deploy.agent.request.DeployRequest;
import nl.jpoint.vertx.deploy.agent.util.LogConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.time.LocalDateTime;


public class AwsAsRegisterInstance implements Command<DeployRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(AwsElbRegisterInstance.class);
    private final AwsAutoScalingUtil awsAsUtil;
    private final AwsPollingAsStateObservable poller;

    public AwsAsRegisterInstance(final Vertx vertx, final DeployConfig config, final Integer maxDuration) {
        this.awsAsUtil = new AwsAutoScalingUtil(config);
        this.poller = new AwsPollingAsStateObservable(vertx, awsAsUtil, LocalDateTime.now().plusMinutes(maxDuration),
                config.getPollIntervall(),
                AwsState.INSERVICE);
    }

    @Override
    public Observable<DeployRequest> executeAsync(DeployRequest request) {
        if (!awsAsUtil.exitStandby(request.getAutoScalingGroup())) {
            LOG.error("[{} - {}]: InstanceId {} failed to exit standby in auto scaling group {}", LogConstants.AWS_AS_REQUEST, request.getId(), awsAsUtil.getInstanceId(), request.getAutoScalingGroup());
            throw new IllegalStateException();
        }

        LOG.info("[{} - {}]: Waiting for instance {} status in auto scaling group {} to reach {}.", LogConstants.AWS_AS_REQUEST, request.getId(), awsAsUtil.getInstanceId(), request.getAutoScalingGroup(), AwsState.INSERVICE);
        LOG.info("[{} - {}]: Starting instance status poller for instance id {} in auto scaling group {}", LogConstants.AWS_AS_REQUEST, request.getId(), awsAsUtil.getInstanceId(), request.getAutoScalingGroup());

        return poller.poll(request);
    }
}
