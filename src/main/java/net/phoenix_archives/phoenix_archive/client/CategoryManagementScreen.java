package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenix_archives.phoenix_archive.api.CategoryDefinition;
import net.phoenix_archives.phoenix_archive.api.CategoryRegistry;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;

public class CategoryManagementScreen extends Screen {

    private final Screen lastScreen;

    private final String editingId;

    private String selectedParentId;

    private boolean parentInitialised = false;

    private EditBox idBox, descBox, weightBox;

    private String idText = "";
    private String descText = "";
    private String weightText = "50";

    public CategoryManagementScreen(Screen lastScreen) {
        this(lastScreen, null);
    }

    public CategoryManagementScreen(Screen lastScreen, String existingCategoryId) {
        super(Component.literal("Category Manager"));
        this.lastScreen = lastScreen;
        this.editingId = existingCategoryId;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 60;

        if (!parentInitialised) {
            if (editingId != null) {
                idText = editingId;
                descText = CategoryRegistry.getDescription(editingId);
                weightText = String.valueOf(CategoryRegistry.getWeight(editingId));
                selectedParentId = CategoryRegistry.getParentId(editingId);
            } else {
                idText = "";
                descText = "";
                weightText = "50";
                selectedParentId = null;
            }
            parentInitialised = true;
        }

        idBox = new EditBox(this.font, x, y, 200, 20, Component.empty());
        idBox.setHint(Component.literal("Category ID (e.g. MATERIALS)"));
        idBox.setValue(idText);
        if (editingId != null) {
            idBox.setEditable(false);
            idBox.setTextColor(ArchivePalette.TEXT_FAINT);
        }

        descBox = new EditBox(this.font, x, y + 25, 200, 20, Component.empty());
        descBox.setMaxLength(512);
        descBox.setHint(Component.literal("Broad Explanation..."));
        descBox.setValue(descText);

        weightBox = new EditBox(this.font, x, y + 50, 40, 20, Component.empty());
        weightBox.setHint(Component.literal("Weight"));
        weightBox.setValue(weightText);

        this.addRenderableWidget(idBox);
        this.addRenderableWidget(descBox);
        this.addRenderableWidget(weightBox);

        String parentLabel = (selectedParentId == null) ? "(none — root)" : selectedParentId;

        this.addRenderableWidget(Button.builder(
                Component.literal("§8Parent: §7" + parentLabel + " §8[change]"),
                b -> openParentPicker())
                .bounds(x + 45, y + 50, 155, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "Set parent category. Opens a picker screen.")))
                .build());

        String saveLabel = (editingId != null) ? "§2UPDATE_CATEGORY" : "§2INITIALIZE_CATEGORY";
        Tooltip saveTooltip = (editingId != null) ? Tooltip.create(Component.literal("Update Selected Category")) :
                Tooltip.create(Component.literal("Save New Category"));

        this.addRenderableWidget(Button.builder(Component.literal(saveLabel), b -> saveCategory())
                .bounds(x, y + 80, 200, 20)
                .tooltip(saveTooltip)
                .build());

        this.addRenderableWidget(Button.builder(
                Component.literal("CANCEL"),
                b -> this.minecraft.setScreen(lastScreen))
                .bounds(x, y + 105, 200, 16)
                .build());
    }

    private void openParentPicker() {
        snapshotWidgetValues();

        this.minecraft.setScreen(new ParentPickerScreen(
                this,
                selectedParentId,
                editingId,
                chosen -> {

                    this.selectedParentId = chosen;
                }));
    }

    @Override
    protected void repositionElements() {
        snapshotWidgetValues();
        this.init();
    }

    private void snapshotWidgetValues() {
        if (idBox != null) idText = idBox.getValue();
        if (descBox != null) descText = descBox.getValue();
        if (weightBox != null) weightText = weightBox.getValue();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);

        String title = (editingId != null) ? "§6EDIT_CATEGORY: " + editingId : "§6NEW_CATEGORY_PROTOCOL";

        graphics.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 80,
                ArchivePalette.TERM_BRIGHT);

        if (editingId != null) {
            graphics.drawCenteredString(this.font, "§8(ID cannot be changed)",
                    this.width / 2, this.height / 2 - 68, ArchivePalette.TEXT_FAINT);
        }

        super.render(graphics, mouseX, mouseY, partial);
    }

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
