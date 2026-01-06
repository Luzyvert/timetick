package com.luzyvert.timetick;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public class CachedChunkData implements CachedChunkDataComponent {
	public long tickTime;
	public final List<BlockPos> positions;

	public CachedChunkData(long tickTime, List<BlockPos> positions) {
		this.tickTime = tickTime;
		this.positions = positions;
	}

	@Override
	public long getTickTime() {
		return tickTime;
	}

	@Override
	public void readFromNbt(NbtCompound nbtCompound) {
		tickTime = nbtCompound.getLong("value");

		long[] posArray = nbtCompound.getLongArray("positions");
		positions.clear();
		if (posArray != null) {
			for (long packed : posArray) {
				positions.add(BlockPos.fromLong(packed));
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound nbtCompound) {
		nbtCompound.putLong("value", tickTime);

		long[] posArray = positions.stream().mapToLong(BlockPos::asLong).toArray();
		nbtCompound.putLongArray("positions", posArray);
	}
}
