package org.jboss.sbomer.pnc.generator.core.domain.slsa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlsaAnnotations(
    String identifier,
    String purl,
    String uri,
    String artifactId, 
    String buildId     
) {}