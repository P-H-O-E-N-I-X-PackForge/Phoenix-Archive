package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phoenix_archive.PhoenixArchive;
import net.phoenixvine.phoenix_archive.api.CategoryRegistry;
import net.phoenixvine.phoenix_archive.api.LoreDataLoader;
import net.phoenixvine.phoenix_archive.api.LoreEntry;
import net.phoenixvine.phoenix_archive.config.ArchiveConfigs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ArchiveScreen extends Screen {

    private LoreEntry selectedEntry = null;
    private boolean isEditMode = false;
    private int contentScrollOffset = 0;
    private int scrollOffset = 0;
    private String selectedCategory = "GENERAL";

    private CompoundTag lastPhoenixData;
    public static CompoundTag CLIENT_LORE_CACHE = new CompoundTag();

    private final Map<String, Boolean> collapsedCategories = new HashMap<>();
    private final Map<String, List<LoreEntry>> groupedEntries = new LinkedHashMap<>();

    private int tickCounter = 0;
    private SoundInstance currentVoice = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int guiX, guiY;
    private int guiWidth = 420;
    private int guiHeight = 240;

    public ArchiveScreen() {
        super(Component.literal(ArchiveConfigs.INSTANCE.general.mainMenuName));
        CategoryRegistry.loadFromDisk();
    }

    @Override
    protected void init() {
        super.init();
        this.guiX = (this.width - this.guiWidth) / 2;
        this.guiY = (this.height - this.guiHeight) / 2;

        refreshGroups();

        int sidebarWidth = 140;
        int contentXOffset = sidebarWidth + 20;
        boolean isOp = this.minecraft != null && this.minecraft.player != null &&
                this.minecraft.player.hasPermissions(2);

        if (selectedEntry != null && isLoreUnlockedClient(selectedEntry) && selectedEntry.voiceLine() != null &&
                !selectedEntry.voiceLine().isEmpty()) {
            this.addRenderableWidget(
                    Button.builder(Component.literal("▶ PLAY VOICE"), b -> playLoreVoice(selectedEntry.voiceLine()))
                            .bounds(guiX + contentXOffset, guiY + guiHeight - 25, 100, 16)
                            .tooltip(Tooltip.create(Component.literal("Play Voice Entry")))
                            .build());
        }

        if (isOp) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(isEditMode ? "§c[EDIT: ON]" : "§7[EDIT: OFF]"),
                    b -> {
                        this.isEditMode = !this.isEditMode;
                        this.minecraft.tell(() -> this.init(this.minecraft, this.width, this.height));
                    }).bounds(guiX + guiWidth - 75, guiY + 6, 70, 14)
                    .tooltip(Tooltip.create(Component.literal("Toggle Editing Mode")))
                    .build());

            if (isEditMode) {
                this.addRenderableWidget(Button.builder(Component.literal("New Category"),
                        b -> this.minecraft.setScreen(new CategoryManagementScreen(this)))
                        .bounds(guiX + guiWidth - 220, guiY + 6, 72, 14)
                        .tooltip(Tooltip.create(Component.literal("Open New Entry Screen")))
                        .build());

                this.addRenderableWidget(Button.builder(Component.literal("New Entry"),
                        b -> this.minecraft.setScreen(new ArchiveEditorScreen(null, selectedCategory)))
                        .bounds(guiX + guiWidth - 146, guiY + 6, 64, 14)
                        .tooltip(Tooltip.create(Component.literal("Open New Entry Screen")))
                        .build());

                int ctrlX = guiX + guiWidth - 22;

                if (selectedEntry != null) {
                    this.addRenderableWidget(Button.builder(Component.literal("↑"),
                            b -> moveEntry(-1)).bounds(ctrlX, guiY + 30, 18, 18)
                            .tooltip(Tooltip.create(Component.literal("Move Entry Up")))
                            .build());
                    this.addRenderableWidget(Button.builder(Component.literal("↓"), b -> moveEntry(1))
                            .bounds(ctrlX, guiY + 50, 18, 18)
                            .tooltip(Tooltip.create(Component.literal("Move Entry Down")))
                            .build());
                    this.addRenderableWidget(Button.builder(Component.literal("§4X"), b -> openDeletePrompt())
                            .bounds(ctrlX, guiY + 70, 18, 18)
                            .tooltip(Tooltip.create(Component.literal("Delete Entry? Can not be undone.")))
                            .build());
                } else if (selectedCategory != null) {
                    this.addRenderableWidget(Button.builder(Component.literal("↑"), b -> shiftCategory(-1))
                            .tooltip(Tooltip.create(Component.literal("Move Category Up")))
                            .bounds(ctrlX, guiY + 30, 18, 18).build());

                    this.addRenderableWidget(Button.builder(Component.literal("↓"), b -> shiftCategory(1))
                            .tooltip(Tooltip.create(Component.literal("Move Category Down")))
                            .bounds(ctrlX, guiY + 50, 18, 18).build());

                    this.addRenderableWidget(
                            Button.builder(Component.literal("§4Delete"), b -> openDeleteCategoryPrompt())
                                    .tooltip(Tooltip.create(Component.literal("Delete this category (entries remain)")))
                                    .bounds(ctrlX - 36, guiY + 70, 55, 14).build());

                    this.addRenderableWidget(Button
                            .builder(Component.literal("§eEdit"),
                                    b -> this.minecraft.setScreen(new CategoryManagementScreen(this, selectedCategory)))
                            .tooltip(Tooltip.create(Component.literal("Edit this category's description and weight")))
                            .bounds(ctrlX - 36, guiY + 87, 55, 14).build());
                }
            }
        }
    }

    private void shiftCategory(int direction) {
        if (selectedCategory == null) return;

        List<String> sortedCats = new ArrayList<>(groupedEntries.keySet());
        int idx = sortedCats.indexOf(selectedCategory);
        if (idx == -1) return;

        int neighborIdx = idx + direction;
        if (neighborIdx < 0 || neighborIdx >= sortedCats.size()) return;

        String neighborCat = sortedCats.get(neighborIdx);

        int myWeight = CategoryRegistry.getWeight(selectedCategory);
        int neighborWeight = CategoryRegistry.getWeight(neighborCat);

        if (myWeight == neighborWeight) {
            neighborWeight = myWeight + (direction > 0 ? 1 : -1);
        }

        String myDesc = CategoryRegistry.getDescription(selectedCategory);
        String neighborDesc = CategoryRegistry.getDescription(neighborCat);

        CategoryRegistry.register(selectedCategory, myDesc, neighborWeight);
        CategoryRegistry.register(neighborCat, neighborDesc, myWeight);

        saveCategoryMeta(selectedCategory, myDesc, neighborWeight);
        saveCategoryMeta(neighborCat, neighborDesc, myWeight);

        this.refreshGroups();
        this.minecraft.tell(() -> this.init(this.minecraft, this.width, this.height));
    }

    private void saveCategoryMeta(String id, String desc, int weight) {
        try {
            CategoryRegistry.CategoryMeta meta = new CategoryRegistry.CategoryMeta(id.toUpperCase(), desc, weight);
            String safeName = id.toLowerCase().replaceAll("[^a-z0-9]", "_");
            File file = new File("config/phoenix_archive/categories", safeName + ".json");
            file.getParentFile().mkdirs();
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                GSON.toJson(meta, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteCategory(String catId) {
        CategoryRegistry.unregister(catId);

        String safeName = catId.toLowerCase().replaceAll("[^a-z0-9]", "_");
        File file = new File("config/phoenix_archive/categories", safeName + ".json");
        if (file.exists()) file.delete();

        this.selectedCategory = "GENERAL";
        this.refreshGroups();
        this.minecraft.tell(() -> this.init(this.minecraft, this.width, this.height));
    }

    private void openDeleteCategoryPrompt() {
        if (selectedCategory == null) return;
        this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen((confirmed) -> {
            if (confirmed) deleteCategory(selectedCategory);
            this.minecraft.setScreen(this);
        }, Component.literal("§4[DELETE_CATEGORY]"),
                Component.literal("Delete category '" + selectedCategory +
                        "'? Entries will remain in their category but it will not appear in the registry.")));
    }

    private void refreshGroups() {
        Set<String> allCats = new HashSet<>(CategoryRegistry.getRegisteredIds());
        LoreDataLoader.LORE_ENTRIES.values().forEach(e -> allCats.add(e.category().toUpperCase()));

        List<String> sortedNames = allCats.stream()
                .sorted(Comparator.comparingInt(CategoryRegistry::getWeight)
                        .thenComparing(String::toString))
                .toList();

        this.groupedEntries.clear();
        for (String cat : sortedNames) {
            List<LoreEntry> entries = LoreDataLoader.LORE_ENTRIES.values().stream()
                    .filter(e -> e.category().equalsIgnoreCase(cat))
                    .sorted(Comparator.comparingInt(LoreEntry::order))
                    .toList();
            this.groupedEntries.put(cat, entries);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int sidebarWidth = 140;
        int contentX = guiX + sidebarWidth + 12;
        int contentWidth = guiWidth - sidebarWidth - 25;
        int windowTop = guiY + 30;

        graphics.fill(guiX, guiY, guiX + guiWidth, guiY + guiHeight, 0xEE050505);
        graphics.renderOutline(guiX, guiY, guiWidth, guiHeight, 0xFF00FF00);
        graphics.drawString(this.font, "> PHOENIX_OS " + ArchiveConfigs.INSTANCE.general.mainMenuName + "// ARCHIVE",
                guiX + 10, guiY + 8, 0x00FF00);

        // --- SIDEBAR TREE ---
        graphics.enableScissor(guiX, windowTop, guiX + sidebarWidth, guiY + guiHeight - 5);
        int currentY = windowTop - (scrollOffset * 12);

        for (Map.Entry<String, List<LoreEntry>> group : groupedEntries.entrySet()) {
            String cat = group.getKey();
            boolean collapsed = collapsedCategories.getOrDefault(cat, false);
            boolean isCurrentDir = cat.equalsIgnoreCase(this.selectedCategory);

            boolean hoveringCat = mouseX >= guiX && mouseX <= guiX + sidebarWidth && mouseY >= currentY &&
                    mouseY < currentY + 12;
            int catColor = isCurrentDir ? 0xFFAA00 : (hoveringCat ? 0xFFFFFF : 0x00AA00);
            graphics.drawString(this.font, (collapsed ? "§6+ " : "§6- ") + cat, guiX + 5, currentY, catColor);
            currentY += 12;

            if (!collapsed) {
                for (LoreEntry entry : group.getValue()) {
                    boolean unlocked = isLoreUnlockedClient(entry);
                    boolean isSelected = entry == selectedEntry;
                    if (isSelected)
                        graphics.fill(guiX + 10, currentY - 1, guiX + sidebarWidth - 5, currentY + 10, 0x3300FF00);

                    if (unlocked) {
                        int color = isSelected ? 0x00FF00 : 0xAAAAAA;
                        graphics.drawString(this.font,
                                (isSelected ? "§f> " : "  ") + (isEditMode ? "§6✎ §7" : "") + entry.title(), guiX + 12,
                                currentY, color);
                    } else {
                        graphics.drawString(this.font, (isSelected ? "> " : "  ") + "§c[DATA_LOCKED]", guiX + 12,
                                currentY, 0xFF4444);
                    }
                    currentY += 12;
                }
            }
        }
        graphics.disableScissor();

        // --- CONTENT PANEL ---
        if (selectedEntry != null) {
            renderEntryContent(graphics, contentX, windowTop, contentWidth);
        } else {
            int centerX = contentX + (contentWidth / 2);
            int centerY = guiY + (guiHeight / 2);

            graphics.drawCenteredString(this.font, "§6FOLDER: " + selectedCategory.toUpperCase(), centerX, centerY - 30,
                    0xFFFFFF);

            String desc = CategoryRegistry.getDescription(selectedCategory);
            if (!desc.isEmpty()) {
                var descLines = this.font.split(Component.literal("§7" + desc), contentWidth - 40);
                int lineY = centerY - 10;
                for (var line : descLines) {
                    graphics.drawCenteredString(this.font, line, centerX, lineY, 0xAAAAAA);
                    lineY += 10;
                }
            }

            if (isEditMode) {
                graphics.drawCenteredString(this.font, "§8[ SELECTED DESTINATION FOR NEW ENTRIES ]", centerX,
                        centerY + 50, 0x444444);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderEntryContent(GuiGraphics graphics, int x, int top, int width) {
        boolean unlocked = isLoreUnlockedClient(selectedEntry);
        int textStartY = guiY + 55;
        int textHeight = guiHeight - 90;

        // FIX: renderFakeItem instead of renderItem — renderItem requires pose stack
        // setup that only renderFakeItem handles safely during arbitrary GUI frames.
        int titleX = x;
        String iconItemStr = selectedEntry.iconItem();
        if (iconItemStr != null && !iconItemStr.isEmpty()) {
            try {
                ResourceLocation iconRes = new ResourceLocation(iconItemStr);
                var item = ForgeRegistries.ITEMS.getValue(iconRes);
                if (item != null && item != Items.AIR) {
                    graphics.renderFakeItem(new ItemStack(item), x, guiY + 28);
                    titleX = x + 18;
                }
            } catch (Exception ignored) {}
        }

        graphics.drawString(this.font, unlocked ? "§6" + selectedEntry.title().toUpperCase() : "§4[ENCRYPTED]", titleX,
                guiY + 30, 0xFFFFFF);
        graphics.drawString(this.font, "§8CAT: " + selectedEntry.category(), titleX, guiY + 40, 0x888888);

        String raw = unlocked ? selectedEntry.content() : selectedEntry.lockedContent();
        if (raw == null) raw = "";
        var lines = this.font.split(Component.literal(raw.replace('&', '§')), width);
        int totalH = (lines.size() * 10) + (!unlocked ? 75 : 0);
        int maxScrollLines = Math.max(0, (totalH - textHeight) / 10);

        graphics.enableScissor(x, textStartY, x + width + 5, textStartY + textHeight);
        int drawY = textStartY - (contentScrollOffset * 10);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(this.font, lines.get(i), x, drawY + (i * 10), unlocked ? 0xCCCCCC : 0x662222);
        }

        if (!unlocked) {
            renderRequisitionBox(graphics, x, drawY + (lines.size() * 10) + 10);
        }
        graphics.disableScissor();

        if (totalH > textHeight) {
            renderScrollbar(graphics, guiX + guiWidth - 10, textStartY, textHeight, maxScrollLines);
        }
    }

    private void renderRequisitionBox(GuiGraphics g, int x, int y) {
        Map<String, String> conditions = selectedEntry.getConditions();
        int questId = selectedEntry.questId();

        int lineCount = 1;
        if (questId != 0) lineCount++;
        for (Map.Entry<String, String> cond : conditions.entrySet()) {
            if (!cond.getValue().isEmpty()) lineCount++;
        }

        g.fill(x - 2, y - 2, guiX + guiWidth - 15, y + 4 + (lineCount * 10), 0x22FF0000);
        g.drawString(this.font, "§c>> REQUISITION:", x + 2, y, 0xFFFFFF);

        int offset = 12;

        if (questId != 0) {
            boolean met = CLIENT_LORE_CACHE.getBoolean("quest_completed:" + questId);
            g.drawString(this.font, (met ? "§a" : "§7") + "- Quest: §f" + questId, x + 5, y + offset, 0xAAAAAA);
            offset += 10;
        }

        for (Map.Entry<String, String> cond : conditions.entrySet()) {
            if (cond.getValue() == null || cond.getValue().isEmpty()) continue;

            boolean met = CLIENT_LORE_CACHE.getBoolean(cond.getKey() + ":" + cond.getValue());
            String typeLabel = getConditionTypeLabel(cond.getKey());
            String valueLabel = prettifyId(cond.getValue());
            g.drawString(this.font, (met ? "§a" : "§7") + "- " + typeLabel + ": §f" + valueLabel, x + 5, y + offset,
                    0xAAAAAA);
            offset += 10;
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
            default -> prettifyId(key);
        };
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int h, int max) {
        g.fill(x, y, x + 2, y + h, 0x22FFFFFF);
        float pct = (float) contentScrollOffset / (float) Math.max(1, max);
        g.fill(x - 1, y + (int) (pct * (h - 15)), x + 3, y + (int) (pct * (h - 15)) + 15, 0xFF00FF00);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listWidth = 140;
        if (mouseX < guiX + listWidth) {
            int totalLines = 0;
            for (String cat : groupedEntries.keySet()) {
                totalLines++;
                if (!collapsedCategories.getOrDefault(cat, false)) totalLines += groupedEntries.get(cat).size();
            }
            int max = Math.max(0, totalLines - ((guiHeight - 40) / 12));
            scrollOffset = (int) Math.max(0, Math.min(scrollOffset - (int) delta, max));
            return true;
        }

        if (selectedEntry != null) {
            boolean unlocked = isLoreUnlockedClient(selectedEntry);
            String raw = unlocked ? selectedEntry.content() : selectedEntry.lockedContent();
            if (raw == null) raw = "";
            var lines = this.font.split(Component.literal(raw.replace('&', '§')), guiWidth - 140 - 35);
            int total = lines.size() + (unlocked ? 0 : 8);
            int max = Math.max(0, (total * 10 - (guiHeight - 90)) / 10);
            contentScrollOffset = (int) Math.max(0, Math.min(contentScrollOffset - (int) delta, max));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int sidebarWidth = 140;
        int currentY = guiY + 30 - (scrollOffset * 12);

        for (String cat : groupedEntries.keySet()) {
            if (mouseX >= guiX && mouseX <= guiX + sidebarWidth && mouseY >= currentY && mouseY < currentY + 12) {
                this.selectedCategory = cat;
                this.selectedEntry = null;

                collapsedCategories.put(cat, !collapsedCategories.getOrDefault(cat, false));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                this.minecraft.tell(() -> this.init(this.minecraft, this.width, this.height));
                return true;
            }
            currentY += 12;

            if (!collapsedCategories.getOrDefault(cat, false)) {
                for (LoreEntry entry : groupedEntries.get(cat)) {
                    if (mouseX >= guiX + 10 && mouseX <= guiX + sidebarWidth && mouseY >= currentY &&
                            mouseY < currentY + 12) {
                        this.selectedEntry = entry;
                        this.selectedCategory = entry.category();
                        this.contentScrollOffset = 0;

                        if (button == 1 && isEditMode) {
                            this.minecraft.getSoundManager()
                                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                            this.minecraft.tell(
                                    () -> this.minecraft.setScreen(new ArchiveEditorScreen(entry, entry.category())));
                        } else {
                            this.minecraft.getSoundManager()
                                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            this.minecraft.tell(() -> this.init(this.minecraft, this.width, this.height));
                        }
                        return true;
                    }
                    currentY += 12;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * FIX: moveEntry order-swap bug.
     *
     * Old bug: when orderA == orderB, code set orderB = orderA + 1 but never updated orderA.
     * Result: updatedA got (orderA + 1) and updatedB got orderA — identical to the starting
     * state, so the sort saw no change and the entry stayed put.
     *
     * Fix: force orders apart BEFORE assigning, then swap so entryA gets the position
     * entryB had and vice versa — guaranteed distinct and in the correct direction.
     */
    private void moveEntry(int direction) {
        if (selectedEntry == null) return;

        String cat = selectedEntry.category();
        List<LoreEntry> catEntries = LoreDataLoader.LORE_ENTRIES.values().stream()
                .filter(e -> e.category().equalsIgnoreCase(cat))
                .sorted(Comparator.comparingInt(LoreEntry::order))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        int currentIndex = -1;
        for (int i = 0; i < catEntries.size(); i++) {
            if (catEntries.get(i).id().equals(selectedEntry.id())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex == -1) return;

        int newIndex = currentIndex + direction;
        if (newIndex < 0 || newIndex >= catEntries.size()) return;

        LoreEntry entryA = catEntries.get(currentIndex);
        LoreEntry entryB = catEntries.get(newIndex);

        int orderA = entryA.order();
        int orderB = entryB.order();

        // Force orders apart first so the swap always produces two distinct values
        if (orderA == orderB) {
            orderB = orderA + (direction > 0 ? -1 : 1);
        }

        // entryA moves into entryB's position, entryB moves into entryA's position
        LoreEntry updatedA = new LoreEntry(entryA.id(), entryA.title(), entryA.category(),
                entryA.content(), entryA.iconItem(), entryA.questId(),
                entryA.lockedContent(), entryA.voiceLine(), entryA.getConditions(), orderB);
        LoreEntry updatedB = new LoreEntry(entryB.id(), entryB.title(), entryB.category(),
                entryB.content(), entryB.iconItem(), entryB.questId(),
                entryB.lockedContent(), entryB.voiceLine(), entryB.getConditions(), orderA);

        ResourceLocation keyA = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                .filter(e -> e.getValue().id().equals(entryA.id())).map(Map.Entry::getKey).findFirst().orElse(null);
        ResourceLocation keyB = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                .filter(e -> e.getValue().id().equals(entryB.id())).map(Map.Entry::getKey).findFirst().orElse(null);

        if (keyA == null || keyB == null) return;

        LoreDataLoader.LORE_ENTRIES.put(keyA, updatedA);
        LoreDataLoader.LORE_ENTRIES.put(keyB, updatedB);

        saveToConfig(updatedA, keyA.getPath());
        saveToConfig(updatedB, keyB.getPath());

        this.selectedEntry = updatedA;
        refreshGroups();
        this.init(this.minecraft, this.width, this.height);
    }

    private void saveToConfig(LoreEntry entry, String fileName) {
        File file = new File("config/phoenix_archive/lore", fileName + ".json");
        file.getParentFile().mkdirs();
        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            GSON.toJson(entry, writer);
        } catch (Exception ignored) {}
    }

    private void deleteEntry(LoreEntry entry) {
        ResourceLocation id = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                .filter(e -> e.getValue().equals(entry)).map(Map.Entry::getKey).findFirst().orElse(null);
        if (id == null) return;

        Path path = this.minecraft.gameDirectory.toPath()
                .resolve("config/phoenix_archive/lore/" + id.getPath() + ".json");
        try {
            Files.deleteIfExists(path);
            LoreDataLoader.LORE_ENTRIES.remove(id);
            this.selectedEntry = null;
            refreshGroups();
            this.init(this.minecraft, this.width, this.height);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isLoreUnlockedClient(LoreEntry entry) {
        boolean hasConditions = entry.getConditions() != null && !entry.getConditions().isEmpty();
        boolean hasQuest = entry.questId() != 0;
        if (!hasConditions && !hasQuest) return true;

        String loreId = (entry.id() != null && !entry.id().isEmpty()) ? entry.id() :
                entry.title().toLowerCase().replace(" ", "_");
        String key = "lore_unlocked:" + loreId;
        return CLIENT_LORE_CACHE.getBoolean(key);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCounter++ % 20 != 0) return;
        if (this.minecraft == null || this.minecraft.player == null) return;

        CompoundTag currentData = this.minecraft.player.getPersistentData().getCompound("PhoenixArchive");
        if (lastPhoenixData == null || !currentData.equals(lastPhoenixData)) {
            this.lastPhoenixData = currentData.copy();
            CLIENT_LORE_CACHE = currentData.copy();
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void playLoreVoice(String soundLocation) {
        if (this.minecraft == null || soundLocation == null || soundLocation.isEmpty()) return;

        if (currentVoice != null) {
            this.minecraft.getSoundManager().stop(currentVoice);
            currentVoice = null;
        }

        ResourceLocation res = soundLocation.contains(":") ? new ResourceLocation(soundLocation) :
                new ResourceLocation("phoenix_archive", soundLocation);

        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(res);

        if (event == null) {
            PhoenixArchive.LOGGER.error("SOUND_NOT_FOUND: {}", res);
            return;
        }

        currentVoice = SimpleSoundInstance.forUI(event, 1.0F, 1.0F);
        this.minecraft.getSoundManager().play(currentVoice);
    }

    private String prettifyId(String id) {
        String path = id.contains(":") ? id.split(":")[1] : id;
        return Arrays.stream(path.split("_"))
                .map(s -> s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(id);
    }

    private void openDeletePrompt() {
        if (selectedEntry == null) return;
        this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen((confirmed) -> {
            if (confirmed) deleteEntry(selectedEntry);
            this.minecraft.setScreen(this);
        }, Component.literal("§4[CRITICAL_PURGE]"),
                Component.literal("Permanently delete '" + selectedEntry.title() + "'?")));
    }
}
