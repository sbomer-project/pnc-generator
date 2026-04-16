package org.jboss.sbomer.pnc.generator.core.port.spi;

import java.util.List;

import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;

/**
 * Outbound port (SPI) for interacting with the Project Newcastle (PNC) system.
 */
public interface PNCService {

    /**
     * Retrieves the metadata for a specific PNC Build.
     * This contains SCM Repository, Environment, and Attributes.
     *
     * @param buildId The unique identifier of the PNC build.
     * @return The Build details.
     */
    Build getBuild(String buildId);

    /**
     * Retrieves the list of artifacts that were built by the specified PNC build.
     *
     * @param buildId The unique identifier of the PNC build.
     * @return A list of Artifacts.
     */
    List<Artifact> getBuiltArtifacts(String buildId);

    /**
     * Retrieves the SLSA Provenance for a specific artifact ID.
     * This provides the dependency graph and built subjects.
     *
     * @param artifactId The unique identifier of an artifact.
     * @return The SLSA Provenance data.
     */
    SlsaProvenance getBuildProvenance(String artifactId);
}