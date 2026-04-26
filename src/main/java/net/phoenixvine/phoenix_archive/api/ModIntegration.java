package net.phoenixvine.phoenix_archive.api;

import net.minecraftforge.fml.ModList;

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
