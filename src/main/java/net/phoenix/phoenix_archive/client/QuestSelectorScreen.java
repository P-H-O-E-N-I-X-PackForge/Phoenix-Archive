package net.phoenix.phoenix_archive.client;

import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class QuestSelectorScreen extends Screen {
    private final ArchiveEditorScreen parent;
    private final List<Quest> allQuests;
    private QuestList list;
    private EditBox searchBox;

    // Fixed Dimensions for the "Themed" look
    private final int guiWidth = 220;
    private final int guiHeight = 180;
    private int left, top;

    public QuestSelectorScreen(ArchiveEditorScreen parent, List<Quest> allQuests) {
        super(Component.literal("Quest Selector"));
        this.parent = parent;
        this.allQuests = allQuests;
    }

    @Override
    protected void init() {
        this.left = (this.width - guiWidth) / 2;
        this.top = (this.height - guiHeight) / 2;

        // Search Box centered in the themed frame
        this.searchBox = new EditBox(this.font, left + 10, top + 15, guiWidth - 20, 18, Component.empty());
        this.searchBox.setResponder(this::refreshList);
        this.searchBox.setBordered(false); // Looks more "terminal"
        this.searchBox.setTextColor(0x00FF00);
        this.addRenderableWidget(searchBox);

        // The Scroll List - Constrained to inside the frame
        // (mc, width, height, top_pos, bottom_pos, item_height)
        this.list = new QuestList(this.minecraft, guiWidth - 10, guiHeight - 70, top + 40, top + guiHeight - 30);
        this.list.setLeftPos(left + 5);
        this.addRenderableWidget(list);

        this.addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> this.minecraft.setScreen(parent))
                .bounds(left + 10, top + guiHeight - 22, guiWidth - 20, 16).build());

        this.setInitialFocus(this.searchBox);
        refreshList("");
    }

    private void refreshList(String filter) {
        this.list.replaceEntries(allQuests.stream()
                .filter(q -> filter.isEmpty() || q.getTitle().getString().toLowerCase().contains(filter.toLowerCase()))
                // We do NOT call q.isVisible() here so devs can see everything
                .limit(100)
                .map(q -> new QuestEntry(q, this))
                .collect(Collectors.toList()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        // Terminal Frame (Matching TerminalInputScreen)
        graphics.fill(left, top, left + guiWidth, top + guiHeight, 0xEE050505);
        graphics.renderOutline(left, top, guiWidth, guiHeight, 0xFF00FF00);

        // Input Line highlight
        graphics.fill(left + 8, top + 13, left + guiWidth - 8, top + 34, 0x4400FF00);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    // --- INNER CLASSES ---

    class QuestList extends ObjectSelectionList<QuestEntry> {
        public QuestList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, 14);
            this.setRenderBackground(false); // Removes the dirt background
            this.setRenderTopAndBottom(false); // Removes the dark gradients
        }

        public void replaceEntries(List<QuestEntry> entries) {
            this.clearEntries();
            entries.forEach(this::addEntry);
            this.setScrollAmount(0);
        }

        @Override
        public int getRowWidth() { return 200; }

        @Override
        protected int getScrollbarPosition() {
            // Logic: The scrollbar should be on the far right of our themed box.
            // We calculate this relative to the Screen's 'left' variable.
            return QuestSelectorScreen.this.left + QuestSelectorScreen.this.guiWidth - 10;
        }
    }

    class QuestEntry extends ObjectSelectionList.Entry<QuestEntry> {
        private final Quest quest; // Declare as a field so it's accessible in render()
        private final QuestSelectorScreen screen;

        public QuestEntry(Quest quest, QuestSelectorScreen screen) {
            this.quest = quest;
            this.screen = screen;
        }

        @Override
        public void render(GuiGraphics g, int i, int top, int left, int w, int h, int mx, int my, boolean hover, float p) {
            String text = quest.getTitle().getString();
            boolean isLocked = false;

            // Use Reflection to check the private 'invisibleUntilCompleted' field from Quest.java
            try {
                java.lang.reflect.Field field = quest.getClass().getDeclaredField("invisibleUntilCompleted");
                field.setAccessible(true);
                isLocked = field.getBoolean(quest);
            } catch (Exception ignored) {}

            // Dev Theme: Grayed out if locked, Green if active
            int color = isLocked ? 0x666666 : (hover ? 0x00FF00 : 0x00AA00);
            String prefix = isLocked ? "[LOCKED] " : (hover ? "> " : "  ");

            g.drawString(Minecraft.getInstance().font, prefix + text, left, top + 2, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            screen.parent.questId = quest.id;
            screen.parent.questName = quest.getTitle().getString();
            Minecraft.getInstance().setScreen(screen.parent);
            return true;
        }

        @Override
        public @NotNull Component getNarration() { return quest.getTitle(); }
    }
}