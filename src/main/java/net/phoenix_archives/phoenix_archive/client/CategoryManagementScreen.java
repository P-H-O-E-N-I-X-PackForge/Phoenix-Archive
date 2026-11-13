package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenix_archives.phoenix_archive.api.CategoryDefinition;
import net.phoenix_archives.phoenix_archive.api.CategoryRegistry;
import net.phoenix_archives.phoenix_archive.common.ArchiveLang;
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
        super(ArchiveLang.tr("phoenix_archive.category_management_screen.category_manager"));
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
        idBox.setHint(ArchiveLang.tr("phoenix_archive.category_management_screen.category_id_e_g_materials"));
        idBox.setValue(idText);
        if (editingId != null) {
            idBox.setEditable(false);
            idBox.setTextColor(ArchivePalette.TEXT_FAINT);
        }

        descBox = new EditBox(this.font, x, y + 25, 200, 20, Component.empty());
        descBox.setMaxLength(512);
        descBox.setHint(ArchiveLang.tr("phoenix_archive.category_management_screen.broad_explanation"));
        descBox.setValue(descText);

        weightBox = new EditBox(this.font, x, y + 50, 40, 20, Component.empty());
        weightBox.setHint(ArchiveLang.tr("phoenix_archive.category_management_screen.weight"));
        weightBox.setValue(weightText);

        this.addRenderableWidget(idBox);
        this.addRenderableWidget(descBox);
        this.addRenderableWidget(weightBox);

        String parentLabel = (selectedParentId == null) ? "(none — root)" : selectedParentId;

        this.addRenderableWidget(ArchiveButton.create(
                ArchiveLang.tr("phoenix_archive.category_management_screen.parent_change", parentLabel),
                b -> openParentPicker())
                .bounds(x + 45, y + 50, 155, 20)
                .tooltip(Tooltip.create(
                        ArchiveLang.tr("phoenix_archive.category_management_screen.set_parent_category_opens_a")))
                .build());

        String saveLabel = (editingId != null) ?
                ArchiveText.str("phoenix_archive.category_management.update_category") :
                ArchiveText.str("phoenix_archive.category_management.initialize_category");
        Tooltip saveTooltip = (editingId != null) ?
                Tooltip.create(ArchiveLang.tr("phoenix_archive.category_management_screen.update_selected_category")) :
                Tooltip.create(ArchiveLang.tr("phoenix_archive.category_management_screen.save_new_category"));

        this.addRenderableWidget(ArchiveButton.create(Component.literal(saveLabel), b -> saveCategory())
                .bounds(x, y + 80, 200, 20)
                .tooltip(saveTooltip)
                .build());

        this.addRenderableWidget(ArchiveButton.create(
                ArchiveLang.tr("phoenix_archive.category_management_screen.cancel"),
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

        String title = (editingId != null) ?
                ArchiveText.str("phoenix_archive.category_management.edit_category", editingId) :
                ArchiveText.str("phoenix_archive.category_management.new_category_protocol");

        graphics.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 80,
                ArchivePalette.TERM_BRIGHT);

        if (editingId != null) {
            graphics.drawCenteredString(this.font,
                    ArchiveText.str("phoenix_archive.category_management.id_cannot_be_changed"),
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
