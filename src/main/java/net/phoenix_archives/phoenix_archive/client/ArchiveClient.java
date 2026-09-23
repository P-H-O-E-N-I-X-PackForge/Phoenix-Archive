package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.phoenix_archives.phoenix_archive.PhoenixArchive;
import net.phoenix_archives.phoenix_archive.api.LoreDataLoader;
import net.phoenix_archives.phoenix_archive.api.LoreEntry;
import net.phoenix_archives.phoenix_archive.client.render.shader.ArchiveShaderManager;
import net.phoenix_archives.phoenix_archive.client.rich.ArchiveConditionalBlockParser;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.wiki.client.rich.markdown.BlockParserRegistry;
import net.phoenixvine.wiki.client.rich.markdown.inline.handlers.LinkTargetHandler;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import static net.phoenix_archives.phoenix_archive.PhoenixArchive.MOD_ID;

@OnlyIn(Dist.CLIENT)
public class ArchiveClient {

    public static final String TORN_PAGE_SHADER_ID = "torn_page";

    private static final String TORN_PAGE_SHADER_SOURCE = """
            // Default "corrupted data" effect for Archive's torn/missing-page placeholder.
            // Edit or replace this file freely -- it's only written once, on first launch.
            vec2 hash2(vec2 p) {
                p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
                return fract(sin(p) * 43758.5453);
            }

            void mainImage(out vec4 fragColor, in vec2 fragCoord) {
                vec2 uv = fragCoord / iResolution.xy;

                vec3 col = vec3(0.02, 0.02, 0.03);

                float bandY = floor(uv.y * 24.0);
                float bandTime = floor(iTime * 6.0 + bandY * 3.7);
                float glitch = step(0.94, fract(sin(bandTime) * 43758.5453));
                float shift = (fract(sin(bandTime * 1.37) * 12345.6) - 0.5) * 0.06 * glitch;
                uv.x += shift;

                float n = hash2(floor(uv * iResolution.xy * 0.5) + iTime * 60.0).x;
                col += vec3(0.05) * n;

                col *= 0.9 + 0.1 * sin(uv.y * iResolution.y * 3.14159);

                float vig = smoothstep(0.9, 0.2, length(uv - 0.5));
                col *= mix(0.4, 1.0, vig);

                float alertFlicker = step(0.985, fract(sin(floor(iTime * 2.0)) * 91.7));
                col = mix(col, vec3(0.6, 0.08, 0.08), alertFlicker * 0.15);

                fragColor = vec4(col, 1.0);
            }
            """;

    public static void onClientSetup(final FMLClientSetupEvent event) {
        Minecraft mc = Minecraft.getInstance();
        PhoenixArchive.LOGGER.info("PHOENIX_OS // Client setup, registering HUD bar entry...");

        // Lets the suite's shared/per-mod theme toggle (see PhoenixTheme#setSharedMode) tell this
        // mod's own code apart from every other Phoenix mod's when they call the no-arg theme
        // accessors -- see PhoenixTheme#resolveCallerModId.
        PhoenixTheme.registerMod("net.phoenix_archives.phoenix_archive", MOD_ID);

        ArchivePalette.refresh(PhoenixTheme.current());
        PhoenixTheme.addChangeListener(() -> ArchivePalette.refresh(PhoenixTheme.current()));

        ArchiveShaderManager.ensureDefault(TORN_PAGE_SHADER_ID, TORN_PAGE_SHADER_SOURCE);

        // Archive's own :::if/:::else conditional-content block, and the quest: link scheme for
        // deep-linking into Chronicles -- see ArchiveConditionalBlockParser and openLink() below.
        BlockParserRegistry.DEFAULT.registerFirst(new ArchiveConditionalBlockParser());
        LinkTargetHandler.registerScheme("quest:");

        registerHudBar(mc);
    }

    public static boolean openLoreForChroniclesQuest(Screen returnTo, String chroniclesQuestId) {
        for (LoreEntry entry : LoreDataLoader.LORE_ENTRIES.values()) {
            if (entry.conditionTree().findLeafValue("chronicles_quest").filter(chroniclesQuestId::equals).isPresent()) {
                Minecraft.getInstance().setScreen(new ArchiveScreen(returnTo, entry));
                return true;
            }
        }
        return false;
    }

    public static boolean hasLoreForChroniclesQuest(String chroniclesQuestId) {
        for (LoreEntry entry : LoreDataLoader.LORE_ENTRIES.values()) {
            if (entry.conditionTree().findLeafValue("chronicles_quest").filter(chroniclesQuestId::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static void openLoreEntryById(Screen returnTo, String entryId) {
        LoreEntry entry = findLoreEntry(entryId);
        if (entry != null) Minecraft.getInstance().setScreen(new ArchiveScreen(returnTo, entry));
    }

    public static boolean hasLoreEntry(String entryId) {
        return findLoreEntry(entryId) != null;
    }

    private static LoreEntry findLoreEntry(String entryId) {
        if (entryId == null || entryId.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(entryId);
        return rl != null ? LoreDataLoader.LORE_ENTRIES.get(rl) : null;
    }

    public static void openChroniclesQuest(Screen returnTo, String chroniclesQuestId) {
        if (!ModList.get().isLoaded("phoenix_chronicles")) return;
        ResourceLocation id = ResourceLocation.tryParse(chroniclesQuestId);
        if (id == null) return;
        QuestNode node = QuestTreeRegistry.getQuest(id);
        if (node == null) return;

        ChronicleOverviewScreen screen = new ChronicleOverviewScreen(returnTo);
        Minecraft.getInstance().setScreen(screen);
        screen.navigateToNode(node);
    }

    private static void registerHudBar(Minecraft mc) {
        // A plain static 16x16 crop of lore_terminal.png's first frame -- the HUD bar blits this
        // raw texture directly (not through the item/block atlas), and the source item texture's
        // animation .mcmeta made that raw blit sample garbage in a packaged environment (the atlas
        // handles animated item textures fine for real item rendering, but a manual UV-sliced blit
        // of a multi-frame strip isn't guaranteed a clean single frame outside dev).
        ResourceLocation iconPath = ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "textures/gui/lore_terminal_icon.png");

        SuiteHudBar.register(
                MOD_ID,
                SuiteHudBar.PRIORITY_ARCHIVE,
                iconPath,
                () -> Component.literal("§fOpen Archive"),
                () -> 1,
                () -> mc.setScreen(new ArchiveScreen(mc.screen)),
                16,
                16,
                false);
    }
}
