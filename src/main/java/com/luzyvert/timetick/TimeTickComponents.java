package com.luzyvert.timetick;

import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

public class TimeTickComponents {
    public static final ComponentKey<CachedChunkData> CHUNK_DATA =
            ComponentRegistry.getOrCreate(Identifier.of("timetick", "cached_chunk_data"), CachedChunkData.class);
}