package com.luzyvert.timetick;

import java.time.Instant;

public class ChunkTime
{
    long hash;
    Instant timestamp;

    public ChunkTime(long hash){
        this.hash = hash;
        timestamp = Instant.now();
    }
}
