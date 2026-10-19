package com.jvn.villagerretaliation.authoring.preview;

import java.util.Set;

public record PreviewCoverage(Set<String> nodes, Set<String> choices) {
    public PreviewCoverage { nodes = Set.copyOf(nodes); choices = Set.copyOf(choices); }
}
