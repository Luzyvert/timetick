package com.luzyvert.timetick;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.ladysnake.cca.api.v3.component.ComponentV3;

import java.util.List;

public interface CachedChunkDataComponent extends ComponentV3 {
	void readData(ValueInput input);

	void writeData(ValueOutput output);
}