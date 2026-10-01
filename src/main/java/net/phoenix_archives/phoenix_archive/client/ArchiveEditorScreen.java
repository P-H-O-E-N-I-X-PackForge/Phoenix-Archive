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
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.fml.ModList;
import net.phoenix_archives.phoenix_archive.api.CategoryDefinition;
import net.phoenix_archives.phoenix_archive.api.CategoryRegistry;
import net.phoenix_archives.phoenix_archive.api.ChroniclesQuestBridge;
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

    // ---- Responsive layout (recomputed on every init / window resize) ----
    private static final int MARGIN = 6;
    private static final int STATUS_MAX_LINES_PER_ENTRY = 2;

    private int termX, termW;
    private int fieldX, fieldW;
    private int rightX, rightW;

    private int rowH = 20;
    private int idY, titleY, catY, iconY, voiceY;
    private int contentY, contentH, lockedY, lockedH;
    private int hiddenY, unlockY, saveY;

    private int triggerY;
    private int statusStartY;
    private int shaderLabelY, shaderBoxY;
    private List<String> statusEntries = List.of();

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
        if (rawId == null || rawId.isEmpty()) return "";
        return ChroniclesQuestBridge.titleOf(rawId);
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

    /**
     * Works out every position from the current window size. Horizontal: terminal buttons | fields |
     * trigger-logic column, shrinking proportionally when the window is narrow. Vertical: rows compress
     * (20px/5px gaps -> 16px/3px gaps) when short, and the two text boxes absorb whatever height is left.
     */
    private void computeLayout() {
        // ---- horizontal ----
        int remaining = Math.max(200, (this.width - 16) - 10); // 16 = side margins, 10 = two 5px gaps
        if (remaining >= 375) {
            termW = 80;
            fieldW = 200;
            rightW = 95;
        } else {
            termW = Math.max(48, remaining * 80 / 375);
            rightW = Math.max(66, remaining * 95 / 375);
            fieldW = Math.max(100, remaining - termW - rightW);
        }
        int blockW = termW + 5 + fieldW + 5 + rightW;
        termX = (this.width - blockW) / 2;
        fieldX = termX + termW + 5;
        rightX = fieldX + fieldW + 5;

        // ---- vertical ----
        int avail = this.height - 2 * MARGIN;
        // 6 full-height rows (id, title, category, icon, voice, save) + 2 small rows (hidden, unlock) + 9 gaps
        boolean roomy = avail - (6 * 20 + 2 * 16 + 9 * 5) >= 70;
        rowH = roomy ? 20 : 16;
        int gap = roomy ? 5 : 3;
        int fixed = 6 * rowH + 2 * 16 + 9 * gap;
        int flex = Math.max(50, avail - fixed);
        contentH = Math.min(140, Math.max(30, flex * 64 / 100));
        lockedH = Math.min(70, Math.max(20, flex - contentH));

        int used = fixed + contentH + lockedH;
        int y = Math.max(MARGIN, (this.height - used) / 2);

        idY = y;
        y += rowH + gap;
        titleY = y;
        y += rowH + gap;
        catY = y;
        y += rowH + gap;
        iconY = y;
        y += rowH + gap;
        voiceY = y;
        y += rowH + gap;
        contentY = y;
        y += contentH + gap;
        lockedY = y;
        y += lockedH + gap;
        hiddenY = y;
        y += 16 + gap;
        unlockY = y;
        y += 16 + gap;
        saveY = y;

        // ---- trigger-logic column: status lines wrap, and the shader row sits below them ----
        triggerY = iconY;
        statusStartY = triggerY + 15;
        statusEntries = buildStatusEntries();
        int statusLines = 0;
        for (String entry : statusEntries) statusLines += wrapStatus(entry).size();

        int labelY = Math.max(triggerY + 48, statusStartY + statusLines * 10 + 4);
        int boxY = labelY + 12;
        int maxBoxY = this.height - MARGIN - 16;
        if (boxY > maxBoxY) {
            boxY = maxBoxY;
            labelY = boxY - 12;
        }
        shaderLabelY = labelY;
        shaderBoxY = boxY;
    }

    private int statusWidth() {
        return Math.max(40, this.width - MARGIN - rightX);
    }

    private List<FormattedCharSequence> wrapStatus(String text) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), statusWidth());
        return lines.size() > STATUS_MAX_LINES_PER_ENTRY ? lines.subList(0, STATUS_MAX_LINES_PER_ENTRY) : lines;
    }

    private List<String> buildStatusEntries() {
        List<String> out = new ArrayList<>();
        boolean hasAnything = questId != 0 || !currentConditionTree.isEmpty();

        if (!hasAnything) {
            out.add("§8(Manual Only)");
            return out;
        }

        if (questId != 0) {
            out.add("§b• Quest: " + questName + " §8(ftb_quest:" + questId + ")");
        }

        for (var nl : currentConditionTree.collectLeaves()) {
            String value = nl.leaf().value();
            if (value == null || value.isEmpty()) continue;
            if (!nl.negated() && nl.leaf().type().equals("chronicles_quest")) {
                String name = chroniclesQuestName.isEmpty() ? value : chroniclesQuestName;
                out.add("§d• Chronicles Quest: " + name + " §8(chronicles_quest:" + value + ")");
                continue;
            }
            if (nl.leaf().type().equals("external")) {
                out.add((nl.negated() ? "§c• NOT External: " : "§e• External: ") +
                        ArchiveScreen.externalProgressLabel(value));
                continue;
            }
            String typeLabel = getConditionTypeLabel(nl.leaf().type());
            String prefix = (nl.negated() ? "§c• NOT " : "§e• ") + typeLabel + ": ";
            out.add(conditionLine(prefix, value));
        }
        return out;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        if (idBox != null) updateSavedValues();

        buildCategoryList();
        computeLayout();

        int x = fieldX;
        int w = fieldW;
        String termLabel = termW >= 72 ? "TERMINAL >_" : "TERM >_";

        idBox = new EditBox(this.font, x, idY, w, rowH, Component.empty());
        idBox.setHint(Component.literal("§6Permanent ID (e.g. log_001)"));
        idBox.setValue(savedId);
        idBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(idBox);

        titleBox = new EditBox(this.font, x, titleY, w, rowH, Component.empty());
        titleBox.setHint(Component.literal("§8Title..."));
        titleBox.setValue(savedTitle);
        titleBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(titleBox);

        this.addRenderableWidget(Button.builder(Component.literal(termLabel), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "ENTRY_TITLE", this.savedTitle, (val) -> {
                this.savedTitle = val;
                this.titleBox.setValue(val);
            }));
        }).bounds(termX, titleY, termW, rowH)
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
        }).bounds(x, catY, w - 30, rowH).build());

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
        }).bounds(x + w - 28, catY, 28, rowH)
                .tooltip(Tooltip.create(Component.literal("Make a New Category")))
                .build());

        iconBox = new EditBox(this.font, x, iconY, w, rowH, Component.empty());
        iconBox.setMaxLength(512);
        iconBox.setHint(Component.literal("§8Icon (minecraft:apple)"));
        iconBox.setValue(savedIcon);
        iconBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(iconBox);

        voiceLineBox = new EditBox(this.font, x, voiceY, w, rowH, Component.empty());
        voiceLineBox.setHint(Component.literal("§dVoice (phoenix_archive:voice.filename)"));
        voiceLineBox.setValue(savedVoiceLine);
        voiceLineBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(voiceLineBox);

        contentBox = new MultiLineEditBox(this.font, x, contentY, w, contentH, Component.empty(),
                Component.empty());
        contentBox.setValue(savedContent);
        contentBox.setValueListener(v -> isDirty = true);
        this.addRenderableWidget(contentBox);

        lockedContentBox = new MultiLineEditBox(this.font, x, lockedY, w, lockedH, Component.empty(),
                Component.empty());
        lockedContentBox.setValue(savedLockedContent);
        lockedContentBox.setValueListener(v -> isDirty = true);
        this.addRenderableWidget(lockedContentBox);

        this.addRenderableWidget(Button.builder(Component.literal(termLabel), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "UNLOCKED_DATA", this.savedContent, (val) -> {
                this.savedContent = val;
                this.contentBox.setValue(val);
            }));
        }).bounds(termX, contentY, termW, rowH)
                .tooltip(Tooltip.create(Component.literal("Open Terminal Editor for Unlocked Content")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal(termLabel), b -> {
            updateSavedValues();
            this.minecraft
                    .setScreen(new TerminalInputScreen(this, "ENCRYPTED_SIGNAL", this.savedLockedContent, (val) -> {
                        this.savedLockedContent = val;
                        this.lockedContentBox.setValue(val);
                    }));
        }).bounds(termX, lockedY, termW, rowH)
                .tooltip(Tooltip.create(Component.literal("Open Terminal Editor for Locked Content")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("§6EDIT_LOGIC"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ConditionTunerScreen(this, this.currentConditionTree));
        }).bounds(rightX, catY, rightW, rowH)
                .tooltip(Tooltip.create(Component.literal("Add/Remove Entry Conditions")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("SELECT_QUEST"), b -> {
            updateSavedValues();
            openQuestSelector();
        }).bounds(rightX, titleY, rightW, rowH)
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
            }).bounds(rightX, idY + (rowH - 16) / 2, rightW, 16)
                    .tooltip(Tooltip.create(Component.literal("Clear Selected Quest")))
                    .build());
        }

        shaderBox = new EditBox(this.font, rightX, shaderBoxY, rightW - 21, 16, Component.empty());
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
        }).bounds(rightX + rightW - 18, shaderBoxY, 18, 16)
                .tooltip(Tooltip.create(Component.literal("Browse Shaders")))
                .build());

        this.addRenderableWidget(Button.builder(
                Component.literal(savedHidden ? "§eHidden From List: ON" : "§8Hidden From List: OFF"),
                b -> {
                    savedHidden = !savedHidden;
                    isDirty = true;
                    this.init(this.minecraft, this.width, this.height);
                })
                .bounds(x, hiddenY, w, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "When ON, this entry is left out of the sidebar entirely (instead of showing\n" +
                                "greyed-out with its locked text) until the unlock check below passes.")))
                .build());

        String unlockLabel = savedHiddenUntilId.isEmpty() ? "§8(this entry's own unlock)" : "§f" + savedHiddenUntilId;
        int unlockBtnW = savedHiddenUntilId.isEmpty() ? w : w - 22;
        this.addRenderableWidget(Button.builder(Component.literal("§6UNLOCK_CHECK: " + unlockLabel), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ArchiveEntryPickerScreen(this, savedId, id -> {
                this.savedHiddenUntilId = id;
                this.isDirty = true;
                this.init(this.minecraft, this.width, this.height);
            }));
        }).bounds(x, unlockY, unlockBtnW, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Which entry's unlock state gates this one appearing (used with Hidden From " +
                                "List above). Defaults to this entry's own unlock if none is picked.")))
                .build());

        if (!savedHiddenUntilId.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("§4X"), b -> {
                savedHiddenUntilId = "";
                isDirty = true;
                this.init(this.minecraft, this.width, this.height);
            }).bounds(x + w - 19, unlockY, 19, 16)
                    .tooltip(Tooltip.create(Component.literal("Clear (gate on this entry's own unlock)")))
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("§2SAVE PACKET"), b -> saveEntry())
                .bounds(x, saveY, w, rowH)
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

        graphics.drawString(this.font, "§6TRIGGER_LOGIC:", rightX, triggerY, ArchivePalette.TERM_BRIGHT);
        int lineW = Math.min(110, this.width - MARGIN - rightX);
        graphics.fill(rightX, triggerY + 10, rightX + lineW, triggerY + 11,
                ArchivePalette.withAlpha(ArchivePalette.TERM_BRIGHT, 0x44));

        int statusY = statusStartY;
        for (String entry : statusEntries) {
            for (FormattedCharSequence line : wrapStatus(entry)) {
                graphics.drawString(this.font, line, rightX, statusY, ArchivePalette.TEXT_DIM);
                statusY += 10;
            }
        }

        graphics.drawString(this.font, "§6BG_SHADER:", rightX, shaderLabelY, ArchivePalette.TERM_BRIGHT);

        // Header tag: only drawn when it fits to the left of the ID box
        String tag = "> " + ArchiveConfigs.INSTANCE.general.mainMenuName + "Lore_Dev";
        if (10 + this.font.width(tag) < fieldX - 4) {
            graphics.drawString(this.font, tag, 10, idY + (rowH - 8) / 2, ArchivePalette.TERM);
        }
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

    private String conditionLine(String prefix, String id) {
        if (id == null || id.isEmpty()) return prefix;
        String displayName = prettifyId(id);
        String finalName = displayName.length() > 20 ? displayName.substring(0, 18) + ".." : displayName;
        return prefix + "§f" + finalName;
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
        for (var quest : ChroniclesQuestBridge.allQuests()) {
            out.add(new QuestSelectorScreen.PickableQuest(true, quest.id(), quest.title(), false));
        }
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
