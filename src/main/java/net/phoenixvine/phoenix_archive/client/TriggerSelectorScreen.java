package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TriggerSelectorScreen extends Screen {
    private final ArchiveEditorScreen parent;

    public TriggerSelectorScreen(ArchiveEditorScreen parent) {
        super(Component.literal("TRIGGER_SELECT"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 80;
        int y = this.height / 2 - 40;

        // Option: GregTech Multiblock
        this.addRenderableWidget(Button.builder(Component.literal("GT: MULTIBLOCK"), b -> {
            this.minecraft.setScreen(new GTLinkScreen(parent));
        }).bounds(x, y, 160, 20).build());

        // Option: Biome Discovery (Future expansion)
        this.addRenderableWidget(Button.builder(Component.literal("WORLD: BIOME"), b -> {
            // Future Biome Selector Screen
        }).bounds(x, y + 25, 160, 20).build());

        // Back Button
        this.addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> {
            this.minecraft.setScreen(parent);
        }).bounds(x, y + 60, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§2SELECT_TRIGGER_TYPE", this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partial);
    }
}
