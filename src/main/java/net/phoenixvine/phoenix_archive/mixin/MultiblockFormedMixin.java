package net.phoenixvine.phoenix_archive.mixin;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phoenix_archive.api.BlockPlacementTracker;
import net.phoenixvine.phoenix_archive.api.TriggerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Optional;

@Mixin(value = MultiblockWorldSavedData.class, remap = false)
public class MultiblockFormedMixin {

    @Inject(method = "addMapping", at = @At("HEAD"), remap = false)
    private void onMultiblockFormed(MultiblockState state, CallbackInfo ci) {
        if (!(state.getWorld() instanceof ServerLevel serverLevel)) return;

        ResourceLocation machineId = ForgeRegistries.BLOCKS.getKey(
                serverLevel.getBlockState(state.controllerPos).getBlock());

        if (machineId == null) return;

        var positions = new ArrayList<>(state.getCache());
        if (!positions.contains(state.controllerPos)) {
            positions.add(state.controllerPos);
        }

        // Try to find the exact placer, fallback to nearest player if tracker is empty
        BlockPlacementTracker.findPlacer(serverLevel.dimension(), positions)
                .flatMap(uuid -> Optional.ofNullable(serverLevel.getServer().getPlayerList().getPlayer(uuid)))
                .ifPresentOrElse(
                        player -> TriggerRegistry.fire(player, "machine", machineId),
                        () -> {
                            // getNearestPlayer returns Player, so we cast to ServerPlayer
                            net.minecraft.world.entity.player.Player nearestBase = serverLevel.getNearestPlayer(
                                    state.controllerPos.getX(),
                                    state.controllerPos.getY(),
                                    state.controllerPos.getZ(),
                                    10.0, false);

                            if (nearestBase instanceof ServerPlayer nearestServer) {
                                TriggerRegistry.fire(nearestServer, "machine", machineId);
                            }
                        }
                );
    }
}