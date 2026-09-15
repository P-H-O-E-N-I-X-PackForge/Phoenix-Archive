package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.phoenix.chromatic_codes.config.ModConfig;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;

public class ChromaticSelectorScreen extends Screen {

    private final TerminalInputScreen parent;

    public ChromaticSelectorScreen(TerminalInputScreen parent) {
        super(Component.literal("Chromatic OS Interface"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ArchivePalette.refresh(PhoenixTheme.current());
        int centerX = this.width / 2;
        int startY = 45;

        if (ModList.get().isLoaded("phoenix_chromatic_codes")) {
            
            this.addRenderableWidget(Button.builder(Component.literal("§b>_ STATIC_SIGNATURE_ARRAY"), b -> {})
                    .bounds(centerX - 100, startY, 200, 14).build()).active = false;

            startY += 20;

            int colWidth = 65;
            int currentX = centerX - ((colWidth * 3 + 4) / 2);
            int rowCount = 0;

            for (String entry : ModConfig.INSTANCE.colors.customColors) {
                String code = entry.split(":")[0];
                this.addRenderableWidget(Button.builder(Component.literal("§" + code + "Color &" + code), b -> {
                    insertAndClose("&" + code);
                }).bounds(currentX + (rowCount % 3 * (colWidth + 2)), startY, colWidth, 18).build());

                if (++rowCount % 3 == 0) startY += 20;
            }

            if (rowCount % 3 != 0) startY += 25;
            else startY += 5;

            this.addRenderableWidget(Button.builder(Component.literal("§d>_ ACTIVE_CHROMATIC_FLUX"), b -> {})
                    .bounds(centerX - 100, startY, 200, 14).build()).active = false;

            startY += 20;

            for (String entry : ModConfig.INSTANCE.colors.customGradients) {
                String code = entry.split(":")[0];
                this.addRenderableWidget(
                        Button.builder(Component.literal("§" + code + "EXECUTABLE_EFFECT: &" + code), b -> {
                            insertAndClose("&" + code);
                        }).bounds(centerX - 100, startY, 200, 18).build());
                startY += 20;
            }
        }

        this.addRenderableWidget(Button.builder(Component.literal("§7[ ABORT_AND_RETURN ]"), b -> this.onClose())
                .bounds(centerX - 60, this.height - 35, 120, 20).build());
    }

    private void insertAndClose(String code) {

        parent.receiveCode(code);
        this.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);

        graphics.fill(this.width / 2 - 110, 20, this.width / 2 + 110, this.height - 10, ArchivePalette.BG_SCRIM);
        graphics.renderOutline(this.width / 2 - 110, 20, 220, this.height - 30, ArchivePalette.TERM_BRIGHT);
        graphics.drawCenteredString(this.font, "CHROMATIC_SELECTOR_V1.0.4", this.width / 2, 30, ArchivePalette.TERM);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
