package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;

public class GTLinkScreen extends Screen {

    private final ArchiveEditorScreen parent;
    private EditBox machineInput;
    private String status = "§8AWAITING_GT_ID...";

    public GTLinkScreen(ArchiveEditorScreen parent) {
        super(Component.literal("GT_LINK_TERMINAL"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        int x = this.width / 2 - 100;

        machineInput = new EditBox(this.font, x, this.height / 2 - 10, 200, 20, Component.empty());
        machineInput.setHint(Component.literal("gtceu:macerator..."));

        String existing = parent.getLeafValue("machine");
        machineInput.setValue(existing);

        this.addRenderableWidget(machineInput);

        this.addRenderableWidget(Button.builder(Component.literal("VERIFY_ID"), b -> {
            ResourceLocation loc = ResourceLocation.tryParse(machineInput.getValue());
            if (loc != null && ForgeRegistries.BLOCKS.containsKey(loc)) {
                status = "§2ID_VALID: §f" + loc.getPath();
            } else {
                status = "§4ID_NOT_FOUND_IN_REGISTRY";
            }
        }).bounds(x, this.height / 2 + 15, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§2[ LINK ]"), b -> {
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
        this.renderBackground(graphics);
        graphics.fill(this.width / 2 - 120, this.height / 2 - 60, this.width / 2 + 120, this.height / 2 + 50,
                ArchivePalette.BG_SCRIM);
        graphics.renderOutline(this.width / 2 - 120, this.height / 2 - 60, 240, 110, ArchivePalette.TERM_BRIGHT);

        graphics.drawCenteredString(this.font, "§2GREGTECH_INTEGRATION_UNIT", this.width / 2, this.height / 2 - 50,
                ArchivePalette.TERM_BRIGHT);
        graphics.drawCenteredString(this.font, "§7Registers a 'machine' condition key", this.width / 2,
                this.height / 2 - 40, ArchivePalette.TEXT_DIM);
        graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 25,
                ArchivePalette.TERM_BRIGHT);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
