package org.jboss.sbomer.pnc.generator.core.domain.slsa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlsaBuildDefinition(
    String buildType,
    SlsaExternalParameters externalParameters
) {}