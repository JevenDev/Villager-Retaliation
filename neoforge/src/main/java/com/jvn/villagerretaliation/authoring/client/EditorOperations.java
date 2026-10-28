package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class EditorOperations {
    @FunctionalInterface
    interface Action { void run() throws IOException; }

    private final ArrayDeque<Action> pending = new ArrayDeque<>();
    private final Consumer<Exception> failed;
    private boolean deferred;

    EditorOperations(Consumer<Exception> failed) { this.failed = failed; }
    boolean deferred() { return deferred; }
    void beginFrame() { deferred = true; }
    void endFrame() {
        deferred = false;
        while (!pending.isEmpty()) run(pending.removeFirst());
    }
    void submit(Action action) {
        if (deferred) pending.addLast(action);
        else run(action);
    }
    private void run(Action action) {
        try { action.run(); }
        catch (IOException | IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException
                | ClassCastException | UnsupportedOperationException | IndexOutOfBoundsException exception) { failed.accept(exception); }
    }

    static boolean history(AuthoringDocument document, boolean redo, Predicate<String> pending) {
        if (document == null) throw new IllegalStateException("Select a file before changing history");
        for (var affected : document.historyDocuments(redo)) if (pending.test(affected.resourcePath()))
            throw new IllegalStateException("Apply or discard unfinished edits before changing history: " + affected.resourcePath());
        return redo ? document.redo() : document.undo();
    }
}
