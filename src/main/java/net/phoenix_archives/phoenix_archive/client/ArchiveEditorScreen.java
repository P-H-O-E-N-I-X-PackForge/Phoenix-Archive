package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.phoenix_archives.phoenix_archive.api.CategoryDefinition;
import net.phoenix_archives.phoenix_archive.api.CategoryRegistry;
import net.phoenix_archives.phoenix_archive.api.ConditionNode;
import net.phoenix_archives.phoenix_archive.api.ConditionNodeAdapter;
import net.phoenix_archives.phoenix_archive.api.LoreDataLoader;
import net.phoenix_archives.phoenix_archive.api.LoreEntry;
import net.phoenix_archives.phoenix_archive.config.ArchiveConfigs;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

public class ArchiveEditorScreen extends Screen {

    private final LoreEntry editingEntry;
    private final String initialCategory;

    private final Screen parent;

    private EditBox titleBox, iconBox, voiceLineBox, idBox, shaderBox;
    private MultiLineEditBox contentBox, lockedContentBox;

    private List<String> categoryList = new ArrayList<>();
    private int categoryIndex = 0;

    private static final Gson GSON = ConditionNodeAdapter.register(new GsonBuilder().setPrettyPrinting()).create();

    public String savedTitle = "";
    public String savedIcon = "";
    public String savedContent = "";
    public String savedId = "";
    public String savedLockedContent = "";
    public String savedVoiceLine = "";
    public String savedBackgroundShader = "";
    public boolean savedHidden = false;
    public String savedHiddenUntilId = "";
    public long questId = 0;
    public String questName = "None Selected";

    public String chroniclesQuestName = "";
    private int currentOrder = -1;
    public ConditionNode currentConditionTree = ConditionNode.EMPTY;

    private boolean isDirty = false;

    private boolean suppressDirtyGuard = false;

    public ArchiveEditorScreen(Screen parent, LoreEntry existing, String defaultCategory) {
        super(Component.literal("Archive Editor"));
        this.parent = parent;
        this.editingEntry = existing;
        this.initialCategory = (defaultCategory == null || defaultCategory.isEmpty()) ? "GENERAL" : defaultCategory;

        if (existing != null) {
            setupFromEntry(existing);
        }
    }

    private void returnToParent() {
        this.minecraft.setScreen(parent != null ? parent : new ArchiveScreen());
    }

    private void setupFromEntry(LoreEntry entry) {
        this.savedId = entry.id();
        this.savedTitle = entry.title();
        this.savedIcon = entry.iconItem() != null ? entry.iconItem() : "";
        this.savedContent = entry.content().replace("§", "&");
        this.savedLockedContent = entry.lockedContent() != null ? entry.lockedContent().replace("§", "&") : "";
        this.savedVoiceLine = entry.voiceLine() != null ? entry.voiceLine() : "";
        this.savedBackgroundShader = entry.backgroundShader() != null ? entry.backgroundShader() : "";
        this.savedHidden = entry.hidden();
        this.savedHiddenUntilId = entry.hiddenUntilId() != null ? entry.hiddenUntilId() : "";
        this.questId = entry.questId();
        this.currentOrder = entry.order();
        this.currentConditionTree = entry.conditionTree();
        this.chroniclesQuestName = resolveChroniclesQuestTitle(
                entry.conditionTree().findLeafValue("chronicles_quest").orElse(""));
    }

    private String resolveChroniclesQuestTitle(String rawId) {
        if (rawId == null || rawId.isEmpty() || !ModList.get().isLoaded("phoenix_chronicles")) return "";
        try {
            for (var quest : net.phoenixvine.chronicles.registry.QuestTreeRegistry.getAllQuests().values()) {
                if (quest.getId().toString().equals(rawId)) return quest.getTitle().getString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String getCurrentCategory() {
        if (categoryList.isEmpty()) return initialCategory;
        return categoryList.get(Math.min(categoryIndex, categoryList.size() - 1));
    }

    public ConditionNode getConditionTree() {
        return this.currentConditionTree;
    }

    public void setConditionTree(ConditionNode tree) {
        this.currentConditionTree = tree;
        this.isDirty = true;
    }

    public String getLeafValue(String type) {
        return currentConditionTree.findLeafValue(type).orElse("");
    }

    public void setLeafValue(String type, String value) {
        this.currentConditionTree = currentConditionTree.withTopLevelLeaf(type, value);
        this.isDirty = true;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        if (idBox != null) updateSavedValues();

        buildCategoryList();

        int x = this.width / 2 - 100;
        int rightX = x + 205;

        idBox = new EditBox(this.font, x, 10, 200, 20, Component.empty());
        idBox.setHint(Component.literal("§6Permanent ID (e.g. log_001)"));
        idBox.setValue(savedId);
        idBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(idBox);

        titleBox = new EditBox(this.font, x, 35, 200, 20, Component.empty());
        titleBox.setHint(Component.literal("§8Title..."));
        titleBox.setValue(savedTitle);
        titleBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(titleBox);

        this.addRenderableWidget(Button.builder(Component.literal("TERMINAL >_"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "ENTRY_TITLE", this.savedTitle, (val) -> {
                this.savedTitle = val;
                this.titleBox.setValue(val);
            }));
        }).bounds(x - 85, 35, 80, 20)
                .tooltip(Tooltip.create(Component.literal("Open Terminal Editor for Title")))
                .build());

        String currentCat = getCurrentCategory();
        int currentDepth = CategoryRegistry.getDepth(currentCat);
        String depthPrefix = "  ".repeat(currentDepth);
        String cycleLbl = "§8< §7" + depthPrefix + currentCat + " §8>";

        this.addRenderableWidget(Button.builder(Component.literal(cycleLbl), b -> {
            updateSavedValues();

            categoryIndex = (categoryIndex + 1) % Math.max(1, categoryList.size() - 1);
            this.init(this.minecraft, this.width, this.height);
        }).bounds(x, 60, 170, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§a+"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "NEW_CATEGORY", "", (res) -> {
                if (res != null && !res.isEmpty()) {
                    CategoryRegistry.register(res, "", 50, null);
                    saveCategoryToDisk(res);
                    buildCategoryList();
                    for (int i = 0; i < categoryList.size(); i++) {
                        if (categoryList.get(i).equalsIgnoreCase(res)) {
                            categoryIndex = i;
                            break;
                        }
                    }
                }
                this.init(this.minecraft, this.width, this.height);
            }));
        }).bounds(x + 172, 60, 28, 20)
                .tooltip(Tooltip.create(Component.literal("Make a New Category")))
                .build());

        iconBox = new EditBox(this.font, x, 85, 200, 20, Component.empty());
        iconBox.setMaxLength(512);
        iconBox.setHint(Component.literal("§8Icon (minecraft:apple)"));
        iconBox.setValue(savedIcon);
        iconBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(iconBox);

        voiceLineBox = new EditBox(this.font, x, 110, 200, 20, Component.empty());
        voiceLineBox.setHint(Component.literal("§dVoice (phoenix_archive:voice.filename)"));
        voiceLineBox.setValue(savedVoiceLine);
        voiceLineBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(voiceLineBox);

        contentBox = new MultiLineEditBox(this.font, x, 135, 200, 45, Component.empty(), Component.empty());
        contentBox.setValue(savedContent);
        contentBox.setValueListener(v -> isDirty = true);
        this.addRenderableWidget(contentBox);

        lockedContentBox = new MultiLineEditBox(this.font, x, 185, 200, 25, Component.empty(), Component.empty());
        lockedContentBox.setValue(savedLockedContent);
        lockedContentBox.setValueListener(v -> isDirty = true);
        this.addRenderableWidget(lockedContentBox);

        int btnWidth = 80;
        this.addRenderableWidget(Button.builder(Component.literal("TERMINAL >_"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "UNLOCKED_DATA", this.savedContent, (val) -> {
                this.savedContent = val;
                this.contentBox.setValue(val);
            }));
        }).bounds(x - 85, 135, btnWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Open Terminal Editor for Unlocked Content")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("TERMINAL >_"), b -> {
            updateSavedValues();
            this.minecraft
                    .setScreen(new TerminalInputScreen(this, "ENCRYPTED_SIGNAL", this.savedLockedContent, (val) -> {
                        this.savedLockedContent = val;
                        this.lockedContentBox.setValue(val);
                    }));
        }).bounds(x - 85, 185, btnWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Open Terminal Editor for Locked Content")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("§6EDIT_LOGIC"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ConditionTunerScreen(this, this.currentConditionTree));
        }).bounds(rightX, 60, 95, 20)
                .tooltip(Tooltip.create(Component.literal("Add/Remove Entry Conditions")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("SELECT_QUEST"), b -> {
            updateSavedValues();
            openQuestSelector();
        }).bounds(rightX, 35, 95, 20)
                .tooltip(Tooltip.create(Component.literal("Open Quest Selector (Chronicles + FTB Quests)")))
                .build());

        boolean hasChroniclesQuest = !getLeafValue("chronicles_quest").isEmpty();
        if (questId != 0 || hasChroniclesQuest) {
            this.addRenderableWidget(Button.builder(Component.literal("§4CLEAR QUEST"), b -> {
                this.questId = 0;
                this.questName = "None Selected";
                this.chroniclesQuestName = "";
                setLeafValue("chronicles_quest", "");
                this.init(this.minecraft, this.width, this.height);
            }).bounds(rightX, 17, 95, 16)
                    .tooltip(Tooltip.create(Component.literal("Clear Selected Quest")))
                    .build());
        }

        shaderBox = new EditBox(this.font, rightX, 145, 74, 16, Component.empty());
        shaderBox.setMaxLength(64);
        shaderBox.setHint(Component.literal("§8bg shader id"));
        shaderBox.setValue(savedBackgroundShader);
        shaderBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(shaderBox);

        this.addRenderableWidget(Button.builder(Component.literal("..."), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ArchiveShaderPickerScreen(this, id -> {
                this.savedBackgroundShader = id;

                if (this.shaderBox != null) this.shaderBox.setValue(id);
                this.isDirty = true;
            }));
        }).bounds(rightX + 77, 145, 18, 16)
                .tooltip(Tooltip.create(Component.literal("Browse Shaders")))
                .build());

        this.addRenderableWidget(Button.builder(
                Component.literal(savedHidden ? "§eHidden From List: ON" : "§8Hidden From List: OFF"),
                b -> {
                    savedHidden = !savedHidden;
                    isDirty = true;
                    this.init(this.minecraft, this.width, this.height);
                })
                .bounds(x, 215, 200, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "When ON, this entry is left out of the sidebar entirely (instead of showing\n" +
                                "greyed-out with its locked text) until the unlock check below passes.")))
                .build());

        String unlockLabel = savedHiddenUntilId.isEmpty() ? "§8(this entry's own unlock)" : "§f" + savedHiddenUntilId;
        int unlockBtnW = savedHiddenUntilId.isEmpty() ? 200 : 178;
        this.addRenderableWidget(Button.builder(Component.literal("§6UNLOCK_CHECK: " + unlockLabel), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ArchiveEntryPickerScreen(this, savedId, id -> {
                this.savedHiddenUntilId = id;
                this.isDirty = true;
                this.init(this.minecraft, this.width, this.height);
            }));
        }).bounds(x, 234, unlockBtnW, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Which entry's unlock state gates this one appearing (used with Hidden From " +
                                "List above). Defaults to this entry's own unlock if none is picked.")))
                .build());

        if (!savedHiddenUntilId.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("§4X"), b -> {
                savedHiddenUntilId = "";
                isDirty = true;
                this.init(this.minecraft, this.width, this.height);
            }).bounds(x + 181, 234, 19, 16)
                    .tooltip(Tooltip.create(Component.literal("Clear (gate on this entry's own unlock)")))
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("§2SAVE PACKET"), b -> saveEntry())
                .bounds(x, 258, 200, 20)
                .tooltip(Tooltip.create(Component.literal("Save Archive Entry")))
                .build());
    }

    private void buildCategoryList() {
        Set<String> seen = new HashSet<>();
        List<String> ordered = new ArrayList<>();

        Set<String> allIds = new HashSet<>(CategoryRegistry.getRegisteredIds());
        LoreDataLoader.LORE_ENTRIES.values().forEach(e -> allIds.add(e.category().toUpperCase()));
        if (allIds.isEmpty()) allIds.add("GENERAL");

        walkCategoryTree(null, allIds, seen, ordered);

        for (String id : allIds) {
            if (!seen.contains(id)) {
                ordered.add(id);
                seen.add(id);
            }
        }

        ordered.add("+ NEW");

        this.categoryList = ordered;

        String wantedCat = (editingEntry != null && savedTitle.equals(editingEntry.title())) ?
                editingEntry.category().toUpperCase() : (ordered.contains(initialCategory.toUpperCase()) ?
                        initialCategory.toUpperCase() : (ordered.isEmpty() ? "GENERAL" : ordered.get(0)));

        int foundIndex = ordered.indexOf(wantedCat);
        if (foundIndex >= 0) {
            categoryIndex = foundIndex;
        } else if (categoryIndex >= ordered.size()) {
            categoryIndex = 0;
        }
    }

    private void walkCategoryTree(String parentId, Set<String> allIds, Set<String> seen, List<String> out) {
        List<String> children = new ArrayList<>(CategoryRegistry.getChildren(parentId));

        for (String id : allIds) {
            if (!children.contains(id) && !seen.contains(id)) {
                String regParent = CategoryRegistry.getParentId(id);
                if (Objects.equals(regParent, parentId == null ? null : parentId.toUpperCase())) {
                    children.add(id);
                }
            }
        }

        for (String cat : children) {
            if (seen.contains(cat)) continue;
            seen.add(cat);
            out.add(cat);
            walkCategoryTree(cat, allIds, seen, out);
        }
    }

    private void saveCategoryToDisk(String categoryId) {
        try {
            var def = new CategoryDefinition(
                    categoryId.toUpperCase(), "", 50, null);
            File file = new File("config/phoenix_archive/categories/" + categoryId.toLowerCase() + ".json");
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(def, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    @Override
    public void onClose() {
        if (isDirty && !suppressDirtyGuard) {
            this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                    confirmed -> {
                        suppressDirtyGuard = true;
                        if (confirmed) saveEntry();
                        else returnToParent();
                    },
                    Component.literal("§6[UNSAVED_CHANGES]"),
                    Component.literal("You have unsaved changes. Save before leaving?")));
        } else {
            returnToParent();
        }
    }

    private void saveEntry() {
        updateSavedValues();

        if (savedId.isEmpty()) {
            savedId = savedTitle.toLowerCase().replaceAll("[^a-z0-9]", "_");
        }

        int finalOrder = (this.currentOrder != -1) ? this.currentOrder : LoreDataLoader.LORE_ENTRIES.size();
        String finalCategory = getCurrentCategory();

        LoreEntry entry = new LoreEntry(
                savedId,
                savedTitle,
                finalCategory,
                savedContent,
                savedIcon,
                (int) questId,
                savedLockedContent,
                savedVoiceLine,
                currentConditionTree,
                finalOrder,
                savedBackgroundShader.trim(),
                savedHidden,
                savedHiddenUntilId.trim());

        String fileName = (editingEntry != null) ? savedId : savedTitle.toLowerCase().replaceAll("[^a-z0-9]", "_");
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config/phoenix_archive/lore");

        try {
            File dir = path.toFile();
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter writer = new FileWriter(new File(dir, fileName + ".json"))) {
                GSON.toJson(entry, writer);
            }

            ResourceLocation resId = new ResourceLocation("phoenix_archive", fileName);
            LoreDataLoader.LORE_ENTRIES.put(resId, entry);

            boolean hasConditions = !entry.conditionTree().isEmpty();
            boolean hasQuest = entry.questId() != 0;
            if (hasConditions || hasQuest) {
                ArchiveScreen.CLIENT_LORE_CACHE.remove("lore_unlocked:" + savedId);
            }

            if (parent instanceof ArchiveScreen archiveParent) {
                archiveParent.refreshSelectedEntry(entry);
            }
            returnToParent();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        int rightX = (this.width / 2 - 100) + 205;
        graphics.drawString(this.font, "§6TRIGGER_LOGIC:", rightX, 85, ArchivePalette.TERM_BRIGHT);
        graphics.fill(rightX, 95, rightX + 110, 96, ArchivePalette.withAlpha(ArchivePalette.TERM_BRIGHT, 0x44));

        int statusY = 100;

        boolean hasAnything = questId != 0 || !currentConditionTree.isEmpty();

        if (!hasAnything) {
            graphics.drawString(this.font, "§8(Manual Only)", rightX, statusY, ArchivePalette.TEXT_DIM);
        } else {
            if (questId != 0) {
                graphics.drawString(this.font, "§b• Quest: " + questName + " §8(ftb_quest:" + questId + ")",
                        rightX, statusY, ArchivePalette.TEXT_DIM);
                statusY += 10;
            }

            for (var nl : currentConditionTree.collectLeaves()) {
                String value = nl.leaf().value();
                if (value == null || value.isEmpty()) continue;
                if (!nl.negated() && nl.leaf().type().equals("chronicles_quest")) {
                    String name = chroniclesQuestName.isEmpty() ? value : chroniclesQuestName;
                    graphics.drawString(this.font,
                            "§d• Chronicles Quest: " + name + " §8(chronicles_quest:" + value + ")",
                            rightX, statusY, ArchivePalette.TEXT_DIM);
                    statusY += 10;
                    continue;
                }
                if (nl.leaf().type().equals("external")) {
                    graphics.drawString(this.font,
                            (nl.negated() ? "§c• NOT External: " : "§e• External: ") +
                                    ArchiveScreen.externalProgressLabel(value),
                            rightX, statusY, ArchivePalette.TEXT_DIM);
                    statusY += 10;
                    continue;
                }
                String typeLabel = getConditionTypeLabel(nl.leaf().type());
                String prefix = (nl.negated() ? "§c• NOT " : "§e• ") + typeLabel + ": ";
                renderCondition(graphics, prefix, value, rightX, statusY);
                statusY += 10;
            }
        }

        graphics.drawString(this.font, "§6BG_SHADER:", rightX, 133, ArchivePalette.TERM_BRIGHT);

        graphics.drawString(this.font, "> " + ArchiveConfigs.INSTANCE.general.mainMenuName + "Lore_Dev",
                10, 10, ArchivePalette.TERM);
    }

    private String getConditionTypeLabel(String key) {
        return switch (key.toLowerCase()) {
            case "dimension" -> "Dimension";
            case "biome" -> "Biome";
            case "machine" -> "Place Machine";
            case "item" -> "Have Item";
            case "wearing" -> "Wear Item";
            case "suit_event" -> "Event";
            case "chronicles_quest" -> "Chronicles Quest";
            default -> prettifyId(key);
        };
    }

    private void renderCondition(GuiGraphics g, String prefix, String id, int x, int y) {
        if (id == null || id.isEmpty()) return;
        String displayName = prettifyId(id);
        String finalName = displayName.length() > 20 ? displayName.substring(0, 18) + ".." : displayName;
        g.drawString(this.font, prefix + "§f" + finalName, x, y, ArchivePalette.TERM_BRIGHT);
    }

    private String prettifyId(String id) {
        String path = id.contains(":") ? id.split(":")[1] : id;
        return Arrays.stream(path.replace("_", " ").split(" "))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(id);
    }

    private void openQuestSelector() {
        List<QuestSelectorScreen.PickableQuest> combined = new ArrayList<>();
        addChroniclesQuests(combined);
        addFtbQuests(combined);

        if (!combined.isEmpty()) {
            this.minecraft.setScreen(new QuestSelectorScreen(this, combined));
        }
    }

    private void addChroniclesQuests(List<QuestSelectorScreen.PickableQuest> out) {
        if (!ModList.get().isLoaded("phoenix_chronicles")) return;
        try {
            for (var quest : net.phoenixvine.chronicles.registry.QuestTreeRegistry.getAllQuests().values()) {
                out.add(new QuestSelectorScreen.PickableQuest(true, quest.getId().toString(),
                        quest.getTitle().getString(), false));
            }
        } catch (Exception ignored) {}
    }

    private void addFtbQuests(List<QuestSelectorScreen.PickableQuest> out) {
        if (!ModList.get().isLoaded("ftbquests")) return;
        try {
            var questFile = dev.ftb.mods.ftbquests.api.FTBQuestsAPI.api().getQuestFile(true);
            for (var chapter : questFile.getAllChapters()) {
                for (var quest : chapter.getQuests()) {
                    boolean locked = false;
                    try {
                        java.lang.reflect.Field field = quest.getClass().getDeclaredField("invisibleUntilCompleted");
                        field.setAccessible(true);
                        locked = field.getBoolean(quest);
                    } catch (Exception ignored) {}
                    out.add(new QuestSelectorScreen.PickableQuest(false, String.valueOf(quest.id),
                            quest.getTitle().getString(), locked));
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateSavedValues() {
        if (idBox != null) this.savedId = idBox.getValue();
        if (titleBox != null) this.savedTitle = titleBox.getValue();
        if (iconBox != null) this.savedIcon = iconBox.getValue();
        if (voiceLineBox != null) this.savedVoiceLine = voiceLineBox.getValue();
        if (shaderBox != null) this.savedBackgroundShader = shaderBox.getValue();
        if (contentBox != null) this.savedContent = contentBox.getValue();
        if (lockedContentBox != null) this.savedLockedContent = lockedContentBox.getValue();
    }
}
