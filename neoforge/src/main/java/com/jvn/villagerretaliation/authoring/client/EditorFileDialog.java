package com.jvn.villagerretaliation.authoring.client;

import java.io.File;
import java.util.function.Consumer;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

final class EditorFileDialog {
    private static volatile boolean open;
    private EditorFileDialog() { }

    static void choose(String label, String current, Consumer<String> selected) {
        if (open) return;
        open = true;
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(label);
                chooser.setSelectedFile(new File(current));
                boolean image = label.startsWith("PNG");
                chooser.setFileSelectionMode(image ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
                if (image) chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
                if (chooser.showDialog(null, "Choose") == JFileChooser.APPROVE_OPTION) selected.accept(chooser.getSelectedFile().getAbsolutePath());
            } finally {
                open = false;
            }
        });
    }
}
