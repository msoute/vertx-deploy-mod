package nl.jpoint.maven.vertx.utils;

import nl.jpoint.maven.vertx.mojo.DeployConfiguration;
import nl.jpoint.maven.vertx.request.DeployApplicationRequest;
import nl.jpoint.maven.vertx.request.DeployArtifactRequest;
import nl.jpoint.maven.vertx.request.DeployConfigRequest;
import nl.jpoint.maven.vertx.request.Request;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeployUtils {
    public static final String APPLICATION_TYPE = "jar";
    public static final String ARTIFACT_TYPE_ZIP = "zip";
    public static final String ARTIFACT_TYPE_GZIP = "tar.gz";
    public static final String CONFIG_TYPE = "config";

    private final Log log;
    private final MavenProject project;
    private final List<RemoteRepository> remoteRepos;
    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;

    public DeployUtils(Log log, MavenProject project, List<RemoteRepository> remoteRepos, RepositorySystem repoSystem, RepositorySystemSession repoSession) {

        this.log = log;
        this.project = project;
        this.remoteRepos = remoteRepos;
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
    }

    public List<Request> createDeployApplicationList(DeployConfiguration activeConfiguration) throws MojoFailureException {
        return createDeployListByType(activeConfiguration, APPLICATION_TYPE).stream().map(dependency -> new DeployApplicationRequest(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getClassifier(), dependency.getType(), activeConfiguration.doRestart())).collect(Collectors.toList());
    }

    public List<Request> createDeployArtifactList(DeployConfiguration activeConfiguration) throws MojoFailureException {
        List<Request> result = createDeployListByType(activeConfiguration, ARTIFACT_TYPE_ZIP).stream().map(dependency -> new DeployArtifactRequest(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getClassifier(), dependency.getType())).collect(Collectors.toList());
        result.addAll(createDeployListByType(activeConfiguration, ARTIFACT_TYPE_GZIP).stream().map(dependency -> new DeployArtifactRequest(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getClassifier(), dependency.getType())).collect(Collectors.toList()));
        return result;
    }

    public List<Request> createDeployConfigList(DeployConfiguration activeConfiguration) throws MojoFailureException {
        return createDeployListByType(activeConfiguration, CONFIG_TYPE).stream().map(dependency -> new DeployConfigRequest(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getClassifier(), dependency.getType())).collect(Collectors.toList());
    }

    public List<String> parseProperties(String properties) {
        if (StringUtils.isBlank(properties)) {
            return new ArrayList<>();
        }
        return Pattern.compile(";").splitAsStream(properties).collect(Collectors.toList());
    }

    public List<Exclusion> parseExclusions(String exclusions) {
        List<Exclusion> result = new ArrayList<>();
        if (StringUtils.isBlank(exclusions)) {
            return result;
        }

        Pattern.compile(";")
                .splitAsStream(exclusions)
                .forEach(s -> {
                            String[] mavenIds = Pattern.compile(":").split(s, 2);
                            if (mavenIds.length == 2) {
                                Exclusion exclusion = new Exclusion();
                                exclusion.setGroupId(mavenIds[0]);
                                exclusion.setArtifactId(mavenIds[1]);
                                result.add(exclusion);
                            }
                        }
                );
        return result;
    }

    private List<Dependency> createDeployListByType(DeployConfiguration activeConfiguration, String type) throws MojoFailureException {
        List<Dependency> deployModuleDependencies = new ArrayList<>();

        List<Dependency> dependencies = project.getDependencies();

        Iterator<Dependency> it = dependencies.iterator();

        filterTestArtifacts(activeConfiguration, it);

        for (Dependency dependency : dependencies) {
            if ((dependency.getVersion().endsWith("-SNAPSHOT") || hasTransitiveSnapshots(dependency)) && !activeConfiguration.isDeploySnapshots()) {
                throw new MojoFailureException("Target does not allow for snapshots to be deployed");
            }

            if (type.equals(dependency.getType()) && !excluded(activeConfiguration, dependency)) {
                deployModuleDependencies.add(dependency);
            }

        }
        return deployModuleDependencies;
    }

    private boolean hasTransitiveSnapshots(Dependency dependency) throws MojoFailureException {
        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(
                new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion()));
        descriptorRequest.setRepositories(remoteRepos);

        try {
            ArtifactDescriptorResult result = repoSystem.readArtifactDescriptor(repoSession, descriptorRequest);
            Optional<org.eclipse.aether.graph.Dependency> snapshotDependency = result.getDependencies().stream()
                    .filter(d -> d.getArtifact().isSnapshot())
                    .findFirst();
            return snapshotDependency.isPresent();
        } catch (ArtifactDescriptorException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private boolean excluded(DeployConfiguration activeConfiguration, Dependency dependency) {
        if (activeConfiguration.getExclusions() == null) {
            return false;
        }
        for (Exclusion exclusion : activeConfiguration.getExclusions()) {
            if (exclusion.getArtifactId().equals(dependency.getArtifactId()) &&
                    exclusion.getGroupId().equals(dependency.getGroupId())) {
                log.info("Excluding dependency " + dependency.getArtifactId());
                return true;
            }
        }
        return false;
    }

    private void filterTestArtifacts(DeployConfiguration activeConfiguration, Iterator<Dependency> it) {
        if (!activeConfiguration.isTestScope()) {
            while (it.hasNext()) {
                Dependency dependency = it.next();
                if (Artifact.SCOPE_TEST.equals(dependency.getScope())) {
                    log.info("Excluding artifact " + dependency.getArtifactId() + " from scope " + dependency.getScope());
                    it.remove();
                }
            }
        }
    }
}
