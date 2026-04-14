package net.phoenix.phoenix_archive.client;

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
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenix.phoenix_archive.api.LoreDataLoader;
import net.phoenix.phoenix_archive.api.LoreEntry;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchiveEditorScreen extends Screen {
    private EditBox titleBox, categoryBox, iconBox;
    private MultiLineEditBox contentBox, lockedContentBox;
    private ResourceLocation selectedEntryId;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Data values
    public String questName = "None Selected"; // <--- Add this back!
    public String savedTitle = "";
    public String savedCategory = "";
    public String savedIcon = "";
    public String savedContent = "";
    public String savedLockedContent = "";
    public long questId = 0;

    // Multi-Trigger values
    public String savedMachineId = "";
    public String savedDimId = "";
    public String savedBiomeId = "";
    public String savedEntityId = "";

    public ArchiveEditorScreen() {
        super(Component.literal("Archive Editor"));
    }

    public ArchiveEditorScreen(LoreEntry existingEntry, ResourceLocation id) {
        this();
        this.selectedEntryId = id;
        if (existingEntry != null) {
            this.savedTitle = existingEntry.title();
            this.savedCategory = existingEntry.category();
            this.savedIcon = existingEntry.iconItem();
            this.savedContent = existingEntry.content().replace("§", "&");
            this.savedLockedContent = existingEntry.lockedContent() != null ? existingEntry.lockedContent().replace("§", "&") : "";
            this.questId = existingEntry.questId();

            // LOAD FROM CONDITIONS MAP
            Map<String, String> conds = existingEntry.getConditions();
            if (conds != null) {
                this.savedMachineId = conds.getOrDefault("machine", "");
                this.savedDimId = conds.getOrDefault("dimension", "");
                this.savedBiomeId = conds.getOrDefault("biome", "");
                this.savedEntityId = conds.getOrDefault("entity", "");
            }
        }
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int rightX = x + 205;

        // --- Basic Info (Middle) ---
        titleBox = new EditBox(this.font, x, 35, 200, 20, Component.empty());
        titleBox.setHint(Component.literal("§8Enter Title..."));
        titleBox.setValue(savedTitle);

        categoryBox = new EditBox(this.font, x, 60, 200, 20, Component.empty());
        categoryBox.setHint(Component.literal("§8Enter Category..."));
        categoryBox.setValue(savedCategory);

        iconBox = new EditBox(this.font, x, 85, 200, 20, Component.empty());
        iconBox.setHint(Component.literal("§8Icon (minecraft:apple)"));
        iconBox.setValue(savedIcon);

        contentBox = new MultiLineEditBox(this.font, x, 115, 200, 50, Component.literal("§8Unlocked Content..."), Component.empty());
        contentBox.setValue(savedContent);

        lockedContentBox = new MultiLineEditBox(this.font, x, 175, 200, 30, Component.literal("§cEncrypted Content..."), Component.empty());
        lockedContentBox.setValue(savedLockedContent);

        // --- Integrated Tools (Left/Right) ---

        // Universal Terminal for main content
        this.addRenderableWidget(Button.builder(Component.literal("TERMINAL"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new TerminalInputScreen(this, "CONTENT_BUFFER", savedContent, (val) -> this.savedContent = val));
        }).bounds(x - 90, 35, 85, 20).build());

        // FTB Quests Integration
        this.addRenderableWidget(Button.builder(Component.literal("FTB_QUESTS"), b -> {
            updateSavedValues();
            openQuestSelector();
        }).bounds(rightX, 35, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§6EDIT_LOGIC"), b -> {
            updateSavedValues();
            this.minecraft.setScreen(new ConditionTunerScreen(this));
        }).bounds(rightX, 60, 95, 20).build());


        // Save
        this.addRenderableWidget(Button.builder(Component.literal("§2SAVE PACKET"), b -> saveEntry())
                .bounds(x, 210, 200, 20).build());

        this.addRenderableWidget(titleBox);
        this.addRenderableWidget(categoryBox);
        this.addRenderableWidget(iconBox);
        this.addRenderableWidget(contentBox);
        this.addRenderableWidget(lockedContentBox);
    }

    private void updateSavedValues() {
        this.savedTitle = titleBox.getValue();
        this.savedCategory = categoryBox.getValue();
        this.savedIcon = iconBox.getValue();
        this.savedContent = contentBox.getValue();
        this.savedLockedContent = lockedContentBox.getValue();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        int x = this.width / 2 - 100;
        int rightX = x + 205;

        graphics.drawString(this.font, "§6TRIGGER_LOGIC:", rightX, 90, 0xFFFFFF);
        graphics.fill(rightX, 100, rightX + 110, 101, 0x44FFFFFF); // Widened line

        int statusY = 105;
        if (questId != 0) {
            graphics.drawString(this.font, "§b• Q: " + questName, rightX, statusY, 0xFFFFFF);
            statusY += 10;
        }

        // Render each condition if it exists
        renderCondition(graphics, "§a• GT: ", savedMachineId, rightX, statusY);
        if (!savedMachineId.isEmpty()) statusY += 10;

        renderCondition(graphics, "§d• DIM: ", savedDimId, rightX, statusY);
        if (!savedDimId.isEmpty()) statusY += 10;

        renderCondition(graphics, "§e• BIO: ", savedBiomeId, rightX, statusY);

        graphics.drawString(this.font, "> PHOENIX_OS Lore_Dev", 10, 10, 0x00FF00);
    }

    private void renderCondition(GuiGraphics g, String prefix, String id, int x, int y) {
        if (id.isEmpty()) return;

        String displayName = id;
        try {
            if (prefix.contains("GT")) {
                displayName = Component.translatable(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)).getDescriptionId()).getString();
            } else if (prefix.contains("BIO")) {
                displayName = Component.translatable("biome." + id.replace(":", ".")).getString();
            } else {
                displayName = prettifyId(id);
            }
        } catch (Exception ignored) {}

        // Only shorten if the translated name is still too long
        String finalName = displayName.length() > 22 ? displayName.substring(0, 20) + ".." : displayName;
        g.drawString(this.font, prefix + finalName, x, y, 0xFFFFFF);
    }

    // Helper to turn "minecraft:the_nether" into "The Nether"
    private String prettifyId(String id) {
        String path = id.contains(":") ? id.split(":")[1] : id;
        String[] words = path.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private void renderStatus(GuiGraphics g, String text, int x, int y) {
        g.drawString(this.font, text, x, y, 0xFFFFFF);
    }

    private String shorten(String id) {
        String clean = id.contains(":") ? id.split(":")[1] : id;
        return clean.length() > 10 ? clean.substring(0, 10) + ".." : clean;
    }

    private void saveEntry() {
        updateSavedValues();

        // 1. Build the conditions map exactly how the Registry expects it
        Map<String, String> conditions = new HashMap<>();
        if (!savedMachineId.isEmpty()) conditions.put("machine", savedMachineId);
        if (!savedDimId.isEmpty()) conditions.put("dimension", savedDimId);
        if (!savedBiomeId.isEmpty()) conditions.put("biome", savedBiomeId);
        if (!savedEntityId.isEmpty()) conditions.put("entity", savedEntityId);

        // 2. Create the LoreEntry using the Map-based constructor
        LoreEntry entry = new LoreEntry(
                savedTitle,
                savedCategory,
                savedContent.replace("&", "§"),
                savedIcon,
                questId,
                savedLockedContent.replace("&", "§"),
                conditions // This is the nested map for TriggerRegistry
        );

        // 3. File Writing (RE-INDEXING FIX)
        // We want to preserve the existing "00_" prefix if we are editing an existing entry!
        String fileName = selectedEntryId != null ? selectedEntryId.getPath() : savedTitle.toLowerCase().replaceAll("[^a-z0-9]", "_");
        if (!fileName.endsWith(".json")) fileName += ".json";

        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config/phoenix_archive/lore");

        try {
            File dir = path.toFile();
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter writer = new FileWriter(new File(dir, fileName))) {
                GSON.toJson(entry, writer);
            }

            // Update the internal cache so the screen refreshes instantly
            LoreDataLoader.LORE_ENTRIES.put(new ResourceLocation("phoenix_archive", fileName.replace(".json", "")), entry);
            this.minecraft.setScreen(new ArchiveScreen());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void openQuestSelector() {
        List<dev.ftb.mods.ftbquests.quest.Quest> allQuests = new ArrayList<>();
        if (ModList.get().isLoaded("ftbquests")) {
            try {
                var questFile = dev.ftb.mods.ftbquests.api.FTBQuestsAPI.api().getQuestFile(true);
                for (var chapter : questFile.getAllChapters()) {
                    allQuests.addAll(chapter.getQuests());
                }
            } catch (Exception ignored) {}
        }
        this.minecraft.setScreen(new QuestSelectorScreen(this, allQuests));
    }
}