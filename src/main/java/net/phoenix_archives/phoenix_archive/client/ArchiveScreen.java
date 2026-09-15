package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenix_archives.phoenix_archive.PhoenixArchive;
import net.phoenix_archives.phoenix_archive.api.CategoryDefinition;
import net.phoenix_archives.phoenix_archive.api.CategoryRegistry;
import net.phoenix_archives.phoenix_archive.api.ChroniclesQuestBridge;
import net.phoenix_archives.phoenix_archive.api.ConditionEvaluator;
import net.phoenix_archives.phoenix_archive.api.ConditionNodeAdapter;
import net.phoenix_archives.phoenix_archive.api.ExternalCondition;
import net.phoenix_archives.phoenix_archive.api.LoreDataLoader;
import net.phoenix_archives.phoenix_archive.api.LoreEntry;
import net.phoenix_archives.phoenix_archive.api.QuestHelper;
import net.phoenix_archives.phoenix_archive.client.render.shader.ArchiveShaderManager;
import net.phoenix_archives.phoenix_archive.client.render.shader.ArchiveShaderRenderUtil;
import net.phoenix_archives.phoenix_archive.client.rich.ArchiveMarkdownParser;
import net.phoenix_archives.phoenix_archive.client.rich.ArchiveRichTextRenderer;
import net.phoenix_archives.phoenix_archive.client.rich.RichBlock;
import net.phoenix_archives.phoenix_archive.client.rich.RichSpan;
import net.phoenix_archives.phoenix_archive.common.LoreSavedData;
import net.phoenix_archives.phoenix_archive.config.ArchiveConfigs;
import net.phoenix_archives.phoenix_archive.network.BookmarkPacket;
import net.phoenix_archives.phoenix_archive.network.PhoenixNetwork;
import net.phoenixvine.wiki.client.screen.WikiScreen;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ArchiveScreen extends Screen {

    private LoreEntry selectedEntry = null;
    private boolean isEditMode = false;
    private int contentScrollOffset = 0;
    private int scrollOffset = 0;
    private String selectedCategory = "GENERAL";

    private LoreEntry cachedContentEntry;
    private boolean cachedContentUnlocked;
    private List<RichBlock> cachedContentBlocks;

    private final Set<String> richExpandedKeys = new HashSet<>();
    
    private List<RichSpan.Region> lastContentRegions = List.of();

    private CompoundTag lastPhoenixData;
    public static CompoundTag CLIENT_LORE_CACHE = new CompoundTag();

    private final Map<String, Boolean> collapsedCategories = new HashMap<>();

    private EditBox searchBox;
    private String searchQuery = "";

    private int dragRowIndex = -1;
    
    private LoreEntry dragEntry = null;
    
    private double dragCurrentY = 0;

    private LoreEntry dragPending = null;
    private int dragPendingRowIndex = -1;
    private double dragPendingMouseY = 0;
    
    private long dragPressTime = 0;
    
    private static final long DRAG_HOLD_MS = 300;

    private final Set<String> bulkSelected = new HashSet<>();
    
    private boolean bulkMode = false;

    private final List<SidebarRow> sidebarRows = new ArrayList<>();

    private final List<String> orderedCategories = new ArrayList<>();

    private int tickCounter = 0;
    private SoundInstance currentVoice = null;
    private static final Gson GSON = ConditionNodeAdapter.register(new GsonBuilder().setPrettyPrinting()).create();

    private int guiX, guiY;
    private int guiWidth = 420;
    private int guiHeight = 240;

    private interface SidebarRow {}

    private static final class CatRow implements SidebarRow {

        final String id;
        final int depth;

        CatRow(String id, int depth) {
            this.id = id;
            this.depth = depth;
        }
    }

    private static final class EntryRow implements SidebarRow {

        final LoreEntry entry;
        final int depth;

        EntryRow(LoreEntry entry, int depth) {
            this.entry = entry;
            this.depth = depth;
        }
    }

    private LoreEntry pendingPreselect;

    private final Screen parent;

    public ArchiveScreen() {
        this((Screen) null);
    }

    public ArchiveScreen(Screen parent) {
        super(Component.literal(ArchiveConfigs.INSTANCE.general.mainMenuName));
        this.parent = parent;
        CategoryRegistry.loadFromDisk();
    }

    public ArchiveScreen(LoreEntry preselect) {
        this(null, preselect);
    }

    public ArchiveScreen(Screen parent, LoreEntry preselect) {
        this(parent);
        this.pendingPreselect = preselect;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    public void refreshSelectedEntry(LoreEntry updated) {
        this.selectedEntry = updated;
        this.selectedCategory = updated.category();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        this.guiX = (this.width - this.guiWidth) / 2;
        this.guiY = (this.height - this.guiHeight) / 2;

        if (pendingPreselect != null) {
            this.selectedEntry = pendingPreselect;
            this.selectedCategory = pendingPreselect.category();
            this.pendingPreselect = null;
        }

        refreshGroups();

        searchBox = new EditBox(this.font, guiX + 2, guiY + 18, 136, 10, Component.empty());
        searchBox.setHint(Component.literal("§8search..."));
        searchBox.setValue(searchQuery);
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setResponder(query -> {
            searchQuery = query;
            refreshGroups();
        });
        this.addRenderableWidget(searchBox);

        int sidebarWidth = 140;
        int contentXOffset = sidebarWidth + 20;
        boolean isOp = this.minecraft != null && this.minecraft.player != null &&
                this.minecraft.player.hasPermissions(2);

        if (selectedEntry != null && isLoreUnlockedClient(selectedEntry) && selectedEntry.voiceLine() != null &&
                !selectedEntry.voiceLine().isEmpty()) {
            final LoreEntry voiceTarget = selectedEntry; 
            this.addRenderableWidget(
                    Button.builder(Component.literal("▶ PLAY VOICE"), b -> playLoreVoice(voiceTarget.voiceLine()))
                            .bounds(guiX + contentXOffset, guiY + guiHeight - 25, 100, 16)
                            .tooltip(Tooltip.create(Component.literal("Play Voice Entry")))
                            .build());
        }

        if (selectedEntry != null && isLoreUnlockedClient(selectedEntry) &&
                ModList.get().isLoaded("phoenix_chronicles")) {
            String chroniclesQuestId = selectedEntry.conditionTree().findLeafValue("chronicles_quest").orElse(null);
            if (chroniclesQuestId != null && !chroniclesQuestId.isEmpty()) {
                this.addRenderableWidget(Button.builder(Component.literal("§b→ QUEST TREE"),
                        b -> ArchiveClient.openChroniclesQuest(this, chroniclesQuestId))
                        .bounds(guiX + contentXOffset + 104, guiY + guiHeight - 25, 100, 16)
                        .tooltip(Tooltip.create(Component.literal("Jump to this quest in Chronicles")))
                        .build());
            }
        }

        if (selectedEntry != null) {
            final LoreEntry pinnedEntry = selectedEntry; 
            boolean bookmarked = isBookmarked(pinnedEntry);
            this.addRenderableWidget(Button.builder(
                    Component.literal(bookmarked ? "§6★" : "§8☆"),
                    b -> toggleBookmark(pinnedEntry))
                    .bounds(guiX + guiWidth - 24, guiY + guiHeight - 25, 14, 14)
                    .tooltip(Tooltip.create(Component.literal(bookmarked ? "Remove bookmark" : "Bookmark this entry")))
                    .build());

            if (isLoreUnlockedClient(pinnedEntry)) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("§7[copy]"),
                        b -> {
                            final String text = pinnedEntry.content();
                            new Thread(() -> this.minecraft.keyboardHandler.setClipboard(text),
                                    "phoenix_archives-clipboard").start();
                        })
                        .bounds(guiX + guiWidth - 42, guiY + guiHeight - 25, 16, 14)
                        .tooltip(Tooltip.create(Component.literal("Copy entry text to clipboard")))
                        .build());
            }
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
                        .tooltip(Tooltip.create(Component.literal("Open New Category Screen")))
                        .build());

                int ctrlX = guiX + guiWidth - 22;
                final String pinnedCat = selectedCategory; 

                this.addRenderableWidget(Button.builder(Component.literal("New Entry"),
                        b -> this.minecraft.setScreen(new ArchiveEditorScreen(this, null, pinnedCat)))
                        .bounds(guiX + guiWidth - 146, guiY + 6, 64, 14)
                        .tooltip(Tooltip.create(Component.literal("Open New Entry Screen")))
                        .build());

                if (selectedEntry != null) {
                    final LoreEntry pinnedEdit = selectedEntry;

                    boolean viewingViaBookmark = BOOKMARK_CAT_ID.equals(selectedCategory);
                    if (!viewingViaBookmark) {
                        this.addRenderableWidget(Button.builder(Component.literal("§4X"), b -> openDeletePrompt())
                                .bounds(ctrlX, guiY + 30, 18, 18)
                                .tooltip(Tooltip.create(Component.literal("Delete Entry? Cannot be undone.")))
                                .build());
                        this.addRenderableWidget(Button.builder(Component.literal("§aDupe"),
                                b -> duplicateEntry(pinnedEdit))
                                .bounds(ctrlX - 36, guiY + 52, 55, 14)
                                .tooltip(Tooltip.create(Component.literal("Duplicate this entry as a new draft")))
                                .build());
                    }

                } else if (selectedCategory != null && !BOOKMARK_CAT_ID.equals(selectedCategory)) {
                    this.addRenderableWidget(Button.builder(Component.literal("↑"), b -> shiftCategory(-1))
                            .tooltip(Tooltip.create(Component.literal("Move Category Up (among siblings)")))
                            .bounds(ctrlX, guiY + 30, 18, 18).build());

                    this.addRenderableWidget(Button.builder(Component.literal("↓"), b -> shiftCategory(1))
                            .tooltip(Tooltip.create(Component.literal("Move Category Down (among siblings)")))
                            .bounds(ctrlX, guiY + 50, 18, 18).build());

                    this.addRenderableWidget(
                            Button.builder(Component.literal("§4Delete"), b -> openDeleteCategoryPrompt())
                                    .tooltip(Tooltip.create(Component.literal("Delete this category (entries remain)")))
                                    .bounds(ctrlX - 36, guiY + 70, 55, 14).build());

                    this.addRenderableWidget(Button
                            .builder(Component.literal("§eEdit"),
                                    b -> this.minecraft.setScreen(new CategoryManagementScreen(this, pinnedCat)))
                            .tooltip(Tooltip.create(Component.literal("Edit this category's description and weight")))
                            .bounds(ctrlX - 36, guiY + 87, 55, 14).build());
                }

                String bulkLabel = bulkMode ? "§e[BULK: ON]" : "§8[BULK: OFF]";
                this.addRenderableWidget(Button.builder(Component.literal(bulkLabel), b -> {
                    bulkMode = !bulkMode;
                    if (!bulkMode) bulkSelected.clear();
                    this.init(this.minecraft, this.width, this.height);
                })
                        .bounds(ctrlX - 55, guiY + guiHeight - 40, 74, 14)
                        .tooltip(Tooltip.create(Component.literal(
                                "Toggle bulk-select mode. Check entries in the sidebar, then click a category to assign.")))
                        .build());

                if (bulkMode && !bulkSelected.isEmpty() && selectedCategory != null &&
                        !BOOKMARK_CAT_ID.equals(selectedCategory)) {
                    final String bulkTarget = selectedCategory; 
                    int assignX = guiX + 144;
                    int assignW = guiWidth - 144 - 25;
                    this.addRenderableWidget(Button.builder(
                            Component.literal("§6► Move " + bulkSelected.size() + " entr" +
                                    (bulkSelected.size() == 1 ? "y" : "ies") +
                                    " → §e" + bulkTarget),
                            b -> bulkRecategorise(bulkTarget))
                            .bounds(assignX, guiY + guiHeight - 42, assignW, 16)
                            .tooltip(Tooltip.create(Component.literal(
                                    "Move all checked entries into: " + bulkTarget +
                                            "\nClick a different category first to change the target.")))
                            .build());
                }
            }
        }
    }

    private void shiftCategory(int direction) {
        if (selectedCategory == null) return;

        String parentId = CategoryRegistry.getParentId(selectedCategory);
        
        List<String> siblings = new ArrayList<>(CategoryRegistry.getChildren(parentId));

        int idx = siblings.indexOf(selectedCategory);
        if (idx == -1) return;

        int neighborIdx = idx + direction;
        if (neighborIdx < 0 || neighborIdx >= siblings.size()) return;

        String neighborCat = siblings.get(neighborIdx);

        int myWeight = CategoryRegistry.getWeight(selectedCategory);
        int neighborWeight = CategoryRegistry.getWeight(neighborCat);

        if (myWeight == neighborWeight) {
            neighborWeight = myWeight + (direction > 0 ? 1 : -1);
        }

        String myDesc = CategoryRegistry.getDescription(selectedCategory);
        String neighborDesc = CategoryRegistry.getDescription(neighborCat);
        String myParent = CategoryRegistry.getParentId(selectedCategory);
        String neighborParent = CategoryRegistry.getParentId(neighborCat);

        CategoryRegistry.register(selectedCategory, myDesc, neighborWeight, myParent);
        CategoryRegistry.register(neighborCat, neighborDesc, myWeight, neighborParent);

        saveCategoryMeta(selectedCategory, myDesc, neighborWeight, myParent);
        saveCategoryMeta(neighborCat, neighborDesc, myWeight, neighborParent);

        this.refreshGroups();
        this.minecraft.tell(() -> this.init(this.minecraft, this.width, this.height));
    }

    private void saveCategoryMeta(String id, String desc, int weight, String parentId) {
        try {
            var def = new CategoryDefinition(
                    id.toUpperCase(), desc, weight, parentId);
            String safeName = id.toLowerCase().replaceAll("[^a-z0-9]", "_");
            File file = new File("config/phoenix_archive/categories", safeName + ".json");
            file.getParentFile().mkdirs();
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                GSON.toJson(def, writer);
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

    private static final String BOOKMARK_CAT_ID = "★ BOOKMARKED";

    private void refreshGroups() {
        Set<String> registeredIds = CategoryRegistry.getRegisteredIds();
        Set<String> orphanIds = new HashSet<>();
        LoreDataLoader.LORE_ENTRIES.values().forEach(e -> {
            String cat = e.category().toUpperCase();
            if (!registeredIds.contains(cat)) orphanIds.add(cat);
        });

        sidebarRows.clear();
        orderedCategories.clear();

        List<LoreEntry> bookmarked = LoreDataLoader.LORE_ENTRIES.values().stream()
                .filter(e -> CLIENT_LORE_CACHE.getBoolean("bookmark:" + e.id()))
                .sorted(Comparator.comparing(LoreEntry::title))
                .toList();
        if (!bookmarked.isEmpty()) {
            orderedCategories.add(BOOKMARK_CAT_ID);
            sidebarRows.add(new CatRow(BOOKMARK_CAT_ID, 0));
            if (!collapsedCategories.getOrDefault(BOOKMARK_CAT_ID, false)) {
                boolean searching = searchQuery != null && !searchQuery.isEmpty();
                for (LoreEntry e : bookmarked) {
                    if (!searching || entryMatchesSearch(e)) {
                        sidebarRows.add(new EntryRow(e, 0));
                    }
                }
            }
        }

        Set<String> visited = new HashSet<>();
        walkTree(null, orphanIds, visited, 0);
        for (String cat : orphanIds) {
            if (!visited.contains(cat)) appendCategoryRows(cat, orphanIds, visited, 0);
        }
    }

    private boolean entryMatchesSearch(LoreEntry e) {
        if (searchQuery == null || searchQuery.isEmpty()) return true;
        String q = searchQuery.toLowerCase();
        return e.title().toLowerCase().contains(q) || e.category().toLowerCase().contains(q) ||
                (e.content() != null && e.content().toLowerCase().contains(q));
    }

    private boolean categoryHasSearchMatch(String catId) {
        if (searchQuery == null || searchQuery.isEmpty()) return true;
        for (LoreEntry e : LoreDataLoader.LORE_ENTRIES.values()) {
            if (e.category().equalsIgnoreCase(catId) && entryMatchesSearch(e)) return true;
        }
        for (String child : CategoryRegistry.getChildren(catId)) {
            if (categoryHasSearchMatch(child)) return true;
        }
        return false;
    }

    private void walkTree(String parentId, Set<String> orphanIds, Set<String> visited, int depth) {
        List<String> children = new ArrayList<>(CategoryRegistry.getChildren(parentId));
        for (String id : orphanIds) {
            if (!visited.contains(id) && !children.contains(id)) {
                String regParent = CategoryRegistry.getParentId(id);
                if (Objects.equals(regParent, parentId == null ? null : parentId.toUpperCase()))
                    children.add(id);
            }
        }
        for (String cat : children) appendCategoryRows(cat, orphanIds, visited, depth);
    }

    private void appendCategoryRows(String cat, Set<String> orphanIds, Set<String> visited, int depth) {
        if (visited.contains(cat)) return;
        
        if (!categoryHasSearchMatch(cat)) {
            visited.add(cat);
            return;
        }
        visited.add(cat);

        orderedCategories.add(cat);
        sidebarRows.add(new CatRow(cat, depth));

        boolean searching = searchQuery != null && !searchQuery.isEmpty();
        boolean collapsed = !searching && collapsedCategories.getOrDefault(cat, false);
        if (!collapsed) {
            LoreDataLoader.LORE_ENTRIES.values().stream()
                    .filter(e -> e.category().equalsIgnoreCase(cat) && entryMatchesSearch(e))
                    .sorted(Comparator.comparingInt(LoreEntry::order))
                    .forEach(e -> sidebarRows.add(new EntryRow(e, depth)));
            walkTree(cat, orphanIds, visited, depth + 1);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);

        int sidebarWidth = 140;
        int contentX = guiX + sidebarWidth + 12;
        int contentWidth = guiWidth - sidebarWidth - 25;

        graphics.fill(guiX, guiY, guiX + guiWidth, guiY + guiHeight, ArchivePalette.BG_SCRIM);
        graphics.renderOutline(guiX, guiY, guiWidth, guiHeight, ArchivePalette.TERM_BRIGHT);
        graphics.drawString(this.font, "> " + ArchiveConfigs.INSTANCE.general.mainMenuName, guiX + 10, guiY + 8,
                ArchivePalette.TERM);

        graphics.fill(guiX + 2, guiY + 29, guiX + 138, guiY + 30,
                ArchivePalette.withAlpha(ArchivePalette.TERM_BRIGHT, 0x44));

        int windowTop = guiY + 33; 
        graphics.enableScissor(guiX, windowTop, guiX + sidebarWidth, guiY + guiHeight - 5);
        int currentY = windowTop - (scrollOffset * 12);

        for (SidebarRow row : sidebarRows) {
            if (row instanceof CatRow) {
                CatRow catRow = (CatRow) row;
                int indentX = guiX + 5 + catRow.depth * 4;
                boolean searching = searchQuery != null && !searchQuery.isEmpty();
                boolean collapsed = !searching && collapsedCategories.getOrDefault(catRow.id, false);
                boolean isCurrentDir = catRow.id.equalsIgnoreCase(this.selectedCategory);
                boolean hoveringCat = mouseX >= guiX && mouseX <= guiX + sidebarWidth && mouseY >= currentY &&
                        mouseY < currentY + 12;

                long entryCount = catRow.id.equals(BOOKMARK_CAT_ID) ? LoreDataLoader.LORE_ENTRIES.values().stream()
                        .filter(e -> CLIENT_LORE_CACHE.getBoolean("bookmark:" + e.id())).count() :
                        LoreDataLoader.LORE_ENTRIES.values().stream()
                                .filter(e -> e.category().equalsIgnoreCase(catRow.id)).count();
                String badge = entryCount > 0 ? " §8(" + entryCount + ")" : "";

                boolean isBookmarkCat = catRow.id.equals(BOOKMARK_CAT_ID);
                
                int catColor = isBookmarkCat ? ArchivePalette.GOLD :
                        (isCurrentDir ? ArchivePalette.GOLD :
                                (hoveringCat ? ArchivePalette.TERM_BRIGHT : ArchivePalette.TERM));
                String collapsePrefix = collapsed ? "§8+ " : (isBookmarkCat ? "§6★ " : "§6- ");

                if (dragEntry != null && !isBookmarkCat && mouseY >= currentY && mouseY < currentY + 12) {
                    graphics.fill(guiX + 2, currentY, guiX + sidebarWidth - 2, currentY + 11,
                            ArchivePalette.withAlpha(ArchivePalette.TERM, 0x44));
                    catColor = ArchivePalette.TERM_HILITE;
                }
                graphics.drawString(this.font,
                        collapsePrefix + catRow.id + badge,
                        indentX, currentY, catColor);
                currentY += 12;

            } else if (row instanceof EntryRow) {
                EntryRow entryRow = (EntryRow) row;
                int indentX = guiX + 5 + entryRow.depth * 4;
                LoreEntry entry = entryRow.entry;
                boolean unlocked = isLoreUnlockedClient(entry);
                boolean isSelected = entry == selectedEntry;

                if (bulkMode && isEditMode) {
                    boolean checked = bulkSelected.contains(entry.id());
                    graphics.drawString(this.font, checked ? "§a[x]" : "§8[ ]", guiX + 2, currentY,
                            ArchivePalette.TERM_BRIGHT);
                }

                if (isSelected) {
                    graphics.fill(guiX + 10, currentY - 1, guiX + sidebarWidth - 5, currentY + 10,
                            ArchivePalette.withAlpha(ArchivePalette.TERM, 0x33));
                }

                if (dragEntry != null) {
                    if (mouseY >= currentY - 6 && mouseY < currentY + 6) {
                        graphics.fill(guiX + 10, currentY - 1, guiX + sidebarWidth - 10, currentY,
                                ArchivePalette.GOLD);
                    }
                }

                if (unlocked) {
                    
                    boolean starred = isBookmarked(entry);
                    int starX = guiX + sidebarWidth - 12;
                    int textMaxX = starX - 2; 
                    int textX = indentX + (bulkMode ? 14 : 2);

                    if (starred) {
                        graphics.drawString(this.font, "§6★", starX, currentY, ArchivePalette.GOLD, false);
                    }

                    String prefix = (isSelected ? "§f> " : "  ") + (isEditMode ? "§6✎ §7" : "");
                    String title = entry.title();
                    int maxTitlePx = textMaxX - textX - this.font.width(prefix);
                    while (title.length() > 1 && this.font.width(title) > maxTitlePx) {
                        title = title.substring(0, title.length() - 1);
                    }
                    if (!title.equals(entry.title())) title += "…";

                    int color = isSelected ? ArchivePalette.TERM_BRIGHT : ArchivePalette.TEXT_DIM;
                    graphics.drawString(this.font, prefix + title, textX, currentY, color, false);
                } else {
                    graphics.drawString(this.font,
                            (isSelected ? "> " : "  ") + "§c[DATA_LOCKED]",
                            indentX + (bulkMode ? 14 : 2), currentY, ArchivePalette.ALERT, false);
                }
                currentY += 12;
            }
        }

        if (dragEntry != null) {
            graphics.fill(guiX, (int) dragCurrentY - 1, guiX + sidebarWidth, (int) dragCurrentY + 10,
                    ArchivePalette.withAlpha(ArchivePalette.TERM_DIM, 0x88));
            graphics.drawString(this.font, "§a>> " + dragEntry.title(), guiX + 6, (int) dragCurrentY,
                    ArchivePalette.TERM_HILITE);
        }

        graphics.disableScissor();

        if (selectedEntry != null) {
            renderEntryContent(graphics, contentX, windowTop, contentWidth, mouseX, mouseY);
        } else {
            int centerX = contentX + (contentWidth / 2);
            int centerY = guiY + (guiHeight / 2);

            String breadcrumb = buildBreadcrumb(selectedCategory);
            graphics.drawCenteredString(this.font, "§6FOLDER: " + breadcrumb, centerX, centerY - 30,
                    ArchivePalette.TERM_BRIGHT);

            String desc = CategoryRegistry.getDescription(selectedCategory);
            if (!desc.isEmpty()) {
                var descLines = this.font.split(Component.literal("§7" + desc), contentWidth - 40);
                int lineY = centerY - 10;
                for (var line : descLines) {
                    graphics.drawCenteredString(this.font, line, centerX, lineY, ArchivePalette.TEXT_DIM);
                    lineY += 10;
                }
            }

            if (isEditMode) {
                graphics.drawCenteredString(this.font, "§8[ SELECTED DESTINATION FOR NEW ENTRIES ]", centerX,
                        centerY + 50, ArchivePalette.TEXT_FAINT);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private String buildBreadcrumb(String catId) {
        if (catId == null) return "";
        List<String> parts = new ArrayList<>();
        String current = catId.toUpperCase();
        int guard = 0;
        while (current != null && guard++ < 20) {
            parts.add(0, current);
            current = CategoryRegistry.getParentId(current);
        }
        return String.join(" §8/ §6", parts);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listWidth = 140;
        if (mouseX < guiX + listWidth) {
            int totalLines = sidebarRows.size();
            int max = Math.max(0, totalLines - ((guiHeight - 40) / 12));
            scrollOffset = (int) Math.max(0, Math.min(scrollOffset - (int) delta, max));
            return true;
        }

        if (selectedEntry != null) {
            boolean unlocked = isLoreUnlockedClient(selectedEntry);
            int contentWidth = guiWidth - listWidth - 25;
            List<RichBlock> blocks = resolveConditionals(parsedContent(selectedEntry, unlocked));
            int bodyH = ArchiveRichTextRenderer.measureBlocksHeight(this.font, blocks, contentWidth);
            int totalH = bodyH + (!unlocked ? 75 : 0);
            int maxPx = Math.max(0, totalH - (guiHeight - 90));
            int step = ArchiveRichTextRenderer.LINE_H;
            contentScrollOffset = Math.max(0, Math.min(contentScrollOffset - (int) delta * step, maxPx));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleContentClick(mouseX, mouseY)) return true;

        int sidebarWidth = 140;
        int currentY = guiY + 33 - (scrollOffset * 12);

        for (int rowIdx = 0; rowIdx < sidebarRows.size(); rowIdx++) {
            SidebarRow row = sidebarRows.get(rowIdx);
            if (row instanceof CatRow) {
                CatRow catRow = (CatRow) row;
                if (mouseX >= guiX && mouseX <= guiX + sidebarWidth && mouseY >= currentY && mouseY < currentY + 12) {

                    this.selectedCategory = catRow.id;
                    this.selectedEntry = null;
                    collapsedCategories.put(catRow.id, !collapsedCategories.getOrDefault(catRow.id, false));
                    this.minecraft.getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    refreshGroups();
                    return true;
                }
                currentY += 12;

            } else if (row instanceof EntryRow) {
                EntryRow entryRow = (EntryRow) row;
                LoreEntry entry = entryRow.entry;
                if (mouseX >= guiX && mouseX <= guiX + sidebarWidth && mouseY >= currentY && mouseY < currentY + 12) {

                    if (bulkMode && isEditMode && mouseX <= guiX + 16) {
                        if (bulkSelected.contains(entry.id())) bulkSelected.remove(entry.id());
                        else bulkSelected.add(entry.id());
                        this.init(this.minecraft, this.width, this.height);
                        return true;
                    }

                    this.selectedEntry = entry;
                    this.selectedCategory = entry.category();
                    this.contentScrollOffset = 0;

                    if (button == 1 && isEditMode) {
                        this.minecraft.getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                        this.minecraft.tell(
                                () -> this.minecraft.setScreen(new ArchiveEditorScreen(this, entry, entry.category())));
                    } else {
                        this.minecraft.getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        if (isEditMode && button == 0) {
                            
                            dragPending = entry;
                            dragPendingRowIndex = rowIdx;
                            dragPendingMouseY = mouseY;
                            dragPressTime = System.currentTimeMillis();
                        }
                        this.init(this.minecraft, this.width, this.height);
                    }
                    return true;
                }
                currentY += 12;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        
        if (dragEntry == null && dragPending != null && System.currentTimeMillis() - dragPressTime >= DRAG_HOLD_MS) {
            dragEntry = dragPending;
            dragRowIndex = dragPendingRowIndex;
            dragCurrentY = dragPendingMouseY;
            dragPending = null;
        }
        if (dragEntry != null) {
            dragCurrentY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        
        dragPending = null;
        dragPendingRowIndex = -1;

        if (dragEntry != null && button == 0) {
            int currentY = guiY + 33 - (scrollOffset * 12);
            int dropIndex = sidebarRows.size(); 
            for (int i = 0; i < sidebarRows.size(); i++) {
                SidebarRow row = sidebarRows.get(i);
                if (row instanceof CatRow cr) {
                    
                    if (!cr.id.equals(BOOKMARK_CAT_ID) && mouseY >= currentY && mouseY < currentY + 12) {
                        dropIndex = i;
                        break;
                    }
                } else if (row instanceof EntryRow) {
                    if (mouseY < currentY + 6) {
                        dropIndex = i;
                        break;
                    }
                }
                currentY += 12;
            }
            if (dropIndex != dragRowIndex) reorderEntryTo(dragEntry, dropIndex);
            dragEntry = null;
            dragRowIndex = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

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

        if (orderA == orderB) {
            orderB = orderA + (direction > 0 ? -1 : 1);
        }

        LoreEntry updatedA = new LoreEntry(entryA.id(), entryA.title(), entryA.category(),
                entryA.content(), entryA.iconItem(), entryA.questId(),
                entryA.lockedContent(), entryA.voiceLine(), entryA.conditionTree(), orderB,
                entryA.backgroundShader());
        LoreEntry updatedB = new LoreEntry(entryB.id(), entryB.title(), entryB.category(),
                entryB.content(), entryB.iconItem(), entryB.questId(),
                entryB.lockedContent(), entryB.voiceLine(), entryB.conditionTree(), orderA,
                entryB.backgroundShader());

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

    private boolean handleContentClick(double mouseX, double mouseY) {
        if (selectedEntry == null) return false;
        for (RichSpan.Region region : lastContentRegions) {
            if (!region.contains(mouseX, mouseY)) continue;
            RichSpan span = region.span();
            if (span instanceof RichSpan.Link l) {
                openLink(l.url());
                return true;
            } else if (span instanceof RichSpan.CodeCopy cc) {
                this.minecraft.keyboardHandler.setClipboard(cc.code());
                return true;
            } else if (span instanceof RichSpan.DetailsToggle dt) {
                if (!richExpandedKeys.remove(dt.key())) richExpandedKeys.add(dt.key());
                return true;
            } else if (span instanceof RichSpan.ChecklistToggle ct) {
                boolean current = richExpandedKeys.contains("CL1:" + ct.key()) ||
                        (!richExpandedKeys.contains("CL0:" + ct.key()) && ct.checkedDefault());
                boolean next = !current;
                richExpandedKeys.remove("CL1:" + ct.key());
                richExpandedKeys.remove("CL0:" + ct.key());
                richExpandedKeys.add((next ? "CL1:" : "CL0:") + ct.key());
                return true;
            }
        }
        return false;
    }

    private void openLink(String url) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("wiki:")) {
            String spec = url.substring(5);
            int slash = spec.indexOf('/');
            String namespace = slash >= 0 ? spec.substring(0, slash) : spec;
            String basePath = slash >= 0 ? spec.substring(slash + 1) : "";
            this.minecraft.setScreen(new WikiScreen(this, namespace, basePath));
            return;
        }
        if (url.startsWith("quest:")) {
            ArchiveClient.openChroniclesQuest(this, url.substring(6));
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {}
    }

    private void renderEntryContent(GuiGraphics graphics, int x, int top, int width, int contentMouseX,
                                    int contentMouseY) {
        boolean unlocked = isLoreUnlockedClient(selectedEntry);
        int textStartY = guiY + 55;
        int textHeight = guiHeight - 90;

        String bgShaderId = selectedEntry.backgroundShader();
        if (bgShaderId != null && !bgShaderId.isEmpty()) {
            ShaderInstance bgShader = ArchiveShaderManager.get(bgShaderId);
            if (bgShader != null) {
                int panelBottom = guiY + guiHeight - 5;
                float t = ArchiveShaderRenderUtil.wrappedSeconds(System.currentTimeMillis());
                ArchiveShaderRenderUtil.drawDynamicShaderQuad(graphics, bgShader, x, top, width,
                        panelBottom - top, t);
            }
        }

        int titleX = x;
        String iconItemStr = selectedEntry.iconItem();
        if (iconItemStr != null && !iconItemStr.isEmpty()) {
            try {
                ResourceLocation iconRes = new ResourceLocation(iconItemStr);
                var item = ForgeRegistries.ITEMS.getValue(iconRes);
                if (item != null && item != Items.AIR) {
                    graphics.renderFakeItem(new ItemStack(item), x, guiY + 28);
                } else {

                    graphics.fill(x, guiY + 28, x + 16, guiY + 44,
                            ArchivePalette.withAlpha(ArchivePalette.ALERT, 0x33));
                    graphics.renderOutline(x, guiY + 28, 16, 16, ArchivePalette.ALERT);
                    graphics.drawCenteredString(this.font, "?", x + 8, guiY + 32, ArchivePalette.ALERT);
                }
                titleX = x + 18;
            } catch (Exception ignored) {}
        }

        graphics.drawString(this.font, unlocked ? "§6" + selectedEntry.title().toUpperCase() : "§4[ENCRYPTED]",
                titleX, guiY + 30, ArchivePalette.TERM_BRIGHT);
        
        graphics.drawString(this.font, "§8CAT: " + buildBreadcrumb(selectedEntry.category()),
                titleX, guiY + 40, ArchivePalette.TEXT_FAINT);

        List<RichBlock> blocks = resolveConditionals(parsedContent(selectedEntry, unlocked));
        int bodyH = ArchiveRichTextRenderer.measureBlocksHeight(this.font, blocks, width,
                ArchiveRichTextRenderer.DEFAULT_SCALE, richExpandedKeys);
        int totalH = bodyH + (!unlocked ? 75 : 0);
        int maxScrollPx = Math.max(0, totalH - textHeight);
        contentScrollOffset = Math.min(contentScrollOffset, maxScrollPx);

        graphics.enableScissor(x, textStartY, x + width + 5, textStartY + textHeight);
        if (unlocked && blocks.isEmpty()) {
            renderTornPage(graphics, x, textStartY, width, textHeight, selectedEntry.id());
            lastContentRegions = List.of();
        } else {
            lastContentRegions = ArchiveRichTextRenderer.renderBlocks(graphics, this.font, blocks, x,
                    textStartY, width, contentScrollOffset, textStartY, textStartY + textHeight,
                    ArchiveRichTextRenderer.DEFAULT_SCALE, ArchivePalette.TERM, richExpandedKeys);
            for (RichSpan.Region region : lastContentRegions) {
                if (region.span() instanceof RichSpan.Tip tip && region.contains(contentMouseX, contentMouseY)) {
                    graphics.renderTooltip(this.font, Component.literal(tip.tooltip()), contentMouseX, contentMouseY);
                    break;
                }
            }
        }

        if (!unlocked) {
            renderRequisitionBox(graphics, x, textStartY - contentScrollOffset + bodyH + 10);
        }
        graphics.disableScissor();

        if (totalH > textHeight) {
            renderScrollbar(graphics, guiX + guiWidth - 10, textStartY, textHeight, maxScrollPx);
        }
    }

    private List<RichBlock> parsedContent(LoreEntry entry, boolean unlocked) {
        if (cachedContentEntry != entry || cachedContentUnlocked != unlocked) {
            String raw = unlocked ? entry.content() : entry.lockedContent();
            cachedContentBlocks = ArchiveMarkdownParser.parse(raw == null ? "" : raw);
            cachedContentEntry = entry;
            cachedContentUnlocked = unlocked;
        }
        return cachedContentBlocks;
    }

    private List<RichBlock> resolveConditionals(List<RichBlock> blocks) {
        List<RichBlock> out = new ArrayList<>(blocks.size());
        for (RichBlock b : blocks) {
            if (b instanceof RichBlock.ConditionalSection cs) {
                boolean met = ConditionEvaluator.evaluate(cs.condition(), this::isConditionMetClient);
                out.addAll(resolveConditionals(met ? cs.thenChildren() : cs.elseChildren()));
            } else if (b instanceof RichBlock.Callout c) {
                out.add(new RichBlock.Callout(c.type(), c.title(), resolveConditionals(c.children())));
            } else if (b instanceof RichBlock.Details d) {
                out.add(new RichBlock.Details(d.expandKey(), d.title(), resolveConditionals(d.children())));
            } else if (b instanceof RichBlock.CollapsibleSection s) {
                out.add(new RichBlock.CollapsibleSection(s.level(), s.headingSpans(), s.collapseKey(),
                        resolveConditionals(s.children())));
            } else {
                out.add(b);
            }
        }
        return out;
    }

    private boolean isConditionMetClient(String key, String value) {
        if (value == null || value.isEmpty()) return false;
        if (key.equalsIgnoreCase("chronicles_quest")) {

            if (CLIENT_LORE_CACHE.getBoolean("chronicles_quest:" + value)) return true;
            return ModList.get().isLoaded("phoenix_chronicles") && this.minecraft.player != null &&
                    ChroniclesQuestBridge.isCompleted(this.minecraft.player, value);
        }
        if (key.equalsIgnoreCase("ftb_quest")) {
            if (CLIENT_LORE_CACHE.getBoolean("ftb_quest:" + value)) return true;
            try {
                return QuestHelper.isQuestCompleted(Long.parseLong(value));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (key.equalsIgnoreCase("external")) {
            ExternalCondition.Parsed p = ExternalCondition.parse(value);
            int count = CLIENT_LORE_CACHE.getInt("external_count:" + p.id());
            return ExternalCondition.test(count, p.op(), p.threshold());
        }
        return CLIENT_LORE_CACHE.getBoolean(key + ":" + value);
    }

    private void renderTornPage(GuiGraphics g, int x, int y, int width, int height, String entryId) {
        ShaderInstance shader = ArchiveShaderManager.get(ArchiveClient.TORN_PAGE_SHADER_ID);
        if (shader != null) {
            float t = ArchiveShaderRenderUtil.wrappedSeconds(System.currentTimeMillis());
            ArchiveShaderRenderUtil.drawDynamicShaderQuad(g, shader, x, y, width, height, t);
        } else {
            g.fill(x, y, x + width, y + height, ArchivePalette.BG);
        }

        int tearY = y + height / 2;
        Random rng = new Random(entryId == null ? 0 : entryId.hashCode());

        int segW = 10;
        int cx = x;
        int offset = 0;
        while (cx < x + width) {
            int segEndX = Math.min(cx + segW, x + width);
            offset = Math.max(-6, Math.min(6, offset + rng.nextInt(7) - 3));
            g.fill(cx, tearY + offset, segEndX, tearY + offset + 2, ArchivePalette.BORDER);
            cx = segEndX;
        }

        g.drawCenteredString(this.font, "§8⌀ DATA_FRAGMENT_MISSING", x + width / 2, tearY - 12,
                ArchivePalette.TEXT_FAINT);
    }

    private void renderRequisitionBox(GuiGraphics g, int x, int y) {
        var leaves = selectedEntry.conditionTree().collectLeaves();
        int questId = selectedEntry.questId();

        int lineCount = 1;
        if (questId != 0) lineCount++;
        for (var nl : leaves) {
            if (!nl.leaf().value().isEmpty()) lineCount++;
        }

        g.fill(x - 2, y - 2, guiX + guiWidth - 15, y + 4 + (lineCount * 10), ArchivePalette.ALERT_FILL);
        g.drawString(this.font, "§c>> REQUISITION:", x + 2, y, ArchivePalette.TERM_BRIGHT);

        int offset = 12;

        if (questId != 0) {
            boolean met = QuestHelper.isQuestCompleted(questId);
            g.drawString(this.font, (met ? "§a" : "§7") + "- Quest: §f" + questId, x + 5, y + offset,
                    ArchivePalette.TEXT_DIM);
            offset += 10;
        }

        for (var nl : leaves) {
            String value = nl.leaf().value();
            if (value == null || value.isEmpty()) continue;
            boolean met = isConditionMetClient(nl.leaf().type(), value);
            if (nl.negated()) met = !met;
            String typeLabel = (nl.negated() ? "NOT " : "") + getConditionTypeLabel(nl.leaf().type());
            String valueLabel = nl.leaf().type().equalsIgnoreCase("external") ?
                    externalProgressLabel(value) : prettifyId(value);
            g.drawString(this.font, (met ? "§a" : "§7") + "- " + typeLabel + ": §f" + valueLabel,
                    x + 5, y + offset, ArchivePalette.TEXT_DIM);
            offset += 10;
        }
    }

    static String externalProgressLabel(String leafValue) {
        ExternalCondition.Parsed p = ExternalCondition.parse(leafValue);
        int count = CLIENT_LORE_CACHE.getInt("external_count:" + p.id());
        return prettifyId(p.id()) + " §8(" + count + " " + p.op() + " " + p.threshold() + ")";
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
        g.fill(x, y, x + 2, y + h, ArchivePalette.withAlpha(ArchivePalette.TERM_BRIGHT, 0x22));
        float pct = (float) contentScrollOffset / (float) Math.max(1, max);
        g.fill(x - 1, y + (int) (pct * (h - 15)), x + 3, y + (int) (pct * (h - 15)) + 15,
                ArchivePalette.TERM_BRIGHT);
    }

    private boolean isLoreUnlockedClient(LoreEntry entry) {
        boolean hasConditions = !entry.conditionTree().isEmpty();
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

    static String prettifyId(String id) {
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

    private boolean isBookmarked(LoreEntry entry) {
        return CLIENT_LORE_CACHE.getBoolean("bookmark:" + entry.id());
    }

    private void toggleBookmark(LoreEntry entry) {
        String key = "bookmark:" + entry.id();
        boolean next = !CLIENT_LORE_CACHE.getBoolean(key);

        CLIENT_LORE_CACHE.putBoolean(key, next);

        if (this.minecraft != null && this.minecraft.player != null) {
            CompoundTag pd = this.minecraft.player.getPersistentData().getCompound("PhoenixArchive");
            pd.putBoolean(key, next);
            this.minecraft.player.getPersistentData().put("PhoenixArchive", pd);
            this.lastPhoenixData = pd.copy();

        } else {
            PhoenixArchive.LOGGER
                    .warn("[Bookmark] minecraft or player is null — skipping persistentData write");
        }

        if (this.minecraft != null && this.minecraft.hasSingleplayerServer()) {
            PhoenixArchive.LOGGER
                    .info("[Bookmark] singleplayer path — getting integrated server");
            net.minecraft.server.MinecraftServer server = this.minecraft.getSingleplayerServer();
            if (server != null && this.minecraft.player != null) {
                final java.util.UUID uuid = this.minecraft.player.getUUID();
                final boolean finalNext = next;

                server.execute(() -> {

                    LoreSavedData data = LoreSavedData
                            .get(server.overworld());
                    if (finalNext) data.unlock(uuid, key);
                    else data.relock(uuid, key);

                    server.overworld().getDataStorage().save();

                });
            } else {

            }
        } else {

            PhoenixNetwork.CHANNEL.sendToServer(new BookmarkPacket(entry.id(), next));
        }

        this.init(this.minecraft, this.width, this.height);
    }

    private void duplicateEntry(LoreEntry source) {
        String newId = source.id() + "_copy";
        String newTitle = source.title() + " (copy)";
        
        this.minecraft.setScreen(new ArchiveEditorScreen(this,
                new LoreEntry(newId, newTitle, source.category(),
                        source.content(), source.iconItem(), 0,
                        source.lockedContent(), source.voiceLine(),
                        source.conditionTree(),
                        LoreDataLoader.LORE_ENTRIES.size(), source.backgroundShader()),
                source.category()));
    }

    private void bulkRecategorise(String targetCategory) {
        if (targetCategory == null || bulkSelected.isEmpty()) return;
        for (String entryId : bulkSelected) {
            LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                    .filter(e -> e.getValue().id().equals(entryId))
                    .findFirst()
                    .ifPresent(mapEntry -> {
                        LoreEntry old = mapEntry.getValue();
                        LoreEntry updated = new LoreEntry(old.id(), old.title(), targetCategory,
                                old.content(), old.iconItem(), old.questId(),
                                old.lockedContent(), old.voiceLine(),
                                old.conditionTree(), old.order(), old.backgroundShader());
                        LoreDataLoader.LORE_ENTRIES.put(mapEntry.getKey(), updated);
                        saveToConfig(updated, mapEntry.getKey().getPath());
                    });
        }
        bulkSelected.clear();
        bulkMode = false;
        refreshGroups();
        this.init(this.minecraft, this.width, this.height);
    }

    private void reorderEntryTo(LoreEntry entry, int dropIndex) {
        
        String targetCat = entry.category(); 
        LoreEntry insertBefore = null;            

        if (dropIndex >= 0 && dropIndex < sidebarRows.size()) {
            SidebarRow targetRow = sidebarRows.get(dropIndex);
            if (targetRow instanceof CatRow cr) {
                targetCat = cr.id;
                insertBefore = null; 
            } else if (targetRow instanceof EntryRow er) {
                targetCat = er.entry.category();
                insertBefore = er.entry;
            }
        }

        final String finalTargetCat = targetCat;
        LoreEntry resolvedEntry = entry; 
        if (!entry.category().equalsIgnoreCase(targetCat)) {
            ResourceLocation entryKey = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                    .filter(e -> e.getValue().id().equals(entry.id()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (entryKey == null) return;
            LoreEntry moved = new LoreEntry(entry.id(), entry.title(), finalTargetCat,
                    entry.content(), entry.iconItem(), entry.questId(),
                    entry.lockedContent(), entry.voiceLine(), entry.conditionTree(), Integer.MAX_VALUE,
                    entry.backgroundShader());
            LoreDataLoader.LORE_ENTRIES.put(entryKey, moved);
            resolvedEntry = moved;
        }
        final LoreEntry workEntry = resolvedEntry; 

        List<LoreEntry> catEntries = LoreDataLoader.LORE_ENTRIES.values().stream()
                .filter(e -> e.category().equalsIgnoreCase(finalTargetCat))
                .sorted(Comparator.comparingInt(LoreEntry::order))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        catEntries.removeIf(e -> e.id().equals(workEntry.id()));

        int insertIdx = catEntries.size(); 
        if (insertBefore != null) {
            for (int i = 0; i < catEntries.size(); i++) {
                if (catEntries.get(i).id().equals(insertBefore.id())) {
                    insertIdx = i;
                    break;
                }
            }
        }
        catEntries.add(insertIdx, workEntry);

        for (int i = 0; i < catEntries.size(); i++) {
            LoreEntry e = catEntries.get(i);
            if (e.order() == i && e.category().equalsIgnoreCase(finalTargetCat)) continue;
            LoreEntry updated = new LoreEntry(e.id(), e.title(), finalTargetCat,
                    e.content(), e.iconItem(), e.questId(),
                    e.lockedContent(), e.voiceLine(), e.conditionTree(), i, e.backgroundShader());
            ResourceLocation key = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                    .filter(en -> en.getValue().id().equals(e.id()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (key != null) {
                LoreDataLoader.LORE_ENTRIES.put(key, updated);
                saveToConfig(updated, key.getPath());
                if (e.id().equals(workEntry.id())) selectedEntry = updated;
            }
        }

        selectedCategory = finalTargetCat;
        refreshGroups();
        this.init(this.minecraft, this.width, this.height);
    }
}
