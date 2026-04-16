package org.jboss.sbomer.pnc.generator.adapter.out.pnc;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.sbomer.pnc.generator.core.domain.slsa.SlsaProvenance;
import org.jboss.sbomer.pnc.generator.core.port.spi.PNCService;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class PncServiceAdapter implements PNCService {

    @ConfigProperty(name = "pnc.api.url")
    String pncApiUrl;

    @Inject
    @RestClient
    PncRestApiClient pncRestClient;

    private BuildClient buildClient;

    @PostConstruct
    void init() {
        log.info("Initializing official PNC Clients with URL: {}", pncApiUrl);
        Configuration config = Configuration.builder().host(pncApiUrl).protocol("http").build();
        this.buildClient = new BuildClient(config);
    }

    @PreDestroy
    void cleanup() {
        if (buildClient != null) buildClient.close();
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
    public Build getBuild(@SpanAttribute("pnc.buildId") String buildId) {
        log.debug("Fetching Build '{}'", buildId);
        try {
            return buildClient.getSpecific(buildId);
        } catch (RemoteResourceNotFoundException e) {
            log.warn("Build with id '{}' was not found", buildId);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve build from PNC", e);
        }
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
    public List<Artifact> getBuiltArtifacts(@SpanAttribute("pnc.buildId") String buildId) {
        log.debug("Fetching Built Artifacts for build '{}'", buildId);
        try {
            var remoteCollection = buildClient.getBuiltArtifacts(buildId);
            return new ArrayList<>(remoteCollection.getAll());
        } catch (RemoteResourceNotFoundException e) {
            throw new RuntimeException("Built artifacts not found for build " + buildId, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve built artifacts from PNC", e);
        }
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
    public SlsaProvenance getBuildProvenance(@SpanAttribute("pnc.artifactId") String artifactId) {
        log.debug("Fetching SLSA Provenance for artifact '{}'", artifactId);
        try {
            // This endpoint utilizes the PNC REST API rather than the PNC Client library since
            // this is a new and actively developed feature being implemented that might not yet
            // be exposed in client
            // TODO check if PNC Client support is ready to be used and move to that
            return pncRestClient.getBuildProvenanceFallback(artifactId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve SLSA provenance for artifact " + artifactId, e);
        }
    }
}