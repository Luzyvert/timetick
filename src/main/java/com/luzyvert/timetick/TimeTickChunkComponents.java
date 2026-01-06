package com.luzyvert.timetick;

import dev.onyxstudios.cca.api.v3.chunk.ChunkComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.chunk.ChunkComponentInitializer;

import java.util.ArrayList;

public class TimeTickChunkComponents implements ChunkComponentInitializer {
    @Override
    public void registerChunkComponentFactories(ChunkComponentFactoryRegistry registry) {
        registry.register(TimeTickComponents.CHUNK_DATA, chunk -> new CachedChunkData(0, new ArrayList<>()));
    }
}