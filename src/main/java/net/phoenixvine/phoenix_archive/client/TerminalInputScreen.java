package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class TerminalInputScreen extends Screen {

    private final Screen parent;
    private final String titleLabel;
    private final String initialValue;
    private final Consumer<String> onCommit;
    private MultiLineEditBox textArea;
    private String liveValue; // Survives re-init when returning from sub-screens

    public TerminalInputScreen(Screen parent, String titleLabel, String initialValue, Consumer<String> onCommit) {
        super(Component.literal("Terminal Input Unit"));
        this.parent = parent;
        this.titleLabel = titleLabel;
        this.initialValue = initialValue;
        this.onCommit = onCommit;
    }

    @Override
    protected void init() {
        // Use liveValue if we have one (e.g. returning from ChromaticSelectorScreen),
        // otherwise fall back to the original initialValue.
        String startingValue = (liveValue != null) ? liveValue : initialValue;

        this.textArea = new MultiLineEditBox(this.font, 20, 45, this.width - 40, this.height - 90,
                Component.literal("§8RAW DATA BUFFER..."), Component.empty());
        this.textArea.setCharacterLimit(10000);
        this.textArea.setValue(startingValue);
        this.addRenderableWidget(textArea);

        // --- 1. Standard Vanilla Colors & Formatting ---
        int startX = 20;
        int startY = 12;
        int btnW = 14;

        // Standard MC Color Codes: 0-9, a-f
        String colorCodes = "0123456789abcdef";
        for (char c : colorCodes.toCharArray()) {
            this.addRenderableWidget(Button.builder(Component.literal("§" + c + "█"), b -> insertCode("&" + c))
                    .bounds(startX, startY, btnW, 14).build());
            startX += btnW + 2;
        }

        startX += 4; // Gap

        // Standard Formatting: Bold, Strikethrough, Underline, Italic, Obfuscated, Reset
        String formatCodes = "lmnork";
        for (char c : formatCodes.toCharArray()) {
            String label = (c == 'r') ? "X" : (c == 'k') ? "?" : "§" + c + "█";
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> insertCode("&" + c))
                    .bounds(startX, startY, btnW, 14).build());
            startX += btnW + 2;
        }

        // --- 2. Chromatic OS Trigger ---
        if (net.minecraftforge.fml.ModList.get().isLoaded("phoenix_chromatic_codes")) {
            this.addRenderableWidget(Button.builder(Component.literal("§z[ CHROMATIC_OS ]"), b -> {
                if (this.minecraft != null) {
                    // Save current text before leaving so it survives re-init on return
                    liveValue = this.textArea.getValue();
                    this.minecraft.setScreen(new ChromaticSelectorScreen(this));
                }
            }).bounds(this.width - 110, 12, 100, 14).build());
        }

        // --- 3. Commit Changes ---
        this.addRenderableWidget(Button.builder(Component.literal("§2[ COMMIT_CHANGES ]"), b -> {
            this.onCommit.accept(this.textArea.getValue());
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 - 60, this.height - 35, 120, 20).build());
    }

    /**
     * Called by ChromaticSelectorScreen to append a code and persist it across re-init.
     */
    public void receiveCode(String code) {
        liveValue = this.textArea.getValue() + code;
    }

    public void insertCode(String code) {
        this.textArea.setValue(this.textArea.getValue() + code);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        graphics.fill(10, 10, this.width - 10, this.height - 10, 0xEE050505);
        graphics.renderOutline(10, 10, this.width - 20, this.height - 20, 0xFF00FF00);
        graphics.drawString(this.font, titleLabel + " // ADDR: 0x7FFA", 20, 32, 0x00AA00);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
