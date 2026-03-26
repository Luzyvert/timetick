package com.luzyvert.timetick;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
	public void readData(ValueInput input) {
		this.tickTime = input.getLongOr("value", 0L);

		Optional<long[]> posArray = input.getOptionalLongArray("positions");
		if (posArray.isPresent()) {
			this.positions.clear();
			for (long packed : posArray.get()) {
				this.positions.add(BlockPos.of(packed));
			}
		}
	}

	@Override
	public void writeData(ValueOutput output) {
		output.putLong("value", this.tickTime);

		long[] posArray = this.positions.stream().mapToLong(BlockPos::asLong).toArray();
		output.putLongArray("positions", posArray);
	}
}