package net.phoenix.phoenix_archive.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenix.phoenix_archive.PhoenixArchive;
import net.phoenix.phoenix_archive.api.LoreDataLoader;
import net.phoenix.phoenix_archive.api.LoreEntry;
import net.phoenix.phoenix_archive.api.QuestHelper;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ArchiveScreen extends Screen {
    private LoreEntry selectedEntry = null;
    private LoreEntry entryToPurge = null;
    private List<LoreEntry> filteredEntries;
    private boolean isEditMode = false;
    private CompoundTag lastPhoenixData;
    private int tickCounter = 0;
    private String currentCategory = "ALL";
    private final List<String> categories = new ArrayList<>();

    public ArchiveScreen() {
        super(Component.literal("Phoenix Archive"));
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

        // 2. Layout Positioning
        int listWidth = getDynamicListWidth();
        int guiWidth = listWidth + 240;
        int guiHeight = 220;
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;

        boolean isOp = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);

        // 3. Category Tabs
        int catX = x + 10;
        for (String cat : categories) {
            int catW = font.width(cat) + 10;
            this.addRenderableWidget(Button.builder(Component.literal(cat), b -> {
                this.currentCategory = cat;
                this.init(this.minecraft, this.width, this.height);
            }).bounds(catX, y + guiHeight + 5, catW, 14).build());
            catX += catW + 2;
        }

        // 4. Admin Controls (Pushed right to avoid title overlap)
        if (isOp) {
            this.addRenderableWidget(Button.builder(
                            Component.literal(isEditMode ? "§cEDIT: ON" : "§7EDIT: OFF"),
                            b -> {
                                this.isEditMode = !this.isEditMode;
                                this.init(this.minecraft, this.width, this.height);
                            })
                    .bounds(x + guiWidth - 75, y + 6, 70, 14).build());

            if (isEditMode && selectedEntry != null) {
                int deleteX = x + guiWidth - 25;
                // The [X] button for deletion
                this.addRenderableWidget(Button.builder(Component.literal("§4X"), b -> {
                    this.deleteEntry(selectedEntry);
                }).bounds(deleteX, y + 80, 20, 20).build());
            }

            if (isEditMode) {
                this.addRenderableWidget(Button.builder(Component.literal("[+]"),
                                b -> this.minecraft.setScreen(new ArchiveEditorScreen(null, null)))
                        .bounds(x + guiWidth - 97, y + 6, 20, 14).build());

                // 5. REORDER BUTTONS (GUI Buttons instead of keyboard)
                if (selectedEntry != null) {
                    int arrowX = x + guiWidth - 25;
                    this.addRenderableWidget(Button.builder(Component.literal("↑"), b -> moveEntry(-1))
                            .bounds(arrowX, y + 30, 20, 20).build());
                    this.addRenderableWidget(Button.builder(Component.literal("↓"), b -> moveEntry(1))
                            .bounds(arrowX, y + 55, 20, 20).build());
                }
            }
        }
    }


    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // We render the background first, then our custom box, then super (buttons)
        this.renderBackground(graphics);

        int listWidth = getDynamicListWidth();
        int guiWidth = listWidth + 240;
        int guiHeight = 220;
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        int contentXOffset = listWidth + 25;

        // 1. Draw Background Frame
        graphics.fill(x, y, x + guiWidth, y + guiHeight, 0xEE050505);
        graphics.renderOutline(x, y, guiWidth, guiHeight, 0xFF444444);

        // 2. Header
        graphics.drawString(this.font, "> PHOENIX_OS // ARCHIVE", x + 10, y + 8, 0x00FF00);

        // 3. Render List with Edit Icons
        int listY = y + 30;
        for (int i = 0; i < filteredEntries.size(); i++) {
            LoreEntry entry = filteredEntries.get(i);
            int entryY = listY + (i * 22);
            boolean unlocked = isLoreUnlockedClient(entry);

            if (entry == selectedEntry) {
                graphics.fill(x + 8, entryY - 2, x + listWidth + 8, entryY + 18, 0x3300FF00);
            }

            ItemStack icon = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.iconItem())));
            graphics.renderFakeItem(unlocked ? (icon.isEmpty() ? new ItemStack(Items.PAPER) : icon) : new ItemStack(Items.BARRIER), x + 12, entryY);

            String title = unlocked ? entry.title() : "§8[DATA_LOCKED]";
            int color = entry == selectedEntry ? 0x00FF00 : (unlocked ? 0xAAAAAA : 0x555555);

            // PENCIL INDICATOR: Now draws properly in the sidebar
            String prefix = isEditMode ? "§6✎ §7" : "";
            graphics.drawString(this.font, prefix + title, x + 34, entryY + 4, color);
        }

        // 4. Content Area
        if (selectedEntry != null) {
            boolean unlocked = isLoreUnlockedClient(selectedEntry);
            int cx = x + contentXOffset;
            int wrapWidth = guiWidth - contentXOffset - 30; // Extra padding for arrow buttons

            graphics.drawString(this.font, unlocked ? "§6" + selectedEntry.title().toUpperCase() : "§4[ENCRYPTED_SIGNAL]", cx, y + 30, 0xFFFFFF);
            graphics.drawString(this.font, "§8CATEGORY: " + selectedEntry.category(), cx, y + 40, 0x888888);

            String body = unlocked ? selectedEntry.content() : selectedEntry.lockedContent();
            var lines = this.font.split(Component.literal(body), wrapWidth);
            for (int j = 0; j < lines.size(); j++) {
                graphics.drawString(this.font, lines.get(j), cx, y + 55 + (j * 10), unlocked ? 0xCCCCCC : 0x662222);
            }

            // MOVE HINT: Only shows when reordering is possible
            if (isEditMode) {
                graphics.drawString(this.font, "§8MODIFY_ORDER", x + guiWidth - 65, y + 22, 0x444444);
            }

            if (!unlocked) {
                int hintY = y + 65 + (lines.size() * 10);
                graphics.fill(cx - 2, hintY - 2, x + guiWidth - 30, hintY + 35, 0x22FF0000);
                graphics.drawString(this.font, "§c>> REQUISITION:", cx + 2, hintY, 0xFFFFFF);

                int offset = 12;
                if (!selectedEntry.getDimensionId().isEmpty()) {
                    graphics.drawString(this.font, "§7- Locate Dimension: " + prettifyId(selectedEntry.getDimensionId()), cx + 5, hintY + offset, 0xAAAAAA);
                    offset += 10;
                }
                if (!selectedEntry.getBiomeId().isEmpty()) {
                    graphics.drawString(this.font, "§7- Survey Biome: " + prettifyId(selectedEntry.getBiomeId()), cx + 5, hintY + offset, 0xAAAAAA);
                    offset += 10;
                }
                if (selectedEntry.questId() != 0) {
                    graphics.drawString(this.font, "§7- Complete Quest ID: " + selectedEntry.questId(), cx + 5, hintY + offset, 0xAAAAAA);
                }
            }
        }

        // SUPER RENDER: This draws the buttons (↑, ↓, EDIT) last so they are on top.
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void moveEntry(int direction) {
        if (selectedEntry == null) return;

        // 1. Convert the Map to a List to manipulate indices
        List<Map.Entry<ResourceLocation, LoreEntry>> entries = new ArrayList<>(LoreDataLoader.LORE_ENTRIES.entrySet());

        int currentIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getValue().equals(selectedEntry)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex != -1) {
            int newIndex = currentIndex + direction;

            // 2. Check boundaries (Don't move past top or bottom)
            if (newIndex >= 0 && newIndex < entries.size()) {
                // Swap the items
                Collections.swap(entries, currentIndex, newIndex);

                // 3. Rebuild the LinkedHashMap to preserve the new order
                Map<ResourceLocation, LoreEntry> newMap = new LinkedHashMap<>();
                for (var e : entries) {
                    newMap.put(e.getKey(), e.getValue());
                }

                // 4. Update the static loader so the change is "Live"
                LoreDataLoader.LORE_ENTRIES = newMap;

                // 5. REBOOT the UI to show the change immediately
                this.init(this.minecraft, this.width, this.height);

                // 6. Play a mechanical "click" sound
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2F));
                }
            }
        }
    }
    private void deleteEntry(LoreEntry entry) {
        if (minecraft == null) return;

        // 1. Determine the file path
        // We use the title-to-key logic to find the file name
        String safeName = entry.title().toLowerCase().replaceAll("[^a-z0-9]", "_");
        Path configPath = this.minecraft.gameDirectory.toPath().resolve("config/phoenix_archive/lore/" + safeName + ".json");

        try {
            // 2. Physical Deletion
            if (Files.exists(configPath)) {
                Files.delete(configPath);
                PhoenixArchive.LOGGER.info("§6[SYSTEM] §fPurged entry file: {}", safeName);
            }

            // 3. Memory Deletion
            // Remove it from the static map so it disappears from the GUI immediately
            LoreDataLoader.LORE_ENTRIES.entrySet().removeIf(e -> e.getValue().equals(entry));

            // 4. UI Reset
            this.selectedEntry = null;
            this.init(this.minecraft, this.width, this.height);

            // 5. Sound Feedback
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 0.5F));

        } catch (Exception e) {
            PhoenixArchive.LOGGER.error("Failed to delete lore entry!", e);
        }
    }

    private boolean isLoreUnlockedClient(LoreEntry entry) {
        if (this.minecraft == null || this.minecraft.player == null) return false;

        // The Master Ledger key format
        String key = "unlocked_" + entry.title().toLowerCase().replaceAll("[^a-z0-9]", "_");

        // We grab the Truth directly from the player's NBT, which is updated by the Server packets
        CompoundTag nbt = this.minecraft.player.getPersistentData().getCompound("PhoenixArchive");

        return nbt.getBoolean(key);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCounter++ % 20 == 0 && this.minecraft != null && this.minecraft.player != null) {
            CompoundTag currentData = this.minecraft.player.getPersistentData().getCompound("PhoenixArchive");
            if (lastPhoenixData == null || !currentData.equals(lastPhoenixData)) {
                this.lastPhoenixData = currentData.copy();
                // This forces the screen to check if anything newly unlocked should be displayed
                this.init(this.minecraft, this.width, this.height);
            }
        }
    }



    private int getDynamicListWidth() {
        int maxTitleFound = 80;
        for (LoreEntry e : LoreDataLoader.LORE_ENTRIES.values()) {
            int w = this.font.width(e.title());
            if (w > maxTitleFound) maxTitleFound = w;
        }
        return Math.min(180, maxTitleFound + 40);
    }

    private String prettifyId(String id) {
        String path = id.contains(":") ? id.split(":")[1] : id;
        return Arrays.stream(path.split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listWidth = getDynamicListWidth();
        int x = (this.width - (listWidth + 240)) / 2;
        int y = (this.height - 220) / 2;

        for (int i = 0; i < filteredEntries.size(); i++) {
            LoreEntry entry = filteredEntries.get(i);
            int entryY = y + 30 + (i * 22);

            if (mouseX >= x + 10 && mouseX <= x + 10 + listWidth && mouseY >= entryY && mouseY < entryY + 20) {
                this.selectedEntry = entry;
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}