package com.jvn.villagerretaliation.authoring.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AuthoringPackBuild {
    private AuthoringPackBuild() { }

    public static Path build(AuthoringWorkspace workspace, Path destination, boolean zip) throws IOException {
        if (!zip) return ManagedPackExport.export(workspace, destination);
        Path target = destination.toAbsolutePath().normalize();
        if (target.getParent() == null || target.getFileName() == null) throw new IOException("Choose a ZIP file inside an output folder");
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) throw new IOException("Choose a ZIP filename ending in .zip");
        for (Path path = target; path != null; path = path.getParent()) if (Files.isSymbolicLink(path)) throw new IOException("Build cannot traverse symbolic links");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("This ZIP already exists. Choose a new filename to preserve it");
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), ".vr-package-");
        try {
            Path pack = staging.resolve("pack");
            workspace.exportPack(pack);
            Path archive = staging.resolve("pack.zip");
            try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
                try (var files = Files.walk(pack)) {
                    for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                        var entry = new ZipEntry(pack.relativize(file).toString().replace('\\', '/'));
                        entry.setTime(0);
                        output.putNextEntry(entry);
                        Files.copy(file, output);
                        output.closeEntry();
                    }
                }
            }
            Files.move(archive, target);
            return null;
        } finally {
            try (var files = Files.walk(staging)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
