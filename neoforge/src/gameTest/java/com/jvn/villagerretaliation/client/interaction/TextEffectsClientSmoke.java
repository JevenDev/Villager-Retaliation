package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanTextRenderer;
import com.jvn.toucanlib.client.ToucanHudText;
import com.jvn.toucanlib.client.ToucanWorldTextIndicators;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class TextEffectsClientSmoke {
    private static final String OUTPUT = "villagerretaliation.textEffectsClientSmoke";
    private static final List<String> TAGS = List.of("italic", "bold", "underline", "strikethrough", "obfuscated",
            "wave", "shake", "pulse", "jump", "rainbow", "aqua", "gradient:#ff0000:#0000ff");
    private static long started;
    private static long shown;
    private static int phase;
    private static int frames;
    private static String headMode = "";
    private static String headText;

    private TextEffectsClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || phase == 99) return;
        var minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        if (started == 0) started = now;
        require(now - started < 120_000, "text effects timed out at phase " + phase);
        if (phase == 0 && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.set(false);
            verifyMetadata();
            minecraft.setScreen(new EffectScreen());
            shown = now;
            phase = 1;
        } else if (phase == 1 && now - shown > 700) {
            capture("effects-first.png");
            minecraft.gui.getChat().scrollChat(5);
            shown = now;
            phase = 2;
        } else if (phase == 2 && now - shown > 700) {
            capture("effects-later.png");
            VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.set(true);
            shown = now;
            phase = 3;
        } else if (phase == 3 && now - shown > 500) {
            capture("effects-disabled.png");
            VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.set(false);
            if (ModList.get().isLoaded("chat_heads")) {
                prepareHeads("BEFORE_NAME");
                shown = now;
                phase = 4;
            } else finish();
        } else if (phase == 4 && now - shown > 700) {
            capture("heads-before-name.png");
            prepareHeads("BEFORE_LINE");
            shown = now;
            phase = 5;
        } else if (phase == 5 && now - shown > 700) {
            capture("heads-before-line.png");
            finish();
        }
    }

    private static void verifyMetadata() {
        var minecraft = Minecraft.getInstance();
        for (String tag : TAGS) {
            var segments = DialogueTextSegment.parse("<" + tag + ">Sample text</" + tag.split(":")[0] + ">", DialogueTextEffects.NONE);
            require(segments.getFirst().effects().active(), "recognized effect " + tag);
            var component = VillagerStyledTextRenderer.component(segments, Style.EMPTY.withColor(0xabcdef), null);
            boolean animated = ToucanTextRenderer.usesAnimatedRenderer(VillagerStyledTextRenderer.effects(segments.getFirst().effects()));
            require(ToucanTextRenderer.hasGlyphEffects(component.getVisualOrderText()) == animated, "metadata for " + tag);
            for (var line : minecraft.font.split(component, 28))
                require(ToucanTextRenderer.hasGlyphEffects(line) == animated, "wrapped metadata for " + tag);
        }
        var repeated = styled("<wave>Same Same</wave> <shake>Same Same</shake>");
        var expected = new ArrayList<ToucanTextRenderer.GlyphEffect>();
        repeated.getVisualOrderText().accept((index, style, point) -> {
            expected.add(ToucanTextRenderer.glyphEffect(style));
            return true;
        });
        require(expected.get(0).effects().wavy() && expected.get(10).effects().shake(), "duplicate text keeps independent effects");
        require(expected.get(5).index() == 5 && expected.get(10).index() == 0, "effect phase remains attached to the original glyph");
        var gradient = styled("<wave><gradient:#ff0000:#0000ff>AB</gradient></wave>");
        var colors = new ArrayList<Integer>();
        gradient.getVisualOrderText().accept((index, style, point) -> {
            var effect = ToucanTextRenderer.glyphEffect(style);
            require(effect != null && effect.effects().wavy(), "gradient retains motion");
            colors.add(ToucanTextRenderer.colorWithAlpha(style, effect.effects(), 0xffffff, 255, 20, effect.index()) & 0xffffff);
            return true;
        });
        require(colors.equals(List.of(0xff0000, 0x0000ff)), "gradient endpoints survive animation");
        VillagerStyledTextRenderer.slice(gradient, 1, 2).getVisualOrderText().accept((index, style, point) -> {
            require(style.getColor().getValue() == 0x0000ff && ToucanTextRenderer.glyphEffect(style).index() == 1,
                    "interaction reveal slices retain gradient colors and animation phase");
            return true;
        });
        var bold = DialogueTextSegment.parse("<bold>" + "Bold words ".repeat(14) + "</bold>", DialogueTextEffects.NONE);
        String boldText = DialogueTextSegment.plainText(bold);
        for (String line : VillagerInteractionScreen.wrappedInteractionDialogueLines(minecraft.font, boldText, bold)) {
            require(minecraft.font.width(Component.literal(line).withStyle(Style.EMPTY.withBold(true))) <= 211,
                    "interaction wrapping measures styled glyph advances");
        }
        var motion = DialogueTextSegment.parse("<wave><jump><shake>Motion</shake></jump></wave>", DialogueTextEffects.NONE);
        require(VillagerStyledTextRenderer.verticalPadding(motion) >= 6 && VillagerStyledTextRenderer.horizontalPadding(motion) >= 3,
                "interaction clip bounds include combined motion and outline");
        var inherited = VillagerStyledTextRenderer.component(DialogueTextSegment.parse("<wave>A \uD83D\uDE00B</wave>", DialogueTextEffects.NONE),
                Style.EMPTY.withBold(true).withColor(0xabcdef), null);
        inherited.getVisualOrderText().accept((index, style, point) -> {
            require(style.isBold() && style.getColor().getValue() == 0xabcdef, "inherited formatting survives animation");
            require(ToucanTextRenderer.glyphEffect(style) != null, "whitespace and supplementary glyphs retain metadata");
            return true;
        });
        require(!ToucanTextRenderer.hasGlyphEffects(Component.literal("ordinary").withColor(0xabcdef).getVisualOrderText()),
                "equal colors on unrelated text do not acquire effects");
        var message = VillagerChatTextFormatter.format(Component.literal("<wave>Hello</wave> <jump>again</jump>"));
        minecraft.gui.getChat().clearMessages(true);
        minecraft.gui.getChat().addMessage(message);
        require(minecraft.gui.getChat().trimmedMessages.stream().anyMatch(line -> ToucanTextRenderer.hasGlyphEffects(line.content())),
                "native chat splitting retains effects");
        for (String tag : TAGS) {
            var sample = VillagerChatTextFormatter.format(Component.literal(tag + ": <" + tag + ">Sample text</" + tag.split(":")[0] + ">"));
            minecraft.gui.getChat().addMessage(sample);
        }
        minecraft.gui.getChat().rescaleChat();
        require(minecraft.gui.getChat().trimmedMessages.stream().anyMatch(line -> ToucanTextRenderer.hasGlyphEffects(line.content())),
                "chat resize retains effects");
    }

    private static Component styled(String text) {
        return VillagerStyledTextRenderer.component(DialogueTextSegment.parse(text, DialogueTextEffects.NONE), Style.EMPTY, 0xffffff);
    }

    private static void prepareHeads(String mode) {
        try {
            Class<?> heads = Class.forName("dzwdz.chat_heads.ChatHeads");
            Class<?> config = Class.forName("dzwdz.chat_heads.config.ChatHeadsConfig");
            Object original = heads.getField("CONFIG").get(null);
            Object position = Class.forName("dzwdz.chat_heads.config.RenderPosition").getField(mode).get(null);
            heads.getField("CONFIG").set(null, Proxy.newProxyInstance(config.getClassLoader(), new Class<?>[]{config},
                    (proxy, method, arguments) -> method.getName().equals("renderPosition") ? position : method.invoke(original, arguments)));
            var player = new net.minecraft.client.multiplayer.PlayerInfo(
                    new com.mojang.authlib.GameProfile(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), "Tester"), false);
            Object data = Class.forName("dzwdz.chat_heads.HeadData")
                    .getConstructor(net.minecraft.client.multiplayer.PlayerInfo.class, int.class).newInstance(player, 2);
            var chat = Minecraft.getInstance().gui.getChat();
            chat.clearMessages(true);
            chat.resetChatScroll();
            heads.getField("lastSenderData").set(null, data);
            var message = styled("  Tester: <wave>Wave text</wave> <rainbow>Rainbow</rainbow>");
            headText = message.getString();
            chat.addMessage(message);
            headMode = mode;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Chat Heads compatibility fixture failed", exception);
        }
    }

    private static void capture(String name) {
        Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), name, Minecraft.getInstance().getMainRenderTarget(), ignored -> { });
    }

    private static void finish() {
        require(frames > 5, "native effect samples rendered");
        try {
            Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"),
                    "Text effects client smoke passed\nChat Heads: " + ModList.get().isLoaded("chat_heads") + "\nFrames: " + frames + "\n");
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
        phase = 99;
        Minecraft.getInstance().stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class EffectScreen extends Screen {
        private boolean verifiedDraw;

        private EffectScreen() { super(Component.literal("Text effects checks")); }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff26313c);
            int tick = (int) ((Util.getMillis() - started) / 50);
            graphics.drawString(font, "Effect", 12, 12, 0xffffff);
            graphics.drawString(font, "GUI", 150, 12, 0xffffff);
            graphics.drawString(font, "Interaction outline", 350, 12, 0xffffff);
            int y = 32;
            for (String tag : TAGS) {
                String text = "<" + tag + ">Sample text</" + tag.split(":")[0] + ">";
                graphics.drawString(font, tag, 12, y + 3, 0xffffff);
                graphics.pose().pushPose();
                graphics.pose().translate(150, y, 0);
                VillagerStyledTextRenderer.renderSingleLine(graphics, font, text, 0xffffff, tick);
                graphics.pose().popPose();
                var segments = DialogueTextSegment.parse(text, DialogueTextEffects.NONE);
                VillagerStyledTextRenderer.renderOutlinedLine(graphics, font, "Sample text", segments, 350, y + 3, 0xffffffff, tick);
                y += 18;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(150, y, 0);
            VillagerStyledTextRenderer.renderWrapped(graphics, font,
                    "<wave><gradient:#ff0000:#0000ff>Gradient motion wraps across several lines</gradient></wave>", 130, 48, 0, 0xffffff, tick);
            graphics.pose().popPose();
            if (!verifiedDraw) {
                var calls = new ArrayList<String>();
                var line = styled("<wave>A B</wave>").getVisualOrderText();
                int end = ToucanTextRenderer.renderChatLine(graphics, font, line, 10, 320, 0xffffffff, tick,
                        (glyph, x, drawY, color) -> {
                            calls.add(ToucanTextRenderer.plainText(glyph));
                            return x + font.width(glyph) + 1 + (calls.size() == 2 ? 10 : 0);
                        });
                require(calls.equals(List.of("A", " ", "B")), "chat renders every glyph including head insertion spaces");
                require(end == 10 + font.width(line) + 10 + 1, "inline head advance propagates to later glyphs");
                for (String tag : List.of("wave", "shake", "pulse", "jump", "rainbow")) {
                    var animated = styled("<" + tag + ">Animated</" + tag + ">").getVisualOrderText();
                    require(!drawSamples(graphics, animated, 0).equals(drawSamples(graphics, animated, 17)),
                            tag + " changes native glyph positions or colors over time");
                }
                var strongPulse = DialogueTextEffects.read(com.google.gson.JsonParser.parseString(
                        "{\"text_effects\":{\"pulse\":{\"strength\":4}}}").getAsJsonObject());
                var invisible = VillagerStyledTextRenderer.component(DialogueTextSegment.plain("A B", strongPulse), Style.EMPTY, 0xffffff);
                require(drawSamples(graphics, invisible.getVisualOrderText(), 21).size() == 3,
                        "fully transparent pulse glyphs still reach inline renderers");
                var bidi = styled("<wave>\u05d0\u05d1</wave>").getVisualOrderText();
                var indices = new ArrayList<Integer>();
                bidi.accept((index, style, point) -> {
                    indices.add(ToucanTextRenderer.glyphEffect(style).index());
                    return true;
                });
                require(indices.equals(List.of(1, 0)), "bidi visual order retains logical effect indices");
                VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.set(true);
                require(drawSamples(graphics, line, 0).equals(drawSamples(graphics, line, 17)), "disabled effects remain static");
                VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.set(false);
                verifyOutlines(graphics);
                verifiedDraw = true;
            }
            graphics.drawString(font, "Native chat " + headMode, 12, height - 85, 0xffffff);
            Minecraft.getInstance().gui.getChat().render(graphics, tick, mouseX, mouseY, true);
            if (headMode.equals("BEFORE_NAME")) {
                try {
                    Class<?> heads = Class.forName("dzwdz.chat_heads.ChatHeads");
                    int expected = headText.codePointCount(0, headText.length());
                    require(heads.getField("renderedChars").getInt(null) == expected, "Chat Heads sees every foreground glyph once");
                    require(heads.getField("renderedShadowChars").getInt(null) == expected, "Chat Heads sees every shadow glyph once");
                } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
            }
            frames++;
        }

        private void verifyOutlines(GuiGraphics graphics) {
            graphics.fill(8, 296, 230, 332, 0xff000000);
            ToucanHudText.drawOutlinedString(graphics, font, Component.literal("MMMM").withStyle(
                    Style.EMPTY.withColor(0xff0000).withObfuscated(true).withBold(true)),
                    12, 302, 0xffffffff, 0xff00ff00);
            ToucanHudText.drawOutlinedString(graphics, font, "\u00a7cH", 70, 302, 0xffffff, 0x00ff00);
            ToucanHudText.drawCenteredOutlinedString(graphics, font,
                    Component.literal("H").withColor(0xff0000), 110, 302, 0xffffffff, 0xff00ff00);
            ToucanHudText.drawOutlinedString(graphics, font, "Invisible", 140, 302, 0x01ffffff, 0xff00ff00);
            ToucanWorldTextIndicators.drawOutlinedText(font, graphics.bufferSource(), graphics.pose().last().pose(),
                    "H", 12, 320, 0xffff0000, false);
            var styles = List.of(Style.EMPTY.withItalic(true), Style.EMPTY.withBold(true),
                    Style.EMPTY.withUnderlined(true), Style.EMPTY.withStrikethrough(true));
            graphics.fill(8, 336, 230, 410, 0xff000000);
            for (int row = 0; row < styles.size(); row++) {
                var sample = Component.literal("Sample").withStyle(styles.get(row).withColor(0xff0000));
                ToucanHudText.drawOutlinedString(graphics, font, sample, 12, 340 + row * 16, 0xffffffff, 0xff00ff00);
                graphics.drawString(font, sample, 122, 340 + row * 16, 0xffffffff, false);
            }
            int[] visits = {0};
            FormattedCharSequence counted = sink -> {
                visits[0]++;
                return Component.literal("One pass").getVisualOrderText().accept(sink);
            };
            ToucanTextRenderer.renderOutlinedLine(graphics, font, counted, 240, 302, 0xffffffff, 0);
            require(visits[0] == 1, "outline resolves the input sequence once for every border pass and foreground");
            graphics.flush();
            var minecraft = Minecraft.getInstance();
            int scale = (int) minecraft.getWindow().getGuiScale();
            try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                screenshot.writeToFile(Path.of(System.getProperty(OUTPUT), "outline-pixels.png"));
                int red = 0;
                int green = 0;
                for (int y = 298; y < 314; y++) {
                    for (int x = 10; x < 125; x++) {
                        int rgb = screenshot.getPixelRGBA(x * scale, y * scale) & 0xffffff;
                        if (isOutlineForeground(rgb)) red++;
                        if (rgb == 0x00ff00) {
                            green++;
                            boolean adjacent = isOutlineForeground(screenshot.getPixelRGBA((x - 1) * scale, y * scale))
                                    || isOutlineForeground(screenshot.getPixelRGBA((x + 1) * scale, y * scale))
                                    || isOutlineForeground(screenshot.getPixelRGBA(x * scale, (y - 1) * scale))
                                    || isOutlineForeground(screenshot.getPixelRGBA(x * scale, (y + 1) * scale));
                            require(adjacent, "outline matches the same obfuscated or styled foreground glyph at " + x + "," + y);
                        }
                    }
                }
                require(red > 30 && green > 30, "styled components and legacy text preserve separate foreground and outline colors");
                for (int y = 298; y < 314; y++) {
                    for (int x = 138; x < 220; x++) {
                        require((screenshot.getPixelRGBA(x * scale, y * scale) & 0xffffff) == 0,
                                "near-zero alpha outline stays invisible");
                    }
                }
                int worldRed = 0;
                for (int y = 319; y < 329; y++) {
                    for (int x = 11; x < 20; x++) {
                        if ((screenshot.getPixelRGBA(x * scale, y * scale) & 0xffffff) == 0x0000ff) worldRed++;
                    }
                }
                require(worldRed > 0, "world text outline emits its foreground");
                for (int row = 0; row < styles.size(); row++) {
                    for (int y = 338 + row * 16; y < 352 + row * 16; y++) {
                        for (int x = 10; x < 100; x++) {
                            boolean outlined = (screenshot.getPixelRGBA(x * scale, y * scale) & 0xffffff) == 0x0000ff;
                            boolean vanilla = (screenshot.getPixelRGBA((x + 110) * scale, y * scale) & 0xffffff) == 0x0000ff;
                            require(outlined == vanilla, "outline preserves vanilla foreground pixels for style " + row + " at " + x + "," + y);
                        }
                    }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static boolean isOutlineForeground(int rgba) {
            int rgb = rgba & 0xffffff;
            return rgb == 0x0000ff || rgb == 0x5555ff;
        }

        private List<String> drawSamples(GuiGraphics graphics, FormattedCharSequence line, int tick) {
            var samples = new ArrayList<String>();
            ToucanTextRenderer.renderChatLine(graphics, font, line, 0, 0, 0xffffffff, tick,
                    (glyph, x, y, color) -> {
                        var pose = graphics.pose().last().pose();
                        samples.add(ToucanTextRenderer.plainText(glyph) + ":" + x + ":" + y + ":" + color + ":" + pose.m30() + ":" + pose.m31());
                        return x + font.width(glyph) + 1;
                    });
            return samples;
        }
    }
}
