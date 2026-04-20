package net.phoenixvine.phoenix_archive.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.phoenix_archive.api.LoreDataLoader;
import net.phoenixvine.phoenix_archive.api.LoreEntry;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.phoenix_archive.config.ArchiveConfigs;
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

    // FIX #3: Store the selected category index separately from cycle button
    // so it doesn't drift on re-init
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

    public ArchiveEditorScreen(LoreEntry existing, String defaultCategory) {
        super(Component.literal("Archive Editor"));
        this.editingEntry = existing;
        this.initialCategory = (defaultCategory == null || defaultCategory.isEmpty()) ? "GENERAL" : defaultCategory;

        if (existing != null) {
            setupFromEntry(existing);
        } else {
            // For new entries, default to the passed category
            // categoryIndex will be set during init() once we have the list
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

    /** Returns current category string from index */
    private String getCurrentCategory() {
        if (categoryList.isEmpty()) return initialCategory;
        return categoryList.get(Math.min(categoryIndex, categoryList.size() - 1));
    }

    public Map<String, String> getConditions() {
        return this.currentConditions;
    }

    @Override
    protected void init() {
        // Save current field values before rebuilding widgets
        if (idBox != null) updateSavedValues();

        // FIX #3: Build category list once, keep index stable
        buildCategoryList();

        int x = this.width / 2 - 100;
        int rightX = x + 205;

        // 1. ID Field
        idBox = new EditBox(this.font, x, 10, 200, 20, Component.empty());
        idBox.setHint(Component.literal("§6Permanent ID (e.g. log_001)"));
        idBox.setValue(savedId);
        this.addRenderableWidget(idBox);

        // 2. Title Field
        titleBox = new EditBox(this.font, x, 35, 200, 20, Component.empty());
        titleBox.setHint(Component.literal("§8Title..."));
        titleBox.setValue(savedTitle);
        this.addRenderableWidget(titleBox);

        // 3. FIX #3: Manual category cycle button using index — no CycleButton that resets
        String currentCat = getCurrentCategory();
        this.addRenderableWidget(Button.builder(Component.literal("§8< §7" + currentCat + " §8>"), b -> {
            // This button cycles forward; right-click would cycle back but buttons only do left
            updateSavedValues();
            categoryIndex = (categoryIndex + 1) % (categoryList.size() - 1); // -1 to skip "+ NEW"
            this.init(this.minecraft, this.width, this.height);
        }).bounds(x, 60, 170, 20).build());

        // "+ NEW CATEGORY" button
        this.addRenderableWidget(Button.builder(Component.literal("§a+"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "NEW_CATEGORY", "", (res) -> {
                if (res != null && !res.isEmpty()) {
                    net.phoenixvine.phoenix_archive.api.CategoryRegistry.register(res, "", 50);
                    // Save new category to disk
                    saveCategoryToDisk(res);
                    // Rebuild list and jump to new category
                    buildCategoryList();
                    for (int i = 0; i < categoryList.size(); i++) {
                        if (categoryList.get(i).equalsIgnoreCase(res)) { categoryIndex = i; break; }
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
        this.addRenderableWidget(iconBox);

        voiceLineBox = new EditBox(this.font, x, 110, 200, 20, Component.empty());
        voiceLineBox.setHint(Component.literal("§dVoice (phoenix_archive:voice.filename)"));
        voiceLineBox.setValue(savedVoiceLine);
        this.addRenderableWidget(voiceLineBox);

        // 5. Content Boxes
        contentBox = new MultiLineEditBox(this.font, x, 135, 200, 45, Component.empty(), Component.empty());
        contentBox.setValue(savedContent);
        this.addRenderableWidget(contentBox);

        lockedContentBox = new MultiLineEditBox(this.font, x, 185, 200, 25, Component.empty(), Component.empty());
        lockedContentBox.setValue(savedLockedContent);
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
            this.minecraft.setScreen(new TerminalInputScreen(this, "ENCRYPTED_SIGNAL", this.savedLockedContent, (val) -> {
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

        // FTB Quests button
        this.addRenderableWidget(Button.builder(Component.literal("FTB_QUESTS"), b -> {
            updateSavedValues();
            openQuestSelector();
        }).bounds(rightX, 35, 95, 20)
                .tooltip(Tooltip.create(Component.literal("Open Quest Selector")))
                .build());

        // FIX #4: Clear quest button
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

    /** Build the list of available categories, without the "+ NEW" sentinel in the cycling range */
    private void buildCategoryList() {
        List<String> cats = new ArrayList<>();
        LoreDataLoader.LORE_ENTRIES.values().forEach(e -> {
            if (!cats.contains(e.category().toUpperCase())) cats.add(e.category().toUpperCase());
        });
        net.phoenixvine.phoenix_archive.api.CategoryRegistry.getRegisteredIds().forEach(id -> {
            if (!cats.contains(id)) cats.add(id);
        });
        if (cats.isEmpty()) cats.add("GENERAL");

        // Sync categoryIndex to current category value if possible
        String wantedCat = (editingEntry != null && savedTitle.equals(editingEntry.title()))
                ? editingEntry.category().toUpperCase()
                : (cats.contains(initialCategory.toUpperCase()) ? initialCategory.toUpperCase() : cats.get(0));

        this.categoryList = cats;

        // Only reset index on first load (idBox is null) or if list changed
        int foundIndex = cats.indexOf(wantedCat);
        if (foundIndex >= 0) {
            categoryIndex = foundIndex;
        } else if (categoryIndex >= cats.size()) {
            categoryIndex = 0;
        }
    }

    private void saveCategoryToDisk(String categoryId) {
        try {
            var def = new net.phoenixvine.phoenix_archive.api.CategoryDefinition(categoryId.toUpperCase(), "", 50);
            File file = new File("config/phoenix_archive/categories/" + categoryId.toLowerCase() + ".json");
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(def, writer);
            }
        } catch (Exception e) { e.printStackTrace(); }
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
                finalOrder
        );

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

            // FIX #8: If this is an existing entry being modified, send a packet to the server
            // to re-evaluate unlock status (so conditions can lock/unlock correctly)
            // We do this by triggering a refresh via a server-bound packet if one exists,
            // or by notifying via chat command. For now we flag dirty on the entry.
            // The server-side fix is in TriggerRegistry / ServerEvents.
            // Client side: clear the cached unlock for this entry so it re-evaluates on next sync
            boolean hasConditions = !entry.getConditions().isEmpty();
            boolean hasQuest = entry.questId() != 0;
            if (hasConditions || hasQuest) {
                // Clear local cache key so screen shows it as locked until server confirms
                ArchiveScreen.CLIENT_LORE_CACHE.remove("lore_unlocked:" + savedId);
            }

            this.minecraft.setScreen(new ArchiveScreen());
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        int rightX = (this.width / 2 - 100) + 205;
        graphics.drawString(this.font, "§6TRIGGER_LOGIC:", rightX, 85, 0xFFFFFF);
        graphics.fill(rightX, 95, rightX + 110, 96, 0x44FFFFFF);

        int statusY = 100;

        // FIX #5: Only show conditions that have non-empty values; special text per type
        boolean hasAnything = questId != 0;
        for (Map.Entry<String, String> cond : currentConditions.entrySet()) {
            if (!cond.getValue().isEmpty()) { hasAnything = true; break; }
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

        graphics.drawString(this.font, "> " + ArchiveConfigs.INSTANCE.general.mainMenuName + "Lore_Dev", 10, 10, 0x00FF00);
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