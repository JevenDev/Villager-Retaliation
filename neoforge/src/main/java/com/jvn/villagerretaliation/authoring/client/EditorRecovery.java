package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

final class EditorRecovery {
    static void write(AuthoringWorkspace workspace, JsonObject buffers, boolean pending) throws IOException {
        workspace.prepareRecovery(buffers, workspace.dirty() || pending).write();
    }

    private record Request(AuthoringWorkspace workspace, long revision, JsonObject buffers, boolean keep) { }
    private record Pending(Request request, CompletableFuture<Void> future) { }
    private final EditorSession sessions;
    private final Executor executor;
    private final ArrayList<Pending> pending = new ArrayList<>();
    private Request latest;
    private IOException sessionFailure;

    EditorRecovery(Path sessions, Executor executor) { this.sessions = new EditorSession(sessions); this.executor = executor; }
    JsonObject session(AuthoringWorkspace workspace) {
        try { return sessions.read(workspace); }
        catch (IOException exception) { sessionFailure = exception; return new JsonObject(); }
    }
    void saveSession(AuthoringWorkspace workspace, JsonObject session) throws IOException { sessions.save(workspace, session); }

    void write(AuthoringWorkspace workspace, JsonObject buffers, boolean unfinished, JsonObject session, boolean wait) throws IOException {
        IOException failure = null;
        try { poll(); } catch (IOException exception) { failure = exception; }
        try {
            boolean keep = workspace.dirty() || unfinished;
            long revision = workspace.revision();
            if (latest == null || latest.workspace() != workspace || latest.revision() != revision
                    || latest.keep() != keep || !latest.buffers().equals(buffers)) {
                var write = workspace.prepareRecovery(buffers, keep);
                var request = new Request(workspace, revision, buffers.deepCopy(), keep);
                var future = CompletableFuture.runAsync(() -> {
                    try { write.write(); } catch (IOException exception) { throw new CompletionException(exception); }
                }, executor);
                latest = request;
                pending.add(new Pending(request, future));
            }
            if (wait) finish(true);
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        try { saveSession(workspace, session); }
        catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    void poll() throws IOException {
        IOException failure = sessionFailure;
        sessionFailure = null;
        try { finish(false); }
        catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    private void finish(boolean wait) throws IOException {
        IOException failure = null;
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (!wait && !item.future().isDone()) continue;
            try { item.future().join(); }
            catch (CompletionException exception) {
                if (latest == item.request()) latest = null;
                var error = new IOException("Could not save editor recovery", exception.getCause());
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            iterator.remove();
        }
        if (failure != null) throw failure;
    }
}
