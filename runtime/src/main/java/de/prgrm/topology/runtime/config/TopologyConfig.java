// runtime/src/main/java/de/prgrm/topology/runtime/config/TopologyConfig.java
package de.prgrm.topology.runtime.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.messaging-topology")
public interface TopologyConfig {

    /**
     * Enable or disable the messaging topology feature
     */
    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    /**
     * Directory where topology files are stored
     */
    @WithName("directory")
    @WithDefault("/tmp/topology")
    String directory();

    /**
     * Project name for grouping related services
     */
    @WithName("project-name")
    Optional<String> projectName();

    /**
     * Override the service name (defaults to application name)
     */
    @WithName("service-name")
    Optional<String> serviceName();

    /**
     * Group ID for the service (defaults to base package)
     */
    @WithName("group-id")
    Optional<String> groupId();

    /**
     * Automatically save topology on startup
     */
    @WithName("auto-save")
    @WithDefault("true")
    boolean autoSave();

    /**
     * Include schema information in topology
     */
    @WithName("include-schema")
    @WithDefault("true")
    boolean includeSchema();
}
