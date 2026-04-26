package net.phoenixvine.phoenix_archive.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

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
        int x = this.width / 2 - 100;

        machineInput = new EditBox(this.font, x, this.height / 2 - 10, 200, 20, Component.empty());
        machineInput.setHint(Component.literal("gtceu:macerator..."));

        // Pull existing machine value from the map if it exists
        String existing = parent.getConditions().getOrDefault("machine", "");
        machineInput.setValue(existing);

        this.addRenderableWidget(machineInput);

        // Verify Button (Checks Forge Registry)
        this.addRenderableWidget(Button.builder(Component.literal("VERIFY_ID"), b -> {
            ResourceLocation loc = ResourceLocation.tryParse(machineInput.getValue());
            if (loc != null && ForgeRegistries.BLOCKS.containsKey(loc)) {
                status = "§2ID_VALID: §f" + loc.getPath();
            } else {
                status = "§4ID_NOT_FOUND_IN_REGISTRY";
            }
        }).bounds(x, this.height / 2 + 15, 95, 20).build());

        // Confirm Button (Injects into Dynamic Map)
        this.addRenderableWidget(Button.builder(Component.literal("§2[ LINK ]"), b -> {
            String id = machineInput.getValue().trim();
            if (!id.isEmpty()) {
                // We inject it into the map using the "machine" key
                parent.getConditions().put("machine", id);
                this.minecraft.setScreen(parent);
            }
        }).bounds(x + 105, this.height / 2 + 15, 95, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        graphics.fill(this.width / 2 - 120, this.height / 2 - 60, this.width / 2 + 120, this.height / 2 + 50,
                0xEE050505);
        graphics.renderOutline(this.width / 2 - 120, this.height / 2 - 60, 240, 110, 0xFF00FF00);

        graphics.drawCenteredString(this.font, "§2GREGTECH_INTEGRATION_UNIT", this.width / 2, this.height / 2 - 50,
                0xFFFFFF);
        graphics.drawCenteredString(this.font, "§7Registers a 'machine' condition key", this.width / 2,
                this.height / 2 - 40, 0xAAAAAA);
        graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 25, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
