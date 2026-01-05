package com.luzyvert.timetick;

import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

@FunctionalInterface
public interface LevelTypeChange {
    void onChunkLevelTypeChange(ServerWorld var1, WorldChunk var2, ChunkLevelType var3, ChunkLevelType var4);
}
