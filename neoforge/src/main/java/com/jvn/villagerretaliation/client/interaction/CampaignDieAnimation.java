package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import java.util.HashSet;
import java.util.Set;

public final class CampaignDieAnimation {
    public static final long DURATION_MILLIS = 1800L;
    private final Set<String> presented = new HashSet<>();
    private String awaiting = "";
    private String current = "";
    private long started;
    private boolean playing;

    public void accept(CampaignConversationViewPayload.Session session, long now, boolean enabled) {
        var check = session == null ? null : session.check();
        if (check == null) { awaiting = ""; current = ""; playing = false; return; }
        String key = session.id() + "/" + check.id();
        if (!check.resolved()) { awaiting = key; current = ""; playing = false; return; }
        if (key.equals(current)) return;
        current = key;
        playing = presented.add(key) && awaiting.equals(key) && enabled;
        started = now;
        awaiting = "";
    }

    public boolean tick(long now) {
        if (!playing || now - started < DURATION_MILLIS) return false;
        playing = false;
        return true;
    }

    public boolean skip() {
        boolean changed = playing;
        playing = false;
        return changed;
    }

    public boolean playing() { return playing; }
    public float progress(long now) { return playing ? Math.clamp((float) (now - started) / DURATION_MILLIS, 0, 1) : 1; }
}
