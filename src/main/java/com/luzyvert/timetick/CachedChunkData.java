package com.luzyvert.timetick;

import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import java.util.Optional;

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
	public void readData(ReadView readView) {
		tickTime = readView.getLong("value", tickTime);

		Optional<long[]> posArray = readView.getOptionalLongArray("positions");
		positions.clear();
		if (posArray.isPresent()) {
			for (long packed : posArray.get()) {
				positions.add(BlockPos.fromLong(packed));
			}
		}
	}

	@Override
	public void writeData(WriteView writeView) {
		writeView.putLong("value", tickTime);

		long[] posArray = positions.stream().mapToLong(BlockPos::asLong).toArray();
		writeView.putLongArray("positions", posArray);
	}
}
