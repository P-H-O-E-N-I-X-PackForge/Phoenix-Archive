package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConditionTunerScreen extends Screen {

    private final ArchiveEditorScreen parent;
    private final Map<String, String> conditions;

    // We use a helper class to keep track of the pairs of text boxes
    private final List<ConditionRow> rows = new ArrayList<>();
    private Button addBtn;

    public ConditionTunerScreen(ArchiveEditorScreen parent, Map<String, String> conditions) {
        super(Component.literal("Condition Tuner"));
        this.parent = parent;
        this.conditions = conditions;
    }

    // ... (imports and class declaration remain the same)

    @Override
    protected void init() {
        this.rows.clear();
        this.clearWidgets();

        // 1. EXPANDED TEMPLATE: Shows all base triggers if the map is empty
        if (conditions.isEmpty()) {
            conditions.put("machine", "");
            conditions.put("dimension", "");
            conditions.put("biome", "");
            conditions.put("item", "");
            conditions.put("kill", "");
            conditions.put("craft", "");
            conditions.put("wearing", "");
            conditions.put("event", "");
        }

        int startX = this.width / 2 - 160;
        int startY = 40;
        int index = 0;

        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            createRow(startX, startY + (index * 25), entry.getKey(), entry.getValue());
            index++;
        }

        // Add Button
        addBtn = Button.builder(Component.literal("§6[+] ADD NEW"), b -> {
            saveRowsToMap();
            conditions.put("new_trigger", ""); // Empty value for dark hint
            this.init(this.minecraft, this.width, this.height);
        }).bounds(startX, startY + (index * 25), 320, 20).build();
        this.addRenderableWidget(addBtn);

        // Apply Button
        this.addRenderableWidget(Button.builder(Component.literal("§2[ APPLY_CHANGES ]"), b -> {
            saveRowsToMap();
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private void createRow(int x, int y, String key, String value) {
        // Key Box
        EditBox keyBox = new EditBox(this.font, x, y, 100, 20, Component.literal("Key"));
        if (key.startsWith("new_trigger") || key.equals("condition_type")) {
            keyBox.setValue("");
            keyBox.setHint(Component.literal("§8trigger_type")); // Dark Hint
        } else {
            keyBox.setValue(key);
        }

        // Value Box
        EditBox valBox = new EditBox(this.font, x + 105, y, 190, 20, Component.literal("Value"));

        if (value.isEmpty()) {
            // 2. ALL BASE TRIGGERS IN HINTS
            String hintText = switch (keyBox.getValue()) {
                case "machine" -> "gtceu:macerator";
                case "dimension" -> "minecraft:the_end";
                case "biome" -> "minecraft:deep_dark";
                case "item" -> "minecraft:diamond";
                case "kill" -> "minecraft:warden";
                case "craft" -> "minecraft:netherite_pickaxe";
                case "wearing" -> "minecraft:netherite_chestplate";
                case "event" -> "custom_signal_id";
                default -> "modid:path_or_id";
            };
            valBox.setHint(Component.literal("§8" + hintText)); // Dark Hint
        } else {
            valBox.setValue(value);
        }

        // Delete Button
        Button delBtn = Button.builder(Component.literal("§cX"), b -> {
            saveRowsToMap();
            conditions.remove(key);
            this.init(this.minecraft, this.width, this.height);
        }).bounds(x + 300, y, 20, 20).build();

        this.addRenderableWidget(keyBox);
        this.addRenderableWidget(valBox);
        this.addRenderableWidget(delBtn);

        rows.add(new ConditionRow(keyBox, valBox));
    }

    private void saveRowsToMap() {
        // Clear old and repopulate from the current UI state
        conditions.clear();
        for (ConditionRow row : rows) {
            String k = row.key.getValue().trim();
            String v = row.val.getValue().trim();
            if (!k.isEmpty()) {
                conditions.put(k, v);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        graphics.fill(this.width / 2 - 170, 10, this.width / 2 + 170, this.height - 10, 0xEE050505);
        graphics.renderOutline(this.width / 2 - 170, 10, 340, this.height - 20, 0xFF00AA00);

        graphics.drawCenteredString(this.font, "PHOENIX_OS // CONDITION_TUNER", this.width / 2, 20, 0x00FF00);
        graphics.drawString(this.font, "§7CONDITION_KEY", this.width / 2 - 160, 30, 0xAAAAAA);
        graphics.drawString(this.font, "§7EXPECTED_VALUE", this.width / 2 - 55, 30, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    // Small record to keep the box pairs together
    private record ConditionRow(EditBox key, EditBox val) {}
}
