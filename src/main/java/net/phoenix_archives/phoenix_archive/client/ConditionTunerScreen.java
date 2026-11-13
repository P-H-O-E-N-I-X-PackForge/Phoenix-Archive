package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenix_archives.phoenix_archive.api.ConditionExprParser;
import net.phoenix_archives.phoenix_archive.api.ConditionNode;
import net.phoenix_archives.phoenix_archive.api.ConditionSyntaxException;
import net.phoenix_archives.phoenix_archive.common.ArchiveLang;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class ConditionTunerScreen extends Screen {

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    private final ArchiveEditorScreen parent;
    private final ConditionNode initial;

    private MultiLineEditBox exprBox;
    private String error = "";

    public ConditionTunerScreen(ArchiveEditorScreen parent, ConditionNode initial) {
        super(ArchiveLang.tr("phoenix_archive.condition_tuner_screen.condition_tuner"));
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
                ArchiveLang.tr("phoenix_archive.condition_tuner_screen.condition_logic"));
        exprBox.setValue(ConditionExprParser.render(initial));
        this.addRenderableWidget(exprBox);

        this.addRenderableWidget(ArchiveButton
                .create(ArchiveLang.tr("phoenix_archive.condition_tuner_screen.apply_changes"), b -> tryApply())
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
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);

        graphics.fill(this.width / 2 - 170, 10, this.width / 2 + 170, this.height - 10, ArchivePalette.BG_SCRIM);
        graphics.renderOutline(this.width / 2 - 170, 10, 340, this.height - 20, ArchivePalette.TERM);

        graphics.drawCenteredString(this.font,
                ArchiveText.str("phoenix_archive.condition_tuner.phoenix_os_condition_tuner"), this.width / 2, 20,
                ArchivePalette.TERM);
        graphics.drawString(this.font, ArchiveText.str("phoenix_archive.condition_tuner.type_value_combined_with_and"),
                this.width / 2 - 160, 32, ArchivePalette.TEXT_DIM);
        graphics.drawString(this.font,
                ArchiveText.str("phoenix_archive.condition_tuner.e_g_item_minecraft_diamond"),
                this.width / 2 - 160, 42, ArchivePalette.TEXT_FAINT);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (!error.isEmpty()) {
            graphics.drawString(this.font, "§c" + error, this.width / 2 - 160, this.height - 42,
                    ArchivePalette.ALERT);
        }
    }
}
