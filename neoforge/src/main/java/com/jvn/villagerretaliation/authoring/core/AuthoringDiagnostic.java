package com.jvn.villagerretaliation.authoring.core;

public record AuthoringDiagnostic(Severity severity, String resourcePath, String pointer, String message) {
    public enum Severity { ERROR, WARNING }
}
