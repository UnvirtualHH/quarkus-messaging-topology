package de.prgrm.topology.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.messaging-topology")
public interface TopologyConfig {

    /** Whether the messaging topology extension is enabled. */
    @WithDefault("true")
    boolean enabled();

    /** Directory for storing topology files. */
    @WithDefault("/tmp/topology")
    String directory();

    /** Project name for grouping services. */
    Optional<String> projectName();

    /** Service name override. */
    Optional<String> serviceName();

    /** Group ID for multi-service topology discovery. */
    Optional<String> groupId();

    /** Whether to automatically save topology on startup. */
    @WithDefault("true")
    boolean autoSave();

    /** Whether to include schema introspection results. */
    @WithDefault("true")
    boolean includeSchema();

    /** Whether to clean up topology files on shutdown. */
    @WithDefault("true")
    boolean cleanupOnShutdown();
}
