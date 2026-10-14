package net.phoenix_archives.phoenix_archive.mixin;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.phoenix_archives.phoenix_archive.api.BlockPlacementTracker;
import net.phoenix_archives.phoenix_archive.api.TriggerRegistry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Optional;

/** Fires the {@code machine} trigger when a multiblock actually forms, not when its controller is placed. */
@Mixin(value = MultiblockControllerMachine.class, remap = false)
public abstract class MultiblockFormedMixin {

    @Shadow
    protected boolean isFormed;

    @Shadow
    public abstract PatternState getPatternState(String name);

    @Unique
    private boolean phoenixArchive$wasFormed;

    @Inject(method = "formStructure(Ljava/lang/String;)V", at = @At("HEAD"), remap = false)
    private void phoenixArchive$beforeForm(String name, CallbackInfo ci) {
        phoenixArchive$wasFormed = isFormed;
    }

    @Inject(method = "formStructure(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void phoenixArchive$afterForm(String name, CallbackInfo ci) {
        if (phoenixArchive$wasFormed || !isFormed) return;
        MultiblockControllerMachine self = (MultiblockControllerMachine) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos controllerPos = self.getBlockPos();
        ResourceLocation machineId = BuiltInRegistries.BLOCK
                .getKey(serverLevel.getBlockState(controllerPos).getBlock());
        if (machineId == null) return;

        var positions = new ArrayList<BlockPos>();
        PatternState state = getPatternState(name);
        if (state != null) state.getCache().keySet().forEach(packed -> positions.add(BlockPos.of(packed)));
        if (!positions.contains(controllerPos)) positions.add(controllerPos);

        BlockPlacementTracker.findPlacer(serverLevel.dimension(), positions)
                .flatMap(uuid -> Optional.ofNullable(serverLevel.getServer().getPlayerList().getPlayer(uuid)))
                .or(() -> Optional.ofNullable(self.getOwnerUUID())
                        .flatMap(uuid -> Optional.ofNullable(serverLevel.getServer().getPlayerList().getPlayer(uuid))))
                .ifPresentOrElse(
                        player -> TriggerRegistry.fire(player, "machine", machineId),
                        () -> {
                            net.minecraft.world.entity.player.Player nearest = serverLevel.getNearestPlayer(
                                    controllerPos.getX(), controllerPos.getY(), controllerPos.getZ(), 10.0, false);
                            if (nearest instanceof ServerPlayer nearestServer) {
                                TriggerRegistry.fire(nearestServer, "machine", machineId);
                            }
                        });
    }
}
