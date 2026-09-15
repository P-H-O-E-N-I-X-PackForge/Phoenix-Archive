package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class TriggerSelectorScreen extends Screen {

    private final ArchiveEditorScreen parent;

    public TriggerSelectorScreen(ArchiveEditorScreen parent) {
        super(Component.literal("TRIGGER_SELECT"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        int x = this.width / 2 - 80;
        int y = this.height / 2 - 40;

        this.addRenderableWidget(Button.builder(Component.literal("GT: MULTIBLOCK"), b -> {
            this.minecraft.setScreen(new GTLinkScreen(parent));
        }).bounds(x, y, 160, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("WORLD: BIOME"), b -> {
            
        }).bounds(x, y + 25, 160, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("CANCEL"), b -> {
            this.minecraft.setScreen(parent);
        }).bounds(x, y + 60, 160, 20).build());
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§2SELECT_TRIGGER_TYPE", this.width / 2, this.height / 2 - 60,
                ArchivePalette.TERM_BRIGHT);
        super.render(graphics, mouseX, mouseY, partial);
    }
}
