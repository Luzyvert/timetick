package com.luzyvert.timetick;

import org.ladysnake.cca.api.v3.chunk.ChunkComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.chunk.ChunkComponentInitializer;

import java.util.ArrayList;

public class TimeTickChunkComponents implements ChunkComponentInitializer {
    @Override
    public void registerChunkComponentFactories(ChunkComponentFactoryRegistry registry) {
        registry.register(TimeTickComponents.CHUNK_DATA, chunk -> new CachedChunkData(0, new ArrayList<>()));
    }
}