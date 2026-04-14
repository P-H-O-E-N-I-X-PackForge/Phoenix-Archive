package net.phoenix.phoenix_archive.client;


import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConditionTunerScreen extends Screen {
    private final ArchiveEditorScreen parent;
    private EditBox gtBox, dimBox, bioBox;

    public ConditionTunerScreen(ArchiveEditorScreen parent) {
        super(Component.literal("Condition Tuner"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;

        // GregTech Machine ID Field
        gtBox = new EditBox(this.font, x, 45, 200, 20, Component.literal("GT Machine"));
        gtBox.setHint(Component.literal("§8gtceu:machine_id..."));
        gtBox.setValue(parent.savedMachineId);
        this.addRenderableWidget(gtBox);

        // Dimension ID Field
        dimBox = new EditBox(this.font, x, 85, 200, 20, Component.literal("Dimension"));
        dimBox.setHint(Component.literal("§8minecraft:the_nether..."));
        dimBox.setValue(parent.savedDimId);
        this.addRenderableWidget(dimBox);

        // Biome ID Field
        bioBox = new EditBox(this.font, x, 125, 200, 20, Component.literal("Biome"));
        bioBox.setHint(Component.literal("§8minecraft:deep_dark..."));
        bioBox.setValue(parent.savedBiomeId);
        this.addRenderableWidget(bioBox);

        // Apply Button
        this.addRenderableWidget(Button.builder(Component.literal("§2[ APPLY_LOGIC ]"), b -> {
            parent.savedMachineId = gtBox.getValue();
            parent.savedDimId = dimBox.getValue();
            parent.savedBiomeId = bioBox.getValue();
            this.minecraft.setScreen(parent);
        }).bounds(x, 160, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        graphics.fill(this.width/2 - 120, 20, this.width/2 + 120, 190, 0xEE111111);
        graphics.renderOutline(this.width/2 - 120, 20, 240, 170, 0xFF00AA00);

        graphics.drawString(this.font, "DISCOVERY_CONDITION_TUNER", this.width/2 - 110, 30, 0x00FF00);
        graphics.drawString(this.font, "§7GT_MACHINE_ID", this.width/2 - 100, 35, 0xFFFFFF);
        graphics.drawString(this.font, "§7DIMENSION_ID", this.width/2 - 100, 75, 0xFFFFFF);
        graphics.drawString(this.font, "§7BIOME_ID", this.width/2 - 100, 115, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
