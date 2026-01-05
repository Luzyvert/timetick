package com.luzyvert.timetick.mixin;

import com.luzyvert.timetick.ChunkLevelEvents;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageMixin {

    @Shadow @Final private ServerWorld world;

    private ChunkLevelType getLevelTypeFromInt(int level) {
        if (level <= 31) {
            return ChunkLevelType.ENTITY_TICKING;
        } else if (level <= 32) {
            return ChunkLevelType.BLOCK_TICKING;
        } else if (level <= 33) {
            return ChunkLevelType.FULL;
        } else {
            return ChunkLevelType.INACCESSIBLE;
        }
    }

    @Inject(method = "setLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ChunkHolder;setLevel(I)V"))
    private void onSetLevel(long pos, int level, ChunkHolder holder, int i, CallbackInfoReturnable<ChunkHolder> cir) {
        WorldChunk chunk = holder.getWorldChunk();

        ChunkLevelType oldType = holder.getLevelType();
        ChunkLevelType newType = getLevelTypeFromInt(level);

        ChunkLevelEvents.CHUNK_LEVEL_TYPE_CHANGE.invoker().onLevelChange(
                this.world,
                chunk,
                oldType,
                newType
        );
    }
}