package net.phoenixvine.phoenix_archive.client;

import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.phoenix_archive.api.CategoryDefinition;
import net.phoenixvine.phoenix_archive.api.CategoryRegistry;

import java.io.File;
import java.io.FileWriter;

public class CategoryManagementScreen extends Screen {
    private final Screen lastScreen;
    /** If non-null, we are editing an existing category rather than creating a new one. */
    private final String editingId;

    private EditBox idBox, descBox, weightBox;

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

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 50;

        idBox = new EditBox(this.font, x, y, 200, 20, Component.empty());
        idBox.setHint(Component.literal("Category ID (e.g. MATERIALS)"));

        descBox = new EditBox(this.font, x, y + 30, 200, 20, Component.empty());
        descBox.setHint(Component.literal("Broad Explanation..."));

        weightBox = new EditBox(this.font, x, y + 60, 40, 20, Component.empty());
        weightBox.setHint(Component.literal("Weight"));

        // Pre-populate fields if editing
        if (editingId != null) {
            idBox.setValue(editingId);
            idBox.setEditable(false); // ID is the stable key — don't allow renaming
            idBox.setTextColor(0x888888);
            descBox.setValue(CategoryRegistry.getDescription(editingId));
            weightBox.setValue(String.valueOf(CategoryRegistry.getWeight(editingId)));
        } else {
            weightBox.setValue("50");
        }

        this.addRenderableWidget(idBox);
        this.addRenderableWidget(descBox);
        this.addRenderableWidget(weightBox);

// 1. Keep the button label simple
        String label = (editingId != null) ? "§2UPDATE_CATEGORY" : "§2INITIALIZE_CATEGORY";

// 2. Define the tooltip logic separately or inline correctly
        Tooltip buttonTooltip = (editingId != null)
                ? Tooltip.create(Component.literal("Update Selected Category"))
                : Tooltip.create(Component.literal("Save New Category"));

        this.addRenderableWidget(Button.builder(Component.literal(label), b -> saveCategory())
                .bounds(x, y + 90, 200, 20)
                .tooltip(buttonTooltip)
                .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> this.minecraft.setScreen(lastScreen))
                .bounds(x, y + 115, 200, 16).build());
    }

    private void saveCategory() {
        String id = idBox.getValue().trim().toUpperCase();
        if (id.isEmpty()) return;

        String desc = descBox.getValue();
        String weightStr = weightBox.getValue().replaceAll("[^0-9]", "");
        int weight = weightStr.isEmpty() ? 50 : Integer.parseInt(weightStr);

        CategoryRegistry.register(id, desc, weight);
        saveCategoryToDisk(new CategoryDefinition(id, desc, weight));

        this.minecraft.setScreen(lastScreen);
    }

    private void saveCategoryToDisk(CategoryDefinition def) {
        File file = new File("config/phoenix_archive/categories/" + def.id().toLowerCase() + ".json");
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(def, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        this.renderBackground(graphics);
        String title = (editingId != null)
                ? "§6EDIT_CATEGORY: " + editingId
                : "§6NEW_CATEGORY_PROTOCOL";
        graphics.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 70, 0xFFFFFF);
        if (editingId != null) {
            graphics.drawCenteredString(this.font, "§8(ID cannot be changed)", this.width / 2, this.height / 2 - 58, 0x666666);
        }
        super.render(graphics, mouseX, mouseY, partial);
    }
}