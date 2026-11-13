package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenix_archives.phoenix_archive.common.ArchiveLang;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;

public class GTLinkScreen extends Screen {

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    private final ArchiveEditorScreen parent;
    private EditBox machineInput;
    private String status = ArchiveText.str("phoenix_archive.g_t_link.awaiting_gt_id");

    public GTLinkScreen(ArchiveEditorScreen parent) {
        super(ArchiveLang.tr("phoenix_archive.g_t_link_screen.gt_link_terminal"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        int x = this.width / 2 - 100;

        machineInput = new EditBox(this.font, x, this.height / 2 - 10, 200, 20, Component.empty());
        machineInput.setHint(ArchiveLang.tr("phoenix_archive.g_t_link_screen.gtceu_macerator"));

        String existing = parent.getLeafValue("machine");
        machineInput.setValue(existing);

        this.addRenderableWidget(machineInput);

        this.addRenderableWidget(
                ArchiveButton.create(ArchiveLang.tr("phoenix_archive.g_t_link_screen.verify_id"), b -> {
                    ResourceLocation loc = ResourceLocation.tryParse(machineInput.getValue());
                    if (loc != null && BuiltInRegistries.BLOCK.containsKey(loc)) {
                        status = ArchiveText.str("phoenix_archive.g_t_link.id_valid", loc.getPath());
                    } else {
                        status = ArchiveText.str("phoenix_archive.g_t_link.id_not_found_in_registry");
                    }
                }).bounds(x, this.height / 2 + 15, 95, 20).build());

        this.addRenderableWidget(ArchiveButton.create(ArchiveLang.tr("phoenix_archive.g_t_link_screen.link"), b -> {
            String id = machineInput.getValue().trim();
            if (!id.isEmpty()) {

                parent.setLeafValue("machine", id);
                this.minecraft.setScreen(parent);
            }
        }).bounds(x + 105, this.height / 2 + 15, 95, 20).build());
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.fill(this.width / 2 - 120, this.height / 2 - 60, this.width / 2 + 120, this.height / 2 + 50,
                ArchivePalette.BG_SCRIM);
        graphics.renderOutline(this.width / 2 - 120, this.height / 2 - 60, 240, 110, ArchivePalette.TERM_BRIGHT);

        graphics.drawCenteredString(this.font, ArchiveText.str("phoenix_archive.g_t_link.gregtech_integration_unit"),
                this.width / 2, this.height / 2 - 50,
                ArchivePalette.TERM_BRIGHT);
        graphics.drawCenteredString(this.font,
                ArchiveText.str("phoenix_archive.g_t_link.registers_a_machine_condition_key"), this.width / 2,
                this.height / 2 - 40, ArchivePalette.TEXT_DIM);
        graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 25,
                ArchivePalette.TERM_BRIGHT);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
