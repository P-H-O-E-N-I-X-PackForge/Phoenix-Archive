package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class QuestSelectorScreen extends Screen {

    public record PickableQuest(boolean chronicles, String rawId, String title, boolean locked) {}

    private final ArchiveEditorScreen parent;
    private final List<PickableQuest> allQuests;
    private QuestList list;
    private EditBox searchBox;

    private final int guiWidth = 220;
    private final int guiHeight = 180;
    private int left, top;

    public QuestSelectorScreen(ArchiveEditorScreen parent, List<PickableQuest> allQuests) {
        super(Component.literal("Quest Selector"));
        this.parent = parent;
        this.allQuests = allQuests;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        this.left = (this.width - guiWidth) / 2;
        this.top = (this.height - guiHeight) / 2;

        this.searchBox = new EditBox(this.font, left + 10, top + 15, guiWidth - 20, 18, Component.empty());
        this.searchBox.setResponder(this::refreshList);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(ArchivePalette.TERM);
        this.addRenderableWidget(searchBox);

        this.list = new QuestList(this.minecraft, guiWidth - 10, guiHeight - 70, top + 40, top + guiHeight - 30);
        this.list.setLeftPos(left + 5);
        this.addRenderableWidget(list);

        this.addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> this.minecraft.setScreen(parent))
                .bounds(left + 10, top + guiHeight - 22, guiWidth - 20, 16).build());

        this.setInitialFocus(this.searchBox);
        refreshList("");
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    private void refreshList(String filter) {
        this.list.replaceEntries(allQuests.stream()
                .filter(q -> filter.isEmpty() || q.title().toLowerCase().contains(filter.toLowerCase()))
                .limit(100)
                .map(q -> new QuestEntry(q, this))
                .collect(Collectors.toList()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);

        graphics.fill(left, top, left + guiWidth, top + guiHeight, ArchivePalette.BG_SCRIM);
        graphics.renderOutline(left, top, guiWidth, guiHeight, ArchivePalette.TERM_BRIGHT);

        graphics.fill(left + 8, top + 13, left + guiWidth - 8, top + 34,
                ArchivePalette.withAlpha(ArchivePalette.TERM, 0x44));

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    class QuestList extends ObjectSelectionList<QuestEntry> {

        public QuestList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, 14);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }

        public void replaceEntries(List<QuestEntry> entries) {
            this.clearEntries();
            entries.forEach(this::addEntry);
            this.setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return 200;
        }

        @Override
        protected int getScrollbarPosition() {
            return QuestSelectorScreen.this.left + QuestSelectorScreen.this.guiWidth - 10;
        }
    }

    class QuestEntry extends ObjectSelectionList.Entry<QuestEntry> {

        private final PickableQuest quest;
        private final QuestSelectorScreen screen;

        public QuestEntry(PickableQuest quest, QuestSelectorScreen screen) {
            this.quest = quest;
            this.screen = screen;
        }

        @Override
        public void render(GuiGraphics g, int i, int top, int left, int w, int h, int mx, int my, boolean hover,
                           float p) {
            String sourceTag = quest.chronicles() ? "§d[CHR] " : "§7[FTB] ";
            String text = sourceTag + quest.title();

            int color = quest.locked() ? ArchivePalette.TEXT_FAINT :
                    (hover ? ArchivePalette.TERM_BRIGHT : ArchivePalette.TERM);
            String prefix = quest.locked() ? "[LOCKED] " : (hover ? "> " : "  ");

            g.drawString(Minecraft.getInstance().font, prefix + text, left, top + 2, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (quest.chronicles()) {
                screen.parent.setLeafValue("chronicles_quest", quest.rawId());
                screen.parent.chroniclesQuestName = quest.title();
            } else {
                screen.parent.questId = Long.parseLong(quest.rawId());
                screen.parent.questName = quest.title();
            }
            Minecraft.getInstance().setScreen(screen.parent);
            return true;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.literal(quest.title());
        }
    }
}
