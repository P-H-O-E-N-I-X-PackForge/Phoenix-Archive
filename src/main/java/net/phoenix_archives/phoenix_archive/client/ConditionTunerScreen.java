package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenix_archives.phoenix_archive.api.ConditionExprParser;
import net.phoenix_archives.phoenix_archive.api.ConditionNode;
import net.phoenix_archives.phoenix_archive.api.ConditionSyntaxException;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class ConditionTunerScreen extends Screen {

    private final ArchiveEditorScreen parent;
    private final ConditionNode initial;

    private MultiLineEditBox exprBox;
    private String error = "";

    public ConditionTunerScreen(ArchiveEditorScreen parent, ConditionNode initial) {
        super(Component.literal("Condition Tuner"));
        this.parent = parent;
        this.initial = initial;
    }

    @Override
    protected void init() {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.clearWidgets();

        int boxX = this.width / 2 - 160;
        int boxY = 55;
        int boxW = 320;
        int boxH = Math.max(40, this.height - 130);

        exprBox = new MultiLineEditBox(this.font, boxX, boxY, boxW, boxH, Component.empty(),
                Component.literal("Condition Logic"));
        exprBox.setValue(ConditionExprParser.render(initial));
        this.addRenderableWidget(exprBox);

        this.addRenderableWidget(Button.builder(Component.literal("§2[ APPLY_CHANGES ]"), b -> tryApply())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
                .build());
    }

    @Override
    protected void repositionElements() {
        this.init();
    }

    private void tryApply() {
        try {
            ConditionNode parsed = ConditionExprParser.parse(exprBox.getValue());
            parent.setConditionTree(parsed);
            this.minecraft.setScreen(parent);
        } catch (ConditionSyntaxException ex) {
            this.error = ex.getMessage();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ArchivePalette.refresh(PhoenixTheme.current());
        this.renderBackground(graphics);

        graphics.fill(this.width / 2 - 170, 10, this.width / 2 + 170, this.height - 10, ArchivePalette.BG_SCRIM);
        graphics.renderOutline(this.width / 2 - 170, 10, 340, this.height - 20, ArchivePalette.TERM);

        graphics.drawCenteredString(this.font, "PHOENIX_OS // CONDITION_TUNER", this.width / 2, 20,
                ArchivePalette.TERM);
        graphics.drawString(this.font, "§7type:value combined with AND / OR / NOT and (parens)",
                this.width / 2 - 160, 32, ArchivePalette.TEXT_DIM);
        graphics.drawString(this.font,
                "§8e.g. item:minecraft:diamond AND NOT dimension:minecraft:the_nether",
                this.width / 2 - 160, 42, ArchivePalette.TEXT_FAINT);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (!error.isEmpty()) {
            graphics.drawString(this.font, "§c" + error, this.width / 2 - 160, this.height - 42,
                    ArchivePalette.ALERT);
        }
    }
}
