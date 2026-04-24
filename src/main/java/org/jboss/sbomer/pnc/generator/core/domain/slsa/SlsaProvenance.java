package org.jboss.sbomer.pnc.generator.core.domain.slsa;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlsaProvenance(
        String predicateType,
        SlsaPredicate predicate,
        List<SlsaSubject> subject
) {}
