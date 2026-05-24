package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
import net.phoenixvine.phoenix_archive.network.BookmarkPacket;
import net.phoenixvine.phoenix_archive.network.PhoenixNetwork;

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

    // --- Search ---
    private EditBox searchBox;
    private String searchQuery = "";

    // --- Bookmarks (persisted in CLIENT_LORE_CACHE NBT under "bookmark:<id>") ---
    // No extra field needed; read/write directly via CLIENT_LORE_CACHE.

    // --- Drag-to-reorder ---
    /** Index into sidebarRows of the entry being dragged, or -1 if none. */
    private int dragRowIndex = -1;
    /** The entry being dragged. */
    private LoreEntry dragEntry = null;
    /** Current mouse Y while dragging, for drawing the drag ghost. */
    private double dragCurrentY = 0;

    /** Entry the mouse is held down on, waiting for the hold threshold. */
    private LoreEntry dragPending = null;
    private int dragPendingRowIndex = -1;
    private double dragPendingMouseY = 0;
    /** System time (ms) when the mouse was pressed on dragPending. */
    private long dragPressTime = 0;
    /** How long the user must hold before drag activates (ms). */
    private static final long DRAG_HOLD_MS = 300;

    // --- Bulk re-categorise ---
    /** Entry IDs checked for bulk operations. */
    private final Set<String> bulkSelected = new HashSet<>();
    /** Whether bulk-select mode is active. */
    private boolean bulkMode = false;

    /**
     * Ordered list of sidebar rows produced by {@link #refreshGroups()}.
     * Each row is either a category header or an entry line.
     */
    private final List<SidebarRow> sidebarRows = new ArrayList<>();

    /**
     * Flat ordered list of category IDs in DFS tree order, used by
     * {@link #shiftCategory(int)} to find siblings.
     */
    private final List<String> orderedCategories = new ArrayList<>();

    private int tickCounter = 0;
    private SoundInstance currentVoice = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int guiX, guiY;
    private int guiWidth = 420;
    private int guiHeight = 240;

    // -------------------------------------------------------------------------
    // Sidebar row model
    // -------------------------------------------------------------------------

    /** A single rendered row in the sidebar: either a category header or a lore entry. */
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

    // -------------------------------------------------------------------------
    // Construction / init
    // -------------------------------------------------------------------------

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

        // Search box — sits above the sidebar, always visible
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
            final LoreEntry voiceTarget = selectedEntry; // capture before lambda
            this.addRenderableWidget(
                    Button.builder(Component.literal("▶ PLAY VOICE"), b -> playLoreVoice(voiceTarget.voiceLine()))
                            .bounds(guiX + contentXOffset, guiY + guiHeight - 25, 100, 16)
                            .tooltip(Tooltip.create(Component.literal("Play Voice Entry")))
                            .build());
        }

        if (selectedEntry != null) {
            final LoreEntry pinnedEntry = selectedEntry; // capture — widget outlives field
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
                                    "archive-clipboard").start();
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
                final String pinnedCat = selectedCategory; // effectively final for all lambdas in this block

                this.addRenderableWidget(Button.builder(Component.literal("New Entry"),
                        b -> this.minecraft.setScreen(new ArchiveEditorScreen(null, pinnedCat)))
                        .bounds(guiX + guiWidth - 146, guiY + 6, 64, 14)
                        .tooltip(Tooltip.create(Component.literal("Open New Entry Screen")))
                        .build());

                if (selectedEntry != null) {
                    final LoreEntry pinnedEdit = selectedEntry;
                    // Don't show edit controls for entries viewed via the bookmark category —
                    // they belong to their real category, which is shown in the breadcrumb.
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

                // Bulk toggle — always visible in edit mode, not tied to entry selection
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

                // Bulk assign — shown in the content area so the target category is always visible
                if (bulkMode && !bulkSelected.isEmpty() && selectedCategory != null &&
                        !BOOKMARK_CAT_ID.equals(selectedCategory)) {
                    final String bulkTarget = selectedCategory; // effectively final for lambda
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

    // -------------------------------------------------------------------------
    // Category shifting (sibling-only)
    // -------------------------------------------------------------------------

    /**
     * Shifts {@link #selectedCategory} one position up or down among its siblings
     * (categories that share the same parent). Children of the moved category
     * travel with it automatically because they are sorted relative to their parent.
     */
    private void shiftCategory(int direction) {
        if (selectedCategory == null) return;

        String parentId = CategoryRegistry.getParentId(selectedCategory);
        // Get only the siblings — categories with the same parent, sorted by weight
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

    // -------------------------------------------------------------------------
    // Category persistence helpers
    // -------------------------------------------------------------------------

    private void saveCategoryMeta(String id, String desc, int weight, String parentId) {
        try {
            var def = new net.phoenixvine.phoenix_archive.api.CategoryDefinition(
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

    // -------------------------------------------------------------------------
    // Group / tree refresh
    // -------------------------------------------------------------------------

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

        // --- Bookmarked pseudo-category (always first) ---
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

        // --- Normal category tree ---
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

    /** True if any entry in this category or its descendants matches the current search query. */
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
        // When searching, skip categories with no matches
        if (!categoryHasSearchMatch(cat)) {
            visited.add(cat);
            return;
        }
        visited.add(cat);

        orderedCategories.add(cat);
        sidebarRows.add(new CatRow(cat, depth));

        // When searching, always expand; otherwise respect collapsed state
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

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int sidebarWidth = 140;
        int contentX = guiX + sidebarWidth + 12;
        int contentWidth = guiWidth - sidebarWidth - 25;

        graphics.fill(guiX, guiY, guiX + guiWidth, guiY + guiHeight, 0xEE050505);
        graphics.renderOutline(guiX, guiY, guiWidth, guiHeight, 0xFF00FF00);
        graphics.drawString(this.font, "> PHOENIX_OS " + ArchiveConfigs.INSTANCE.general.mainMenuName + "// ARCHIVE",
                guiX + 10, guiY + 8, 0x00FF00);

        // Search bar underline
        graphics.fill(guiX + 2, guiY + 29, guiX + 138, guiY + 30, 0x44FFFFFF);

        // --- SIDEBAR TREE ---
        int windowTop = guiY + 33; // 3px below the search underline
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

                // Entry count badge
                long entryCount = catRow.id.equals(BOOKMARK_CAT_ID) ? LoreDataLoader.LORE_ENTRIES.values().stream()
                        .filter(e -> CLIENT_LORE_CACHE.getBoolean("bookmark:" + e.id())).count() :
                        LoreDataLoader.LORE_ENTRIES.values().stream()
                                .filter(e -> e.category().equalsIgnoreCase(catRow.id)).count();
                String badge = entryCount > 0 ? " §8(" + entryCount + ")" : "";

                boolean isBookmarkCat = catRow.id.equals(BOOKMARK_CAT_ID);
                // Bookmark cat: gold; normal: green/white/orange
                int catColor = isBookmarkCat ? (collapsed ? 0xFFAA00 : 0xFFCC00) :
                        (isCurrentDir ? 0xFFAA00 : (hoveringCat ? 0xFFFFFF : 0x00AA00));
                String collapsePrefix = collapsed ? "§8+ " : (isBookmarkCat ? "§6★ " : "§6- ");

                // Highlight category as a drop target when dragging (not the bookmark pseudo-cat)
                if (dragEntry != null && !isBookmarkCat && mouseY >= currentY && mouseY < currentY + 12) {
                    graphics.fill(guiX + 2, currentY, guiX + sidebarWidth - 2, currentY + 11, 0x4400FF44);
                    catColor = 0x00FF88;
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

                // Bulk checkbox
                if (bulkMode && isEditMode) {
                    boolean checked = bulkSelected.contains(entry.id());
                    graphics.drawString(this.font, checked ? "§a[x]" : "§8[ ]", guiX + 2, currentY, 0xFFFFFF);
                }

                if (isSelected) {
                    graphics.fill(guiX + 10, currentY - 1, guiX + sidebarWidth - 5, currentY + 10, 0x3300FF00);
                }

                // Drag drop-target indicator — yellow line for entry slots, green tint for category targets
                if (dragEntry != null) {
                    if (mouseY >= currentY - 6 && mouseY < currentY + 6) {
                        graphics.fill(guiX + 10, currentY - 1, guiX + sidebarWidth - 10, currentY, 0xFFFFFF00);
                    }
                }

                if (unlocked) {
                    // Bookmark star — drawn at a fixed right margin, before title text
                    boolean starred = isBookmarked(entry);
                    int starX = guiX + sidebarWidth - 12;
                    int textMaxX = starX - 2; // entry title must not reach here
                    int textX = indentX + (bulkMode ? 14 : 2);

                    if (starred) {
                        graphics.drawString(this.font, "§6★", starX, currentY, 0xFFAA00, false);
                    }

                    // Truncate title so it never reaches the star column
                    String prefix = (isSelected ? "§f> " : "  ") + (isEditMode ? "§6✎ §7" : "");
                    String title = entry.title();
                    int maxTitlePx = textMaxX - textX - this.font.width(prefix);
                    while (title.length() > 1 && this.font.width(title) > maxTitlePx) {
                        title = title.substring(0, title.length() - 1);
                    }
                    if (!title.equals(entry.title())) title += "…";

                    int color = isSelected ? 0x00FF00 : 0xAAAAAA;
                    graphics.drawString(this.font, prefix + title, textX, currentY, color, false);
                } else {
                    graphics.drawString(this.font,
                            (isSelected ? "> " : "  ") + "§c[DATA_LOCKED]",
                            indentX + (bulkMode ? 14 : 2), currentY, 0xFF4444, false);
                }
                currentY += 12;
            }
        }

        // Drag ghost — floating label under the cursor
        if (dragEntry != null) {
            graphics.fill(guiX, (int) dragCurrentY - 1, guiX + sidebarWidth, (int) dragCurrentY + 10, 0x88004400);
            graphics.drawString(this.font, "§a>> " + dragEntry.title(), guiX + 6, (int) dragCurrentY, 0x00FF88);
        }

        graphics.disableScissor();

        // --- CONTENT PANEL ---
        if (selectedEntry != null) {
            renderEntryContent(graphics, contentX, windowTop, contentWidth);
        } else {
            int centerX = contentX + (contentWidth / 2);
            int centerY = guiY + (guiHeight / 2);

            // Show breadcrumb path for nested categories
            String breadcrumb = buildBreadcrumb(selectedCategory);
            graphics.drawCenteredString(this.font, "§6FOLDER: " + breadcrumb, centerX, centerY - 30, 0xFFFFFF);

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

    /**
     * Builds a "/" separated breadcrumb string for the given category ID,
     * e.g. "RESEARCH / BIOLOGY / FUNGI".
     */
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

    // -------------------------------------------------------------------------
    // Mouse interaction
    // -------------------------------------------------------------------------

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
            String raw = unlocked ? selectedEntry.content() : selectedEntry.lockedContent();
            if (raw == null) raw = "";
            var lines = this.font.split(Component.literal(raw.replace('§', '§')), guiWidth - 140 - 35);
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

                    // Bulk checkbox click (left side strip)
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
                                () -> this.minecraft.setScreen(new ArchiveEditorScreen(entry, entry.category())));
                    } else {
                        this.minecraft.getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        if (isEditMode && button == 0) {
                            // Record press — drag activates after DRAG_HOLD_MS in mouseDragged
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
        // Promote pending drag to active once the hold threshold is met
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
        // Always clear pending — release before threshold means it was just a click
        dragPending = null;
        dragPendingRowIndex = -1;

        if (dragEntry != null && button == 0) {
            int currentY = guiY + 33 - (scrollOffset * 12);
            int dropIndex = sidebarRows.size(); // default: end of list
            for (int i = 0; i < sidebarRows.size(); i++) {
                SidebarRow row = sidebarRows.get(i);
                if (row instanceof CatRow cr) {
                    // Drop onto a category header = append to that category (skip bookmark pseudo-cat)
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

    // -------------------------------------------------------------------------
    // Entry management (unchanged logic, kept for completeness)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Content panel rendering (unchanged)
    // -------------------------------------------------------------------------

    private void renderEntryContent(GuiGraphics graphics, int x, int top, int width) {
        boolean unlocked = isLoreUnlockedClient(selectedEntry);
        int textStartY = guiY + 55;
        int textHeight = guiHeight - 90;

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

        graphics.drawString(this.font, unlocked ? "§6" + selectedEntry.title().toUpperCase() : "§4[ENCRYPTED]",
                titleX, guiY + 30, 0xFFFFFF);
        // Show full breadcrumb path in entry detail view
        graphics.drawString(this.font, "§8CAT: " + buildBreadcrumb(selectedEntry.category()),
                titleX, guiY + 40, 0x888888);

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
            g.drawString(this.font, (met ? "§a" : "§7") + "- " + typeLabel + ": §f" + valueLabel,
                    x + 5, y + offset, 0xAAAAAA);
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

    // -------------------------------------------------------------------------
    // Unlock / tick / sound (unchanged)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Bookmarks
    // -------------------------------------------------------------------------

    private boolean isBookmarked(LoreEntry entry) {
        return CLIENT_LORE_CACHE.getBoolean("bookmark:" + entry.id());
    }

    private void toggleBookmark(LoreEntry entry) {
        String key = "bookmark:" + entry.id();
        boolean next = !CLIENT_LORE_CACHE.getBoolean(key);

        // net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER.info("[Bookmark] toggling key={} next={}", key, next);

        // 1. Optimistic update to CLIENT_LORE_CACHE so the star flips immediately.
        CLIENT_LORE_CACHE.putBoolean(key, next);

        // 2. Mirror into player.getPersistentData() AND update lastPhoenixData so
        // tick() doesn't see this as a server-driven change and clobber it.
        if (this.minecraft != null && this.minecraft.player != null) {
            CompoundTag pd = this.minecraft.player.getPersistentData().getCompound("PhoenixArchive");
            pd.putBoolean(key, next);
            this.minecraft.player.getPersistentData().put("PhoenixArchive", pd);
            this.lastPhoenixData = pd.copy();
            // net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER.info("[Bookmark] persistentData updated,
            // pd.contains={}", pd.contains(key));
        } else {
            net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER
                    .warn("[Bookmark] minecraft or player is null — skipping persistentData write");
        }

        // 3. Persist — write directly on integrated server, packet on dedicated.
        if (this.minecraft != null && this.minecraft.hasSingleplayerServer()) {
            net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER
                    .info("[Bookmark] singleplayer path — getting integrated server");
            net.minecraft.server.MinecraftServer server = this.minecraft.getSingleplayerServer();
            if (server != null && this.minecraft.player != null) {
                final java.util.UUID uuid = this.minecraft.player.getUUID();
                final boolean finalNext = next;
                // net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER.info("[Bookmark] scheduling server.execute for
                // uuid={}", uuid);
                server.execute(() -> {
                    // net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER.info("[Bookmark] server.execute running,
                    // finalNext={}", finalNext);
                    net.phoenixvine.phoenix_archive.common.LoreSavedData data = net.phoenixvine.phoenix_archive.common.LoreSavedData
                            .get(server.overworld());
                    if (finalNext) data.unlock(uuid, key);
                    else data.relock(uuid, key);
                    // Force immediate disk write rather than waiting for autosave,
                    // so bookmarks survive force-close and crashes.
                    server.overworld().getDataStorage().save();
                    // net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER.info("[Bookmark] after write: isUnlocked={}
                    // isDirty={}", data.isUnlocked(uuid, key), data.isDirty());
                });
            } else {
                // net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER.warn("[Bookmark] server={} player={} — skipping
                // server.execute", server, this.minecraft.player);
            }
        } else {
            // net.phoenixvine.phoenix_archive.PhoenixArchive.LOGGER.info("[Bookmark] dedicated server path — sending
            // packet");
            PhoenixNetwork.CHANNEL.sendToServer(new BookmarkPacket(entry.id(), next));
        }

        this.init(this.minecraft, this.width, this.height);
    }

    // -------------------------------------------------------------------------
    // Duplicate entry
    // -------------------------------------------------------------------------

    private void duplicateEntry(LoreEntry source) {
        String newId = source.id() + "_copy";
        String newTitle = source.title() + " (copy)";
        // Open the editor pre-populated — the user completes and saves it themselves
        this.minecraft.setScreen(new ArchiveEditorScreen(
                new LoreEntry(newId, newTitle, source.category(),
                        source.content(), source.iconItem(), 0,
                        source.lockedContent(), source.voiceLine(),
                        new HashMap<>(source.getConditions()),
                        LoreDataLoader.LORE_ENTRIES.size()),
                source.category()));
    }

    // -------------------------------------------------------------------------
    // Bulk re-categorise
    // -------------------------------------------------------------------------

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
                                old.getConditions(), old.order());
                        LoreDataLoader.LORE_ENTRIES.put(mapEntry.getKey(), updated);
                        saveToConfig(updated, mapEntry.getKey().getPath());
                    });
        }
        bulkSelected.clear();
        bulkMode = false;
        refreshGroups();
        this.init(this.minecraft, this.width, this.height);
    }

    // -------------------------------------------------------------------------
    // Drag-to-reorder
    // -------------------------------------------------------------------------

    /**
     * Moves {@code entry} to the position indicated by {@code dropIndex} in sidebarRows.
     * Handles both same-category reorder and cross-category reassignment:
     * - Drop onto a CatRow → append to end of that category
     * - Drop onto an EntryRow in any category → insert before that entry
     * - Drop past end of list → append to end of current category
     */
    private void reorderEntryTo(LoreEntry entry, int dropIndex) {
        // Resolve target category and insert-before entry from the drop row
        String targetCat = entry.category(); // default: stay in same category
        LoreEntry insertBefore = null;            // null = append to end of targetCat

        if (dropIndex >= 0 && dropIndex < sidebarRows.size()) {
            SidebarRow targetRow = sidebarRows.get(dropIndex);
            if (targetRow instanceof CatRow cr) {
                targetCat = cr.id;
                insertBefore = null; // append to end
            } else if (targetRow instanceof EntryRow er) {
                targetCat = er.entry.category();
                insertBefore = er.entry;
            }
        }

        // If category changed, persist the reassignment first
        final String finalTargetCat = targetCat;
        LoreEntry resolvedEntry = entry; // will be replaced below if category changes
        if (!entry.category().equalsIgnoreCase(targetCat)) {
            ResourceLocation entryKey = LoreDataLoader.LORE_ENTRIES.entrySet().stream()
                    .filter(e -> e.getValue().id().equals(entry.id()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (entryKey == null) return;
            LoreEntry moved = new LoreEntry(entry.id(), entry.title(), finalTargetCat,
                    entry.content(), entry.iconItem(), entry.questId(),
                    entry.lockedContent(), entry.voiceLine(), entry.getConditions(), Integer.MAX_VALUE);
            LoreDataLoader.LORE_ENTRIES.put(entryKey, moved);
            resolvedEntry = moved;
        }
        final LoreEntry workEntry = resolvedEntry; // effectively final — safe for lambdas

        // Build ordered list of the target category's entries (now includes the moved entry)
        List<LoreEntry> catEntries = LoreDataLoader.LORE_ENTRIES.values().stream()
                .filter(e -> e.category().equalsIgnoreCase(finalTargetCat))
                .sorted(Comparator.comparingInt(LoreEntry::order))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Remove the dragged entry so we can re-insert it at the right position
        catEntries.removeIf(e -> e.id().equals(workEntry.id()));

        int insertIdx = catEntries.size(); // default: end
        if (insertBefore != null) {
            for (int i = 0; i < catEntries.size(); i++) {
                if (catEntries.get(i).id().equals(insertBefore.id())) {
                    insertIdx = i;
                    break;
                }
            }
        }
        catEntries.add(insertIdx, workEntry);

        // Re-assign sequential order values and persist only changed entries
        for (int i = 0; i < catEntries.size(); i++) {
            LoreEntry e = catEntries.get(i);
            if (e.order() == i && e.category().equalsIgnoreCase(finalTargetCat)) continue;
            LoreEntry updated = new LoreEntry(e.id(), e.title(), finalTargetCat,
                    e.content(), e.iconItem(), e.questId(),
                    e.lockedContent(), e.voiceLine(), e.getConditions(), i);
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
