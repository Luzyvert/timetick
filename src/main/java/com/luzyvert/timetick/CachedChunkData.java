package com.luzyvert.timetick;

import net.minecraft.util.math.BlockPos;

import java.time.Instant;
import java.util.List;

public class CachedChunkData {
	public final long tickTime;
	public final List<BlockPos> positions;

	public CachedChunkData(long tickTime, List<BlockPos> positions) {
		this.tickTime = tickTime;
		this.positions = positions;
	}
}
