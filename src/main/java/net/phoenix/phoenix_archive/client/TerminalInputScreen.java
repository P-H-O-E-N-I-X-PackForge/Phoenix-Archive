package net.phoenix.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private EditBox hexInput;

    /**
     * @param parent The screen to return to
     * @param titleLabel The aesthetic label (e.g., "BIOME_SCANNER")
     * @param initialValue The text already present
     * @param onCommit What to do with the text when [ COMMIT ] is pressed
     */
    public TerminalInputScreen(Screen parent, String titleLabel, String initialValue, Consumer<String> onCommit) {
        super(Component.literal("Terminal Input Unit"));
        this.parent = parent;
        this.titleLabel = titleLabel;
        this.initialValue = initialValue;
        this.onCommit = onCommit;
    }

    @Override
    protected void init() {
        // Raw Data Buffer area
        this.textArea = new MultiLineEditBox(this.font, 20, 45, this.width - 40, this.height - 90,
                Component.literal("§8RAW DATA BUFFER..."), Component.empty());

        this.textArea.setValue(initialValue);
        this.addRenderableWidget(textArea);

        // --- Formatting Tools ---
        String[] codes = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
        int btnW = 16;
        for (int i = 0; i < codes.length; i++) {
            String c = codes[i];
            this.addRenderableWidget(Button.builder(Component.literal("§" + c + "█"), b -> insertCode("&" + c))
                    .bounds(20 + (i * (btnW + 2)), 12, btnW, 14).build());
        }

        String[] formats = {"l", "o", "n", "r", "k"};
        String[] labels = {"§lB", "§oI", "§nU", "§rX", "§k?"};
        for (int i = 0; i < formats.length; i++) {
            String f = formats[i];
            this.addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> insertCode("&" + f))
                    .bounds(20 + (codes.length * (btnW + 2)) + (i * 20), 12, 18, 14).build());
        }

        hexInput = new EditBox(this.font, this.width - 95, 12, 50, 14, Component.empty());
        hexInput.setHint(Component.literal("FFFFFF"));
        hexInput.setMaxLength(6);
        this.addRenderableWidget(hexInput);

        this.addRenderableWidget(Button.builder(Component.literal("HEX"), b -> {
            if (hexInput.getValue().length() == 6) insertCode("&#" + hexInput.getValue());
        }).bounds(this.width - 40, 12, 30, 14).build());

        // [ COMMIT ] - Returns the data to whoever called it
        this.addRenderableWidget(Button.builder(Component.literal("§2[ COMMIT_CHANGES ]"), b -> {
            this.onCommit.accept(this.textArea.getValue());
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }).bounds(this.width / 2 - 60, this.height - 35, 120, 20).build());
    }

    private void insertCode(String code) {
        this.textArea.setValue(this.textArea.getValue() + code);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        graphics.fill(10, 10, this.width - 10, this.height - 10, 0xEE050505);
        graphics.renderOutline(10, 10, this.width - 20, this.height - 20, 0xFF00FF00);

        // Uses the dynamic title
        graphics.drawString(this.font, titleLabel + " // ADDR: 0x7FFA", 20, 32, 0x00AA00);
        graphics.drawString(this.font, "CHAR_COUNT: " + textArea.getValue().length(), this.width - 100, 32, 0x005500);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}