package org.jboss.sbomer.pnc.generator.core.domain.slsa;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlsaSubject(
    String name,
    Map<String, String> digest,
    SlsaAnnotations annotations
) {}
