package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview;
import com.jvn.villagerretaliation.dialogue.DialogueCue;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import java.util.Locale;

final class EditorDialogueTimeline {
    private EditorDialogueTimeline() { }

    static void window() {
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX() + Math.max(16, viewport.getWorkSizeX() - 440),
                viewport.getWorkPosY() + 20, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(420, 300, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Dialogue timeline###VR_DialogueTimeline")) render();
        ImGui.end();
    }

    static void render() {
        var timeline = AuthoringDialoguePreview.timeline();
        if (timeline == null) return;
        if (ImGui.button(timeline.playing() ? "Pause line" : "Play line")) {
            if (timeline.playing()) AuthoringDialoguePreview.pause();
            else AuthoringDialoguePreview.play();
        }
        EditorWidgets.sameLineFor("Replay line");
        if (ImGui.button("Replay line")) AuthoringDialoguePreview.replay();
        float[] time = {timeline.position() / 1000.0F};
        ImGui.setNextItemWidth(-1);
        if (ImGui.sliderFloat(EditorWidgets.field("Line time"), time, 0, Math.max(0.001F, timeline.duration() / 1000.0F), "%.2f s"))
            AuthoringDialoguePreview.seek(Math.round(time[0] * 1000));
        ImGui.textUnformatted(String.format(Locale.ROOT, "%.2f / %.2f s | %d characters",
                timeline.position() / 1000.0, timeline.duration() / 1000.0, timeline.visibleCharacters()));
        ImGui.textWrapped("Scrubbing is silent. Play or replay to hear inline sounds.");
        if (timeline.cues().isEmpty()) {
            ImGui.textDisabled("This line has no cues.");
            return;
        }
        if (ImGui.beginChild("##line_cues", 0, 140)) {
            int index = 0;
            for (var cue : timeline.cues()) {
                String label = String.format(Locale.ROOT, "%.2f s  %s##cue%d", cue.time() / 1000.0, label(cue.cue()), index++);
                if (ImGui.selectable(label, Math.abs(timeline.position() - cue.time()) < 35))
                    AuthoringDialoguePreview.seek((long) Math.ceil(cue.time()));
                if (ImGui.isItemHovered()) ImGui.setTooltip(label(cue.cue()));
            }
        }
        ImGui.endChild();
    }

    static String label(DialogueCue cue) {
        return switch (cue.kind()) {
            case PARTICLE -> "Particle " + cue.particle() + " x" + (int) cue.value();
            case SOUND -> "Sound " + cue.sound().id() + " | volume " + cue.sound().volume() + " | pitch " + cue.sound().pitch();
            case PAUSE -> "Pause " + (int) cue.value() + " ms";
            case LOOK_TARGET -> "Look at " + cue.target();
            case PUSH_POSE -> "Save pose";
            case POP_POSE -> "Restore pose";
            case SPEED -> "Text speed x" + cue.value();
            default -> cue.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }
}
