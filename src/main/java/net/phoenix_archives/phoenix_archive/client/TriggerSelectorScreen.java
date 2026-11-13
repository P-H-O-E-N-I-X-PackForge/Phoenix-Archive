package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.phoenix_archives.phoenix_archive.common.ArchiveLang;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class TriggerSelectorScreen extends Screen {

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    private final ArchiveEditorScreen parent;

    public TriggerSelectorScreen(ArchiveEditorScreen parent) {
        super(ArchiveLang.tr("phoenix_archive.trigger_selector_screen.trigger_select"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        int x = this.width / 2 - 80;
        int y = this.height / 2 - 40;

        this.addRenderableWidget(
                ArchiveButton.create(ArchiveLang.tr("phoenix_archive.trigger_selector_screen.gt_multiblock"), b -> {
                    this.minecraft.setScreen(new GTLinkScreen(parent));
                }).bounds(x, y, 160, 20).build());

        this.addRenderableWidget(
                ArchiveButton.create(ArchiveLang.tr("phoenix_archive.trigger_selector_screen.world_biome"), b -> {

                }).bounds(x, y + 25, 160, 20).build());

        this.addRenderableWidget(
                ArchiveButton.create(ArchiveLang.tr("phoenix_archive.category_management_screen.cancel"), b -> {
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
        this.renderBackground(graphics, mouseX, mouseY, partial);
        graphics.drawCenteredString(this.font, ArchiveText.str("phoenix_archive.trigger_selector.select_trigger_type"),
                this.width / 2, this.height / 2 - 60,
                ArchivePalette.TERM_BRIGHT);
        super.render(graphics, mouseX, mouseY, partial);
    }
}
