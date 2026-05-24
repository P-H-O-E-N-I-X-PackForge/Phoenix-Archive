package net.phoenixvine.phoenix_archive.client;

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
import net.phoenixvine.phoenix_archive.api.CategoryRegistry;
import net.phoenixvine.phoenix_archive.api.LoreDataLoader;
import net.phoenixvine.phoenix_archive.api.LoreEntry;
import net.phoenixvine.phoenix_archive.config.ArchiveConfigs;

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

    private EditBox titleBox, iconBox, voiceLineBox, idBox;
    private MultiLineEditBox contentBox, lockedContentBox;

    /**
     * Flat list of available category IDs, ordered by DFS tree walk so that
     * children appear immediately after their parent. Cycling through this list
     * gives the author a sense of the hierarchy.
     */
    private List<String> categoryList = new ArrayList<>();
    private int categoryIndex = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String savedTitle = "";
    public String savedIcon = "";
    public String savedContent = "";
    public String savedId = "";
    public String savedLockedContent = "";
    public String savedVoiceLine = "";
    public long questId = 0;
    public String questName = "None Selected";
    private int currentOrder = -1;
    public Map<String, String> currentConditions = new HashMap<>();

    /** True once the user has changed anything since the screen was opened. */
    private boolean isDirty = false;
    /** Suppresses the unsaved-changes prompt when we're intentionally saving or discarding. */
    private boolean suppressDirtyGuard = false;

    public ArchiveEditorScreen(LoreEntry existing, String defaultCategory) {
        super(Component.literal("Archive Editor"));
        this.editingEntry = existing;
        this.initialCategory = (defaultCategory == null || defaultCategory.isEmpty()) ? "GENERAL" : defaultCategory;

        if (existing != null) {
            setupFromEntry(existing);
        }
    }

    private void setupFromEntry(LoreEntry entry) {
        this.savedId = entry.id();
        this.savedTitle = entry.title();
        this.savedIcon = entry.iconItem() != null ? entry.iconItem() : "";
        this.savedContent = entry.content().replace("§", "&");
        this.savedLockedContent = entry.lockedContent() != null ? entry.lockedContent().replace("§", "&") : "";
        this.savedVoiceLine = entry.voiceLine() != null ? entry.voiceLine() : "";
        this.questId = entry.questId();
        this.currentOrder = entry.order();
        this.currentConditions = new HashMap<>(entry.getConditions());
    }

    /** Returns the category string currently selected by the cycle button. */
    private String getCurrentCategory() {
        if (categoryList.isEmpty()) return initialCategory;
        return categoryList.get(Math.min(categoryIndex, categoryList.size() - 1));
    }

    public Map<String, String> getConditions() {
        return this.currentConditions;
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        if (idBox != null) updateSavedValues();

        buildCategoryList();

        int x = this.width / 2 - 100;
        int rightX = x + 205;

        // 1. ID Field
        idBox = new EditBox(this.font, x, 10, 200, 20, Component.empty());
        idBox.setHint(Component.literal("§6Permanent ID (e.g. log_001)"));
        idBox.setValue(savedId);
        idBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(idBox);

        // 2. Title Field
        titleBox = new EditBox(this.font, x, 35, 200, 20, Component.empty());
        titleBox.setHint(Component.literal("§8Title..."));
        titleBox.setValue(savedTitle);
        titleBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(titleBox);

        // 3. Category cycle button — label shows indentation depth prefix
        String currentCat = getCurrentCategory();
        int currentDepth = CategoryRegistry.getDepth(currentCat);
        String depthPrefix = "  ".repeat(currentDepth);  // two spaces per level
        String cycleLbl = "§8< §7" + depthPrefix + currentCat + " §8>";

        this.addRenderableWidget(Button.builder(Component.literal(cycleLbl), b -> {
            updateSavedValues();
            // Cycle through all categories except the last sentinel ("+ NEW")
            categoryIndex = (categoryIndex + 1) % Math.max(1, categoryList.size() - 1);
            this.init(this.minecraft, this.width, this.height);
        }).bounds(x, 60, 170, 20).build());

        // "+ NEW CATEGORY" button
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

        // 4. Icon & Voice
        iconBox = new EditBox(this.font, x, 85, 200, 20, Component.empty());
        iconBox.setHint(Component.literal("§8Icon (minecraft:apple)"));
        iconBox.setValue(savedIcon);
        iconBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(iconBox);

        voiceLineBox = new EditBox(this.font, x, 110, 200, 20, Component.empty());
        voiceLineBox.setHint(Component.literal("§dVoice (phoenix_archive:voice.filename)"));
        voiceLineBox.setValue(savedVoiceLine);
        voiceLineBox.setResponder(v -> isDirty = true);
        this.addRenderableWidget(voiceLineBox);

        // 5. Content Boxes
        contentBox = new MultiLineEditBox(this.font, x, 135, 200, 45, Component.empty(), Component.empty());
        contentBox.setValue(savedContent);
        contentBox.setValueListener(v -> isDirty = true);
        this.addRenderableWidget(contentBox);

        lockedContentBox = new MultiLineEditBox(this.font, x, 185, 200, 25, Component.empty(), Component.empty());
        lockedContentBox.setValue(savedLockedContent);
        lockedContentBox.setValueListener(v -> isDirty = true);
        this.addRenderableWidget(lockedContentBox);

        // 6. Terminal Input Buttons
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

        // 7. Logic Buttons
        this.addRenderableWidget(Button.builder(Component.literal("§6EDIT_LOGIC"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ConditionTunerScreen(this, this.currentConditions));
        }).bounds(rightX, 60, 95, 20)
                .tooltip(Tooltip.create(Component.literal("Add/Remove Entry Conditions")))
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("FTB_QUESTS"), b -> {
            updateSavedValues();
            openQuestSelector();
        }).bounds(rightX, 35, 95, 20)
                .tooltip(Tooltip.create(Component.literal("Open Quest Selector")))
                .build());

        if (questId != 0) {
            this.addRenderableWidget(Button.builder(Component.literal("§4CLEAR QUEST"), b -> {
                this.questId = 0;
                this.questName = "None Selected";
                this.init(this.minecraft, this.width, this.height);
            }).bounds(rightX, 17, 95, 16)
                    .tooltip(Tooltip.create(Component.literal("Clear Selected Quest")))
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("§2SAVE PACKET"), b -> saveEntry())
                .bounds(x, 215, 200, 20)
                .tooltip(Tooltip.create(Component.literal("Save Archive Entry")))
                .build());
    }

    // -------------------------------------------------------------------------
    // Category list building (tree-ordered)
    // -------------------------------------------------------------------------

    /**
     * Builds {@link #categoryList} in DFS tree order (parent before its children,
     * siblings sorted by weight). This means that when the author cycles through
     * categories the hierarchy is apparent from the label indentation in the button.
     *
     * <p>
     * A sentinel {@code "+ NEW"} is appended at the end so that the cycle button
     * can skip it (it cycles up to {@code size - 1}).
     * </p>
     */
    private void buildCategoryList() {
        Set<String> seen = new HashSet<>();
        List<String> ordered = new ArrayList<>();

        // Collect all known IDs (registered + referenced by entries)
        Set<String> allIds = new HashSet<>(CategoryRegistry.getRegisteredIds());
        LoreDataLoader.LORE_ENTRIES.values().forEach(e -> allIds.add(e.category().toUpperCase()));
        if (allIds.isEmpty()) allIds.add("GENERAL");

        // Walk tree depth-first
        walkCategoryTree(null, allIds, seen, ordered);

        // Append any orphaned IDs (unregistered categories referenced by entries)
        for (String id : allIds) {
            if (!seen.contains(id)) {
                ordered.add(id);
                seen.add(id);
            }
        }

        // Append sentinel (skipped by cycle logic)
        ordered.add("+ NEW");

        this.categoryList = ordered;

        // Determine which index should be selected
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

        // Include unregistered IDs that logically belong here
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

    // -------------------------------------------------------------------------
    // Save helpers
    // -------------------------------------------------------------------------

    private void saveCategoryToDisk(String categoryId) {
        try {
            var def = new net.phoenixvine.phoenix_archive.api.CategoryDefinition(
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
    public void onClose() {
        if (isDirty && !suppressDirtyGuard) {
            this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                    confirmed -> {
                        suppressDirtyGuard = true;
                        if (confirmed) saveEntry();
                        else this.minecraft.setScreen(new ArchiveScreen());
                    },
                    Component.literal("§6[UNSAVED_CHANGES]"),
                    Component.literal("You have unsaved changes. Save before leaving?")));
        } else {
            super.onClose();
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
                savedContent.replace("&", "§"),
                savedIcon,
                (int) questId,
                savedLockedContent.replace("&", "§"),
                savedVoiceLine,
                new HashMap<>(currentConditions),
                finalOrder);

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

            boolean hasConditions = !entry.getConditions().isEmpty();
            boolean hasQuest = entry.questId() != 0;
            if (hasConditions || hasQuest) {
                ArchiveScreen.CLIENT_LORE_CACHE.remove("lore_unlocked:" + savedId);
            }

            this.minecraft.setScreen(new ArchiveScreen());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        int rightX = (this.width / 2 - 100) + 205;
        graphics.drawString(this.font, "§6TRIGGER_LOGIC:", rightX, 85, 0xFFFFFF);
        graphics.fill(rightX, 95, rightX + 110, 96, 0x44FFFFFF);

        int statusY = 100;

        boolean hasAnything = questId != 0;
        for (Map.Entry<String, String> cond : currentConditions.entrySet()) {
            if (!cond.getValue().isEmpty()) {
                hasAnything = true;
                break;
            }
        }

        if (!hasAnything) {
            graphics.drawString(this.font, "§8(Manual Only)", rightX, statusY, 0xFFFFFF);
        } else {
            if (questId != 0) {
                graphics.drawString(this.font, "§b• Quest: " + questName, rightX, statusY, 0xFFFFFF);
                statusY += 10;
            }

            for (Map.Entry<String, String> cond : currentConditions.entrySet()) {
                if (cond.getValue() == null || cond.getValue().isEmpty()) continue;
                String typeLabel = getConditionTypeLabel(cond.getKey());
                renderCondition(graphics, "§e• " + typeLabel + ": ", cond.getValue(), rightX, statusY);
                statusY += 10;
            }
        }

        graphics.drawString(this.font, "> " + ArchiveConfigs.INSTANCE.general.mainMenuName + "Lore_Dev",
                10, 10, 0x00FF00);
    }

    private String getConditionTypeLabel(String key) {
        return switch (key.toLowerCase()) {
            case "dimension" -> "Dimension";
            case "biome" -> "Biome";
            case "machine" -> "Place Machine";
            case "item" -> "Have Item";
            case "wearing" -> "Wear Item";
            case "suit_event" -> "Event";
            default -> prettifyId(key);
        };
    }

    private void renderCondition(GuiGraphics g, String prefix, String id, int x, int y) {
        if (id == null || id.isEmpty()) return;
        String displayName = prettifyId(id);
        String finalName = displayName.length() > 20 ? displayName.substring(0, 18) + ".." : displayName;
        g.drawString(this.font, prefix + "§f" + finalName, x, y, 0xFFFFFF);
    }

    private String prettifyId(String id) {
        String path = id.contains(":") ? id.split(":")[1] : id;
        return Arrays.stream(path.replace("_", " ").split(" "))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(id);
    }

    private void openQuestSelector() {
        if (ModList.get().isLoaded("ftbquests")) {
            try {
                var questFile = dev.ftb.mods.ftbquests.api.FTBQuestsAPI.api().getQuestFile(true);
                List<dev.ftb.mods.ftbquests.quest.Quest> allQuests = new ArrayList<>();
                for (var chapter : questFile.getAllChapters()) {
                    allQuests.addAll(chapter.getQuests());
                }
                this.minecraft.setScreen(new QuestSelectorScreen(this, allQuests));
            } catch (Exception ignored) {}
        }
    }

    private void updateSavedValues() {
        if (idBox != null) this.savedId = idBox.getValue();
        if (titleBox != null) this.savedTitle = titleBox.getValue();
        if (iconBox != null) this.savedIcon = iconBox.getValue();
        if (voiceLineBox != null) this.savedVoiceLine = voiceLineBox.getValue();
        if (contentBox != null) this.savedContent = contentBox.getValue();
        if (lockedContentBox != null) this.savedLockedContent = lockedContentBox.getValue();
    }
}
