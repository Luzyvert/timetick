package com.luzyvert.timetick;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

public final class ChunkLevelEvents {
    private ChunkLevelEvents() {
    }

    public static final Event<LevelChange> CHUNK_LEVEL_TYPE_CHANGE = EventFactory.createArrayBacked(LevelChange.class, callbacks -> (world, chunk, oldLevel, newLevel) -> {
        for (LevelChange callback : callbacks) {
            callback.onLevelChange(world, chunk, oldLevel, newLevel);
        }
    });

    @FunctionalInterface
    public interface LevelChange {
        void onLevelChange(ServerWorld world, WorldChunk chunk, ChunkLevelType oldLevel, ChunkLevelType newLevel);
    }
}