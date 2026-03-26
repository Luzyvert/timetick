package com.luzyvert.timetick;

import net.minecraft.resources.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;

public class TimeTickComponents {
    public static final ComponentKey<CachedChunkData> CHUNK_DATA =
            ComponentRegistryV3.INSTANCE.getOrCreate(Identifier.fromNamespaceAndPath("timetick", "cached_chunk_data"), CachedChunkData.class);
}