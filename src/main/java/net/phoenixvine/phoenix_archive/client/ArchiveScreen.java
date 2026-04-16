package net.phoenixvine.phoenix_archive.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import net.phoenixvine.phoenix_archive.api.LoreDataLoader;
import net.phoenixvine.phoenix_archive.api.LoreEntry;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;



public class ArchiveScreen extends Screen {
    private LoreEntry selectedEntry = null;
    private List<LoreEntry> filteredEntries;
    private boolean isEditMode = false;
    private int contentScrollOffset = 0; // New: Tracks content scrolling
    private CompoundTag lastPhoenixData;
    public static CompoundTag CLIENT_LORE_CACHE = new CompoundTag();
    private int tickCounter = 0;
    private String currentCategory = "ALL";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<String> categories = new ArrayList<>();
    private SoundInstance currentVoice = null;
    private int scrollOffset = 0; // Tracks how far the list has been scrolled

    public ArchiveScreen() {
        super(Component.literal("Phoenix Archive"));
    }

    private int getMaxVisible(int guiHeight) {
        return (guiHeight - 40) / 22;
    }

    @Override
    protected void init() {
        super.init();

        // 1. Refresh Categories & Filtered List
        categories.clear();
        categories.add("ALL");
        LoreDataLoader.LORE_ENTRIES.values().forEach(e -> {
            if (!categories.contains(e.category())) categories.add(e.category());
        });

        this.filteredEntries = LoreDataLoader.LORE_ENTRIES.values().stream()
                .filter(e -> currentCategory.equals("ALL") || e.category().equals(currentCategory))
                .toList();

        // Clamp scrollOffset in case the filtered list got shorter
        int maxVisible = getMaxVisible(220);
        int maxScroll = Math.max(0, filteredEntries.size() - maxVisible);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int listWidth = getDynamicListWidth();
        int guiWidth = listWidth + 240;
        int guiHeight = 220;
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        int contentXOffset = listWidth + 25;

        boolean isOp = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);

        // 2. Category Tabs
        int catX = x + 10;
        for (String cat : categories) {
            int catW = font.width(cat) + 10;
            final String catFinal = cat;
            this.addRenderableWidget(Button.builder(Component.literal(cat), b -> {
                this.currentCategory = catFinal;
                this.scrollOffset = 0; // Reset scroll when switching categories
                this.init(this.minecraft, this.width, this.height);
            }).bounds(catX, y + guiHeight + 5, catW, 14).build());
            catX += catW + 2;
        }

        // 3. Voice Playback Button
        if (selectedEntry != null && isLoreUnlockedClient(selectedEntry) && !selectedEntry.voiceLine().isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("▶ PLAY VOICE LOG"), b -> {
                playLoreVoice(selectedEntry.voiceLine());
            }).bounds(x + contentXOffset, y + guiHeight - 25, 120, 16).build());
        }

        // 4. Admin / Edit Controls
        if (isOp) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(isEditMode ? "§cEDIT: ON" : "§7EDIT: OFF"),
                    b -> {
                        this.isEditMode = !this.isEditMode;
                        this.init(this.minecraft, this.width, this.height);
                    }).bounds(x + guiWidth - 75, y + 6, 70, 14).build());

            if (isEditMode) {
                this.addRenderableWidget(Button.builder(Component.literal("[+]"),
                                b -> this.minecraft.setScreen(new ArchiveEditorScreen(null, null)))
                        .bounds(x + guiWidth - 97, y + 6, 20, 14).build());

                if (selectedEntry != null) {
                    int ctrlX = x + guiWidth - 25;
                    this.addRenderableWidget(Button.builder(Component.literal("↑"), b -> moveEntry(-1)).bounds(ctrlX, y + 30, 20, 20).build());
                    this.addRenderableWidget(Button.builder(Component.literal("↓"), b -> moveEntry(1)).bounds(ctrlX, y + 55, 20, 20).build());
                    this.addRenderableWidget(Button.builder(Component.literal("§4X"), b -> openDeletePrompt()).bounds(ctrlX, y + 80, 20, 20).build());
                }
            }
        }
    }

    private void playLoreVoice(String soundLocation) {
        if (this.minecraft == null || soundLocation == null || soundLocation.isEmpty()) return;

        if (currentVoice != null) {
            this.minecraft.getSoundManager().stop(currentVoice);
        }

        ResourceLocation res = soundLocation.contains(":")
                ? new ResourceLocation(soundLocation)
                : new ResourceLocation("phoenix_archive", soundLocation);

        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(res);

        if (event == null) {
            PhoenixArchive.LOGGER.error("SOUND_NOT_FOUND: {}", res);
            return;
        }

        currentVoice = SimpleSoundInstance.forUI(event, 1.0F, 1.0F);
        this.minecraft.getSoundManager().play(currentVoice);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        // 1. Layout Math
        int listWidth = getDynamicListWidth();
        int guiWidth = listWidth + 240;
        int guiHeight = 220;
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        int contentX = x + listWidth + 25;
        int maxVisibleEntries = getMaxVisible(guiHeight);

        // 2. Background & Chrome
        graphics.fill(x, y, x + guiWidth, y + guiHeight, 0xEE050505);
        graphics.renderOutline(x, y, guiWidth, guiHeight, 0xFF444444);
        graphics.drawString(this.font, "> PHOENIX_OS // ARCHIVE", x + 10, y + 8, 0x00FF00);

        // --- LEFT SIDE: ENTRY LIST ---
        int listY = y + 30;
        for (int i = 0; i < filteredEntries.size(); i++) {
            int displayIndex = i - scrollOffset;
            if (displayIndex < 0) continue;
            if (displayIndex >= maxVisibleEntries) break;

            LoreEntry entry = filteredEntries.get(i);
            int entryY = listY + (displayIndex * 22);
            boolean unlocked = isLoreUnlockedClient(entry);

            if (entry == selectedEntry) {
                graphics.fill(x + 8, entryY - 2, x + listWidth + 8, entryY + 18, 0x3300FF00);
            }

            ResourceLocation iconLoc = new ResourceLocation(entry.iconItem());
            ItemStack icon = new ItemStack(ForgeRegistries.ITEMS.getValue(iconLoc));
            graphics.renderFakeItem(unlocked ? (icon.isEmpty() ? new ItemStack(Items.PAPER) : icon) : new ItemStack(Items.BARRIER), x + 12, entryY);

            String title = unlocked ? entry.title() : "§8[DATA_LOCKED]";
            graphics.drawString(this.font, (isEditMode ? "§6✎ §7" : "") + title, x + 34, entryY + 4, entry == selectedEntry ? 0x00FF00 : (unlocked ? 0xAAAAAA : 0x555555));
        }

        // --- RIGHT SIDE: CONTENT PANEL ---
        if (selectedEntry != null) {
            boolean unlocked = isLoreUnlockedClient(selectedEntry);
            int wrapWidth = guiWidth - (listWidth + 25) - 35;
            int textStartY = y + 55;
            int textHeight = 140; // Total pixel height of the "window"

            // Headers
            graphics.drawString(this.font, unlocked ? "§6" + selectedEntry.title().toUpperCase() : "§4[ENCRYPTED_SIGNAL]", contentX, y + 30, 0xFFFFFF);
            graphics.drawString(this.font, "§8CATEGORY: " + selectedEntry.category(), contentX, y + 40, 0x888888);

            // Prepare Text Lines
            String rawContent = unlocked ? selectedEntry.content() : selectedEntry.lockedContent();
            Component component = Component.literal(rawContent.replace('&', '§'));
            var allLines = this.font.split(component, wrapWidth);

            // Calculate maximum scroll limit (pixel-based logic)
            // One line is 10px. We also add space for the "Requisition" box if locked.
            int totalContentHeight = (allLines.size() * 10) + (!unlocked ? 70 : 0);
            int maxScrollPixels = Math.max(0, totalContentHeight - textHeight);

            // Clamp current scroll offset to ensure it doesn't go out of bounds
            // (contentScrollOffset is lines, so we multiply/divide by 10)
            int maxScrollLines = maxScrollPixels / 10;
            if (this.contentScrollOffset > maxScrollLines) this.contentScrollOffset = maxScrollLines;
            if (this.contentScrollOffset < 0) this.contentScrollOffset = 0;

            // --- START SCISSORING ---
            graphics.enableScissor(contentX, textStartY, contentX + wrapWidth + 10, textStartY + textHeight);

            int currentDrawY = textStartY - (contentScrollOffset * 10);

            for (int i = 0; i < allLines.size(); i++) {
                graphics.drawString(this.font, allLines.get(i), contentX, currentDrawY + (i * 10), unlocked ? 0xCCCCCC : 0x662222);
            }

            if (!unlocked) {
                int hintY = currentDrawY + (allLines.size() * 10) + 10;
                graphics.fill(contentX - 2, hintY - 2, x + guiWidth - 30, hintY + 50, 0x22FF0000);
                graphics.drawString(this.font, "§c>> REQUISITION:", contentX + 2, hintY, 0xFFFFFF);

                int offset = 12;
                for (Map.Entry<String, String> cond : selectedEntry.getConditions().entrySet()) {
                    boolean condMet = CLIENT_LORE_CACHE.getBoolean(cond.getKey() + ":" + cond.getValue());
                    graphics.drawString(this.font, (condMet ? "§a" : "§7") + "- " + prettifyId(cond.getKey()) + ": §f" + prettifyId(cond.getValue()), contentX + 5, hintY + offset, 0xAAAAAA);
                    offset += 10;
                }
            }

            graphics.disableScissor();
            // --- END SCISSORING ---

            // 3. Render Scrollbar
            if (totalContentHeight > textHeight) {
                int barX = x + guiWidth - 15;
                // Track
                graphics.fill(barX, textStartY, barX + 2, textStartY + textHeight, 0x22FFFFFF);

                // Knob Math
                float scrollPercent = (float) contentScrollOffset / (float) Math.max(1, maxScrollLines);
                int knobHeight = 20;
                int knobY = textStartY + (int)(scrollPercent * (textHeight - knobHeight));

                graphics.fill(barX - 1, knobY, barX + 3, knobY + knobHeight, 0xFF00FF00);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listWidth = getDynamicListWidth();
        int guiWidth = listWidth + 240;
        int x = (this.width - guiWidth) / 2;
        int contentXStart = x + listWidth + 20;

        // ZONE: Content Panel Scrolling
        if (mouseX >= contentXStart && selectedEntry != null) {
            boolean unlocked = isLoreUnlockedClient(selectedEntry);
            int wrapWidth = guiWidth - (listWidth + 25) - 35;

            // 1. Calculate how many lines we actually have
            String rawContent = unlocked ? selectedEntry.content() : selectedEntry.lockedContent();
            var lines = this.font.split(Component.literal(rawContent.replace('&', '§')), wrapWidth);

            int totalLines = lines.size();
            if (!unlocked) {
                // Add extra "virtual lines" for the Requisition box (approx 5-6 lines tall)
                totalLines += 6;
            }

            // 2. Calculate the limit (How many lines fit in the 140px height window?)
            int visibleLines = 14; // 140px height / 10px per line
            int maxScroll = Math.max(0, totalLines - visibleLines);

            // 3. Apply the scroll and CLAMP it
            // We use Math.min to prevent going past the bottom, and Math.max to prevent going above the top
            contentScrollOffset = (int) Math.max(0, Math.min(contentScrollOffset - delta, maxScroll));

            return true;
        }

        // ZONE: Left List Scrolling
        int maxVisible = getMaxVisible(220);
        int maxScrollList = Math.max(0, filteredEntries.size() - maxVisible);
        scrollOffset = (int) Math.max(0, Math.min(scrollOffset - delta, maxScrollList));

        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listWidth = getDynamicListWidth();
        int guiHeight = 220;
        int x = (this.width - (listWidth + 240)) / 2;
        int y = (this.height - guiHeight) / 2;
        int listY = y + 30;
        int maxVisible = getMaxVisible(guiHeight);

        for (int i = 0; i < filteredEntries.size(); i++) {
            int displayIndex = i - scrollOffset;
            if (displayIndex < 0) continue;
            if (displayIndex >= maxVisible) break;

            LoreEntry entry = filteredEntries.get(i);
            int entryY = listY + (displayIndex * 22);

            if (mouseX >= x + 8 && mouseX <= x + 8 + listWidth && mouseY >= entryY - 2 && mouseY < entryY + 20) {
                this.selectedEntry = entry;
                this.contentScrollOffset = 0; // Reset content scroll when clicking a new entry

                if (button == 1 && isEditMode) {
                    ResourceLocation id = getEntryId(entry);
                    this.minecraft.setScreen(new ArchiveEditorScreen(entry, id));
                } else {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.init(this.minecraft, this.width, this.height);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openDeletePrompt() {
        if (selectedEntry == null) return;
        this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen((confirmed) -> {
            if (confirmed) deleteEntry(selectedEntry);
            this.minecraft.setScreen(this);
        }, Component.literal("§4[CRITICAL_PURGE]"), Component.literal("Permanently delete '" + selectedEntry.title() + "'?")));
    }

    private void moveEntry(int direction) {
        if (selectedEntry == null) return;
        List<LoreEntry> entries = new ArrayList<>(LoreDataLoader.LORE_ENTRIES.values());
        int currentIndex = entries.indexOf(selectedEntry);
        int newIndex = currentIndex + direction;

        if (newIndex < 0 || newIndex >= entries.size()) return;

        Collections.swap(entries, currentIndex, newIndex);
        Map<ResourceLocation, LoreEntry> newMap = new LinkedHashMap<>();
        LoreEntry updatedSelected = null;

        for (int i = 0; i < entries.size(); i++) {
            LoreEntry old = entries.get(i);
            LoreEntry updated = new LoreEntry(
                    old.id(), old.title(), old.category(), old.content(), old.iconItem(),
                    old.questId(), old.lockedContent(), old.voiceLine(), old.getConditions(), i
            );

            ResourceLocation id = LoreDataLoader.LORE_ENTRIES.keySet().toArray(new ResourceLocation[0])[entries.indexOf(old)];
            newMap.put(id, updated);
            saveToConfig(updated, id.getPath());
            if (old == selectedEntry) updatedSelected = updated;
        }

        LoreDataLoader.LORE_ENTRIES = newMap;
        this.selectedEntry = updatedSelected;
        this.init(this.minecraft, this.width, this.height);
    }

    private void saveToConfig(LoreEntry entry, String fileName) {
        File file = new File("config/phoenix_archive/lore", fileName + ".json");
        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            GSON.toJson(entry, writer);
        } catch (Exception ignored) {}
    }

    private void deleteEntry(LoreEntry entry) {
        ResourceLocation id = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                .filter(e -> e.getValue().equals(entry)).map(Map.Entry::getKey).findFirst().orElse(null);
        if (id == null) return;

        Path path = this.minecraft.gameDirectory.toPath().resolve("config/phoenix_archive/lore/" + id.getPath() + ".json");
        try {
            Files.deleteIfExists(path);
            LoreDataLoader.LORE_ENTRIES.remove(id);
            this.selectedEntry = null;
            this.init(this.minecraft, this.width, this.height);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private ResourceLocation getEntryId(LoreEntry entry) {
        return LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                .filter(e -> e.getValue().equals(entry)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private boolean isLoreUnlockedClient(LoreEntry entry) {
        String loreId = (entry.id() != null && !entry.id().isEmpty())
                ? entry.id()
                : entry.title().toLowerCase().replace(" ", "_");
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

    private int getDynamicListWidth() {
        int max = 80;
        for (LoreEntry e : LoreDataLoader.LORE_ENTRIES.values()) {
            max = Math.max(max, this.font.width(e.title()));
        }
        return Math.min(180, max + 40);
    }

    private String prettifyId(String id) {
        String path = id.contains(":") ? id.split(":")[1] : id;
        return Arrays.stream(path.split("_"))
                .map(s -> s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(id);
    }
}