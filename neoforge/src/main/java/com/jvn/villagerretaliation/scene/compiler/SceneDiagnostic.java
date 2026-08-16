package com.jvn.villagerretaliation.scene.compiler;

import net.minecraft.resources.ResourceLocation;

public record SceneDiagnostic(Severity severity, String code, ResourceLocation resourceId, String path,
                              String message, String correction) {
    public SceneDiagnostic(Severity severity, String code, String path, String message) {
        this(severity, code, null, path, message, "Correct the value at the reported JSON path and reload datapacks.");
    }

    public SceneDiagnostic {
        severity = severity == null ? Severity.ERROR : severity;
        code = code == null ? "scene.invalid" : code;
        path = path == null ? "" : path;
        message = message == null ? "" : message;
        correction = correction == null ? "" : correction;
    }

    public SceneDiagnostic atSource(ResourceLocation source) {
        return resourceId == null && source != null
                ? new SceneDiagnostic(severity, code, source, path, message, correction)
                : this;
    }

    public enum Severity { WARNING, ERROR }
}
