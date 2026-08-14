package com.jvn.villagerretaliation.util;

public enum ContentTagDomain {
    CLASSIFICATION("tag"),
    ROUTING("route"),
    ANTI_REPEAT("repeat");

    private final String namespace;

    ContentTagDomain(String namespace) {
        this.namespace = namespace;
    }

    public String namespace() {
        return this.namespace;
    }
}
