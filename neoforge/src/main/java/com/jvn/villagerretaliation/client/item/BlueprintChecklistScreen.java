package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.network.BlueprintChecklistSyncPayload;
import com.jvn.villagerretaliation.network.BlueprintChecklistTogglePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlueprintChecklistScreen extends BookViewScreen {
    private static final int IMAGE_WIDTH = 192;
    private static final int LINE_HEIGHT = 9;
    private static final int MAX_PAGE_LINES = TEXT_HEIGHT / LINE_HEIGHT;
    private static final int MAX_TITLE_LINES = 3;
    private static final int CHECKBOX_SIZE = 17;
    private static final int ROW_ICON_OFFSET = 19;
    private static final String ROW_INDENT = "          ";
    private static final ResourceLocation CHECKBOX_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/checkbox");
    private static final ResourceLocation CHECKBOX_HIGHLIGHTED_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/checkbox_highlighted");
    private static final ResourceLocation CHECKBOX_SELECTED_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/checkbox_selected");
    private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/checkbox_selected_highlighted");
    private BlueprintChecklistSyncPayload payload;
    private List<PageLayout> layouts;
    private int checklistPage;

    public BlueprintChecklistScreen(BlueprintChecklistSyncPayload payload) {
        this(payload, layoutPages(payload));
    }

    private BlueprintChecklistScreen(BlueprintChecklistSyncPayload payload, List<PageLayout> layouts) {
        super(access(layouts));
        this.payload = payload;
        this.layouts = layouts;
    }

    public void accept(BlueprintChecklistSyncPayload payload) {
        if (payload.hand() != this.payload.hand()) {
            return;
        }
        this.payload = payload;
        this.layouts = layoutPages(payload);
        int pages = this.layouts.size();
        this.checklistPage = Math.min(this.checklistPage, pages - 1);
        setBookAccess(access(this.layouts));
        forcePage(this.checklistPage);
    }

    @Override
    protected void pageBack() {
        if (this.checklistPage > 0) {
            this.checklistPage--;
        }
        super.pageBack();
    }

    @Override
    protected void pageForward() {
        if (this.checklistPage + 1 < this.layouts.size()) {
            this.checklistPage++;
        }
        super.pageForward();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (this.width - IMAGE_WIDTH) / 2;
        ItemStack hovered = ItemStack.EMPTY;
        PageLayout layout = this.layouts.get(this.checklistPage);
        for (RowLayout row : layout.rows()) {
            BlueprintChecklistSyncPayload.EntryView entry = this.payload.entries().get(row.entryIndex());
            int y = PAGE_TEXT_Y_OFFSET - 4 + row.startLine() * LINE_HEIGHT;
            boolean rowHovered = inside(
                    mouseX, mouseY, left + PAGE_TEXT_X_OFFSET, y,
                    TEXT_WIDTH, Math.max(CHECKBOX_SIZE, row.lineCount() * LINE_HEIGHT));
            renderCheckbox(
                    graphics, left + PAGE_TEXT_X_OFFSET, y, entry.checked(), rowHovered);
            graphics.renderItem(entry.item(), left + PAGE_TEXT_X_OFFSET + ROW_ICON_OFFSET, y);
            if (inside(mouseX, mouseY, left + PAGE_TEXT_X_OFFSET + ROW_ICON_OFFSET, y, 16, 16)) {
                hovered = entry.item();
            }
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (this.width - IMAGE_WIDTH) / 2;
            for (RowLayout row : this.layouts.get(this.checklistPage).rows()) {
                int y = PAGE_TEXT_Y_OFFSET - 4 + row.startLine() * LINE_HEIGHT;
                if (inside(mouseX, mouseY, left + PAGE_TEXT_X_OFFSET, y,
                        TEXT_WIDTH, Math.max(CHECKBOX_SIZE, row.lineCount() * LINE_HEIGHT))) {
                    PacketDistributor.sendToServer(
                            new BlueprintChecklistTogglePayload(this.payload.hand(), row.entryIndex()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void renderCheckbox(
            GuiGraphics graphics, int x, int y, boolean checked, boolean highlighted) {
        ResourceLocation sprite = checked
                ? (highlighted ? CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE : CHECKBOX_SELECTED_SPRITE)
                : (highlighted ? CHECKBOX_HIGHLIGHTED_SPRITE : CHECKBOX_SPRITE);
        graphics.blitSprite(sprite, x, y, CHECKBOX_SIZE, CHECKBOX_SIZE);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static BookAccess access(List<PageLayout> layouts) {
        return new BookAccess(layouts.stream().map(PageLayout::contents).toList());
    }

    private static List<PageLayout> layoutPages(BlueprintChecklistSyncPayload payload) {
        Font font = Minecraft.getInstance().font;
        List<String> titleLines = wrappedLines(
                font, Component.literal(payload.title()).withStyle(ChatFormatting.BOLD), TEXT_WIDTH);
        if (titleLines.size() > MAX_TITLE_LINES) {
            titleLines = new ArrayList<>(titleLines.subList(0, MAX_TITLE_LINES));
            int last = titleLines.size() - 1;
            titleLines.set(last, fitWithEllipsis(font, titleLines.get(last), TEXT_WIDTH, true));
        }

        List<PageLayout> pages = new ArrayList<>();
        List<Component> lines = pageHeader(titleLines);
        List<RowLayout> rows = new ArrayList<>();
        int labelWidth = TEXT_WIDTH - font.width(ROW_INDENT);
        int maximumRowLines = Math.max(2, MAX_PAGE_LINES - lines.size());

        for (int index = 0; index < payload.entries().size(); index++) {
            BlueprintChecklistSyncPayload.EntryView entry = payload.entries().get(index);
            String label = entry.observed() + "/" + entry.required();
            List<String> labelLines = wrappedLines(font, Component.literal(label), labelWidth);
            if (labelLines.size() > maximumRowLines) {
                labelLines = new ArrayList<>(labelLines.subList(0, maximumRowLines));
                int last = labelLines.size() - 1;
                labelLines.set(last, fitWithEllipsis(font, labelLines.get(last), labelWidth, false));
            }
            int rowLines = Math.max(2, labelLines.size());
            if (!rows.isEmpty() && lines.size() + rowLines > MAX_PAGE_LINES) {
                pages.add(new PageLayout(joinLines(lines), List.copyOf(rows)));
                lines = pageHeader(titleLines);
                rows = new ArrayList<>();
            }

            int startLine = lines.size();
            rows.add(new RowLayout(index, startLine, rowLines));
            for (String wrappedLine : labelLines) {
                MutableComponent text = Component.literal(wrappedLine)
                        .withStyle(entry.checked() ? ChatFormatting.GRAY : ChatFormatting.BLACK);
                if (entry.checked()) {
                    text.withStyle(ChatFormatting.STRIKETHROUGH);
                }
                lines.add(Component.empty().append(Component.literal(ROW_INDENT)).append(text));
            }
            while (lines.size() < startLine + rowLines) {
                lines.add(Component.empty());
            }
        }

        if (!rows.isEmpty() || pages.isEmpty()) {
            pages.add(new PageLayout(joinLines(lines), List.copyOf(rows)));
        }
        return List.copyOf(pages);
    }

    private static List<Component> pageHeader(List<String> titleLines) {
        List<Component> lines = new ArrayList<>(titleLines.size() + 1);
        for (String titleLine : titleLines) {
            lines.add(Component.literal(titleLine).withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD));
        }
        lines.add(Component.empty());
        return lines;
    }

    private static List<String> wrappedLines(Font font, FormattedText text, int width) {
        List<String> lines = font.getSplitter().splitLines(text, width, Style.EMPTY).stream()
                .map(FormattedText::getString)
                .toList();
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static Component joinLines(List<Component> lines) {
        MutableComponent contents = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                contents.append("\n");
            }
            contents.append(lines.get(index));
        }
        return contents;
    }

    private static String fitWithEllipsis(Font font, String value, int width, boolean bold) {
        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && styledWidth(font, value.substring(0, end) + ellipsis, bold) > width) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }

    private static int styledWidth(Font font, String text, boolean bold) {
        Component component = Component.literal(text);
        return font.width(bold ? component.copy().withStyle(ChatFormatting.BOLD) : component);
    }

    private record PageLayout(Component contents, List<RowLayout> rows) {
    }

    private record RowLayout(int entryIndex, int startLine, int lineCount) {
    }
}
