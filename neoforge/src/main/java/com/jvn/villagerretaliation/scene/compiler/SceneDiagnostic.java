package com.jvn.villagerretaliation.scene.compiler;

public record SceneDiagnostic(Severity severity, String code, String path, String message) {
    public SceneDiagnostic {
        severity = severity == null ? Severity.ERROR : severity;
        code = code == null ? "scene.invalid" : code;
        path = path == null ? "" : path;
        message = message == null ? "" : message;
    }

    public enum Severity { WARNING, ERROR }
}
