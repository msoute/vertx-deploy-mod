package nl.jpoint.maven.vertx.utils.deploy.strategy;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import nl.jpoint.maven.vertx.mojo.DeployConfiguration;

public class KeepCapacityStrategy implements DeployStrategy {
    @Override
    public boolean calculate(DeployConfiguration activeConfiguration, AutoScalingGroup autoScalingGroup, long inService, long healthy, long inStandby) {
        if (activeConfiguration.useElbStatusCheck()) {
            return inService > autoScalingGroup.getDesiredCapacity()-1;
        } else {
            return healthy > autoScalingGroup.getDesiredCapacity()-1;
        }

    }
}
