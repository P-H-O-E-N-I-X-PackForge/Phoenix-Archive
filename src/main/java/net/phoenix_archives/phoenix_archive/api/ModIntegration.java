package net.phoenix_archives.phoenix_archive.api;

import net.neoforged.fml.ModList;

public class ModIntegration {

    public static boolean isFTBQuestsLoaded() {
        return ModList.get().isLoaded("ftbquests");
    }

    public static boolean isGregTechLoaded() {
        return ModList.get().isLoaded("gtceu");
    }

    public static boolean isGameStagesLoaded() {
        return ModList.get().isLoaded("gamestages");
    }
}
