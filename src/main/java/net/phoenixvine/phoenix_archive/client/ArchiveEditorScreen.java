package net.phoenixvine.phoenix_archive.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.phoenix_archive.api.LoreDataLoader;
import net.phoenixvine.phoenix_archive.api.LoreEntry;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;



public class ArchiveEditorScreen extends Screen {
    private EditBox titleBox, categoryBox, iconBox, voiceLineBox, idBox;
    private MultiLineEditBox contentBox, lockedContentBox;
    private ResourceLocation selectedEntryId;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Core Data
    public String savedTitle = "";
    public String savedCategory = "";
    public String savedIcon = "";
    public String savedContent = "";
    public String savedId = ""; // New field
    public String savedLockedContent = "";
    public String savedVoiceLine = "";
    public long questId = 0;
    public String questName = "None Selected";
    private int currentOrder = -1;

    // The Dynamic Truth: Any condition from any mod lives here
    public Map<String, String> currentConditions = new HashMap<>();

    public ArchiveEditorScreen() {
        super(Component.literal("Archive Editor"));
    }

    public ArchiveEditorScreen(LoreEntry existingEntry, ResourceLocation id) {
        super(Component.literal("Archive Editor"));
        this.selectedEntryId = id;

        if (existingEntry != null) {
            this.savedId = existingEntry.id(); // Load the internal ID
            this.savedTitle = existingEntry.title();
            this.savedCategory = existingEntry.category();
            this.savedIcon = existingEntry.iconItem();
            this.savedContent = existingEntry.content().replace("§", "&");
            this.savedLockedContent = existingEntry.lockedContent() != null ?
                    existingEntry.lockedContent().replace("§", "&") : "";
            this.savedVoiceLine = existingEntry.voiceLine() != null ?
                    existingEntry.voiceLine() : "";
            this.questId = existingEntry.questId();
            this.currentOrder = existingEntry.order();

            if (existingEntry.getConditions() != null) {
                this.currentConditions = new HashMap<>(existingEntry.getConditions());
            }
        }
    }

    // ADD THIS METHOD: This allows GTLinkScreen to work!
    public Map<String, String> getConditions() {
        return this.currentConditions;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int rightX = x + 205;

        // --- ID Field (The permanent key) ---
        idBox = new EditBox(this.font, x, 10, 200, 20, Component.empty());
        idBox.setHint(Component.literal("§6Permanent ID (e.g. log_001)"));
        idBox.setValue(savedId);
        // If editing an existing entry, we should probably warn about changing the ID
        this.addRenderableWidget(idBox);

        titleBox = new EditBox(this.font, x, 35, 200, 20, Component.empty());
        titleBox.setHint(Component.literal("§8Title..."));
        titleBox.setValue(savedTitle);

        categoryBox = new EditBox(this.font, x, 60, 200, 20, Component.empty());
        categoryBox.setHint(Component.literal("§8Category..."));
        categoryBox.setValue(savedCategory);

        iconBox = new EditBox(this.font, x, 85, 200, 20, Component.empty());
        iconBox.setHint(Component.literal("§8Icon (minecraft:apple)"));
        iconBox.setValue(savedIcon);

        voiceLineBox = new EditBox(this.font, x, 110, 200, 20, Component.empty());
        voiceLineBox.setHint(Component.literal("§dVoice (phoenix_archive:voice.filename)"));
        voiceLineBox.setValue(savedVoiceLine);
        voiceLineBox.setMaxLength(256);

        contentBox = new MultiLineEditBox(this.font, x, 135, 200, 45, Component.literal("§8Unlocked Content..."), Component.empty());
        contentBox.setValue(savedContent);

        lockedContentBox = new MultiLineEditBox(this.font, x, 185, 200, 25, Component.literal("§cEncrypted Content..."), Component.empty());
        lockedContentBox.setValue(savedLockedContent);

        // --- Widgets ---
        this.addRenderableWidget(Button.builder(Component.literal("FTB_QUESTS"), b -> {
            updateSavedValues();
            openQuestSelector();
        }).bounds(rightX, 35, 95, 20).build());


        // Inside init() of ArchiveEditorScreen.java


        int btnWidth = 80; // Enough space for "TERMINAL >_"

// --- Button for Unlocked Content (Left of contentBox) ---
// Inside ArchiveEditorScreen.java init()
        this.addRenderableWidget(Button.builder(Component.literal("TERMINAL >_"), b -> {
            updateSavedValues(); // Sync current box text to memory
            this.minecraft.setScreen(new TerminalInputScreen(this, "UNLOCKED_DATA", this.savedContent, (val) -> {
                this.savedContent = val;
                this.contentBox.setValue(val);
            }));
        }).bounds(x - 85, 135, btnWidth, 20).build());

// --- Button for Encrypted Content (Left of lockedContentBox) ---
        this.addRenderableWidget(Button.builder(Component.literal("TERMINAL >_"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "ENCRYPTED_SIGNAL", lockedContentBox.getValue(), (val) -> {
                this.savedLockedContent = val;
                this.lockedContentBox.setValue(val);
            }));
        }).bounds(x - 85, 185, btnWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§6EDIT_LOGIC"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ConditionTunerScreen(this, this.currentConditions));
        }).bounds(rightX, 60, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§2SAVE PACKET"), b -> saveEntry())
                .bounds(x, 215, 200, 20).build());

        this.addRenderableWidget(titleBox);
        this.addRenderableWidget(categoryBox);
        this.addRenderableWidget(iconBox);
        this.addRenderableWidget(voiceLineBox);
        this.addRenderableWidget(contentBox);
        this.addRenderableWidget(lockedContentBox);
    }

    private void updateSavedValues() {
        this.savedId = idBox.getValue();
        this.savedTitle = titleBox.getValue();
        this.savedCategory = categoryBox.getValue();
        this.savedIcon = iconBox.getValue();
        this.savedVoiceLine = voiceLineBox.getValue();
        this.savedContent = contentBox.getValue();
        this.savedLockedContent = lockedContentBox.getValue();
    }

    private void saveEntry() {
        updateSavedValues();

        // If ID is empty, auto-generate it from the title for fluidity
        if (savedId.isEmpty()) {
            savedId = savedTitle.toLowerCase().replaceAll("[^a-z0-9]", "_");
        }

        int finalOrder = (this.currentOrder != -1) ? this.currentOrder : LoreDataLoader.LORE_ENTRIES.size();

        LoreEntry entry = new LoreEntry(
                savedId, // Saved as the permanent internal ID
                savedTitle,
                savedCategory,
                savedContent.replace("&", "§"),
                savedIcon,
                (int)questId, // Casting to int for our record
                savedLockedContent.replace("&", "§"),
                savedVoiceLine,
                new HashMap<>(currentConditions),
                finalOrder
        );

        // Filename is still based on the selected ID or the title for the actual .json file
        String fileName = selectedEntryId != null ?
                selectedEntryId.getPath() :
                savedTitle.toLowerCase().replaceAll("[^a-z0-9]", "_");

        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config/phoenix_archive/lore");

        try {
            File dir = path.toFile();
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter writer = new FileWriter(new File(dir, fileName + ".json"))) {
                GSON.toJson(entry, writer);
            }

            // Reload into memory
            ResourceLocation resId = new ResourceLocation("phoenix_archive", fileName);
            LoreDataLoader.LORE_ENTRIES.put(resId, entry);
            reSortGlobalMap();

            this.minecraft.setScreen(new ArchiveScreen());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void reSortGlobalMap() {
        var sortedList = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().order()))
                .toList();

        Map<ResourceLocation, LoreEntry> sortedMap = new LinkedHashMap<>();
        for (var e : sortedList) sortedMap.put(e.getKey(), e.getValue());
        LoreDataLoader.LORE_ENTRIES = sortedMap;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        int rightX = (this.width / 2 - 100) + 205;
        graphics.drawString(this.font, "§6TRIGGER_LOGIC:", rightX, 85, 0xFFFFFF);
        graphics.fill(rightX, 95, rightX + 110, 96, 0x44FFFFFF);

        int statusY = 100;

        // --- Dynamic Render Loop ---
        // This makes ANY condition key (mana, temperature, etc.) show up in the preview
        if (currentConditions.isEmpty() && questId == 0) {
            graphics.drawString(this.font, "§8(Manual Only)", rightX, statusY, 0xFFFFFF);
        } else {
            if (questId != 0) {
                graphics.drawString(this.font, "§b• Q: " + questName, rightX, statusY, 0xFFFFFF);
                statusY += 10;
            }

            for (Map.Entry<String, String> cond : currentConditions.entrySet()) {
                String prefix = "§e• " + cond.getKey().toUpperCase() + ": ";
                renderCondition(graphics, prefix, cond.getValue(), rightX, statusY);
                statusY += 10;
            }
        }

        graphics.drawString(this.font, "> PHOENIX_OS Lore_Dev", 10, 10, 0x00FF00);
    }

    private void renderCondition(GuiGraphics g, String prefix, String id, int x, int y) {
        if (id.isEmpty()) return;
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
    // Inside ArchiveEditorScreen.java

}