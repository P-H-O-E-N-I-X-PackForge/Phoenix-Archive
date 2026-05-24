package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.phoenix_archive.api.CategoryDefinition;
import net.phoenixvine.phoenix_archive.api.CategoryRegistry;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;

public class CategoryManagementScreen extends Screen {

    private final Screen lastScreen;
    /** If non-null, we are editing an existing category rather than creating a new one. */
    private final String editingId;

    // -------------------------------------------------------------------------
    // Form state — lives on the instance, survives re-init
    // -------------------------------------------------------------------------

    /** The currently chosen parent ID. Null means root (no parent). */
    private String selectedParentId;
    /**
     * True once selectedParentId has been initialised from the registry (edit mode)
     * or defaulted to null (create mode). Prevents init() from overwriting a value
     * that was set by the ParentPickerScreen callback.
     */
    private boolean parentInitialised = false;

    // Widgets — re-created each init() but their text state is read back from the
    // instance fields below so it survives the clearWidgets() call done by the
    // engine on resize / re-init.
    private EditBox idBox, descBox, weightBox;

    /** Snapshots of widget text, kept in sync via snapshotWidgetValues(). */
    private String idText = "";
    private String descText = "";
    private String weightText = "50";

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Constructor for creating a brand-new category. */
    public CategoryManagementScreen(Screen lastScreen) {
        this(lastScreen, null);
    }

    /**
     * Constructor for editing an existing category.
     * Pass the category ID to pre-populate the fields.
     */
    public CategoryManagementScreen(Screen lastScreen, String existingCategoryId) {
        super(Component.literal("Category Manager"));
        this.lastScreen = lastScreen;
        this.editingId = existingCategoryId;
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 60;

        // First-time setup only: seed form state from the registry (edit) or defaults (create).
        // Must NOT run on re-init (e.g. returning from ParentPickerScreen or window resize),
        // otherwise the picker's result would be overwritten.
        if (!parentInitialised) {
            if (editingId != null) {
                idText = editingId;
                descText = CategoryRegistry.getDescription(editingId);
                weightText = String.valueOf(CategoryRegistry.getWeight(editingId));
                selectedParentId = CategoryRegistry.getParentId(editingId); // may be null
            } else {
                idText = "";
                descText = "";
                weightText = "50";
                selectedParentId = null;
            }
            parentInitialised = true;
        }

        // --- ID field ---
        idBox = new EditBox(this.font, x, y, 200, 20, Component.empty());
        idBox.setHint(Component.literal("Category ID (e.g. MATERIALS)"));
        idBox.setValue(idText);
        if (editingId != null) {
            idBox.setEditable(false);
            idBox.setTextColor(0x888888);
        }

        // --- Description field ---
        descBox = new EditBox(this.font, x, y + 25, 200, 20, Component.empty());
        descBox.setHint(Component.literal("Broad Explanation..."));
        descBox.setValue(descText);

        // --- Weight field ---
        weightBox = new EditBox(this.font, x, y + 50, 40, 20, Component.empty());
        weightBox.setHint(Component.literal("Weight"));
        weightBox.setValue(weightText);

        this.addRenderableWidget(idBox);
        this.addRenderableWidget(descBox);
        this.addRenderableWidget(weightBox);

        // --- Parent picker button ---
        // Label always reflects selectedParentId, which is written by the picker callback
        // before the engine calls init() again on return.
        String parentLabel = (selectedParentId == null) ? "(none — root)" : selectedParentId;

        this.addRenderableWidget(Button.builder(
                Component.literal("§8Parent: §7" + parentLabel + " §8[change]"),
                b -> openParentPicker())
                .bounds(x + 45, y + 50, 155, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Set parent category. Opens a picker screen.")))
                .build());

        // --- Save / Update button ---
        String saveLabel = (editingId != null) ? "§2UPDATE_CATEGORY" : "§2INITIALIZE_CATEGORY";
        Tooltip saveTooltip = (editingId != null) ? Tooltip.create(Component.literal("Update Selected Category")) :
                Tooltip.create(Component.literal("Save New Category"));

        this.addRenderableWidget(Button.builder(Component.literal(saveLabel), b -> saveCategory())
                .bounds(x, y + 80, 200, 20)
                .tooltip(saveTooltip)
                .build());

        // --- Cancel button ---
        this.addRenderableWidget(Button.builder(
                Component.literal("CANCEL"),
                b -> this.minecraft.setScreen(lastScreen))
                .bounds(x, y + 105, 200, 16)
                .build());
    }

    // -------------------------------------------------------------------------
    // Parent picker
    // -------------------------------------------------------------------------

    private void openParentPicker() {
        // Snapshot widget values before navigating away so they survive the re-init
        // that the engine triggers when we return to this screen.
        snapshotWidgetValues();

        this.minecraft.setScreen(new ParentPickerScreen(
                this,               // return here when done
                selectedParentId,   // pre-selected value shown in the picker
                editingId,          // excluded from the list (can't parent to yourself)
                chosen -> {
                    // Runs inside ParentPickerScreen.confirm(), before setScreen(returnTo).
                    // selectedParentId is therefore set before init() fires on return,
                    // and parentInitialised == true so init() won't overwrite it.
                    this.selectedParentId = chosen;
                }));
    }

    /** Reads current widget text into backing fields before navigating away. */
    private void snapshotWidgetValues() {
        if (idBox != null) idText = idBox.getValue();
        if (descBox != null) descText = descBox.getValue();
        if (weightBox != null) weightText = weightBox.getValue();
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        this.renderBackground(graphics);

        String title = (editingId != null) ? "§6EDIT_CATEGORY: " + editingId : "§6NEW_CATEGORY_PROTOCOL";

        graphics.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 80, 0xFFFFFF);

        if (editingId != null) {
            graphics.drawCenteredString(this.font, "§8(ID cannot be changed)",
                    this.width / 2, this.height / 2 - 68, 0x666666);
        }

        super.render(graphics, mouseX, mouseY, partial);
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    private void saveCategory() {
        snapshotWidgetValues();

        String id = idText.trim().toUpperCase();
        if (id.isEmpty()) return;

        String weightStr = weightText.replaceAll("[^0-9]", "");
        int weight = weightStr.isEmpty() ? 50 : Integer.parseInt(weightStr);

        CategoryRegistry.register(id, descText, weight, selectedParentId);
        saveCategoryToDisk(new CategoryDefinition(id, descText, weight, selectedParentId));

        this.minecraft.setScreen(lastScreen);
    }

    private void saveCategoryToDisk(CategoryDefinition def) {
        String safeName = def.id().toLowerCase().replaceAll("[^a-z0-9]", "_");
        File file = new File("config/phoenix_archive/categories/" + safeName + ".json");
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(def, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
