package com.luzyvert.timetick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TimeTick implements ModInitializer {
	public static final String MOD_ID = "timetick";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Queue<Runnable> TASK_QUEUE = new ConcurrentLinkedQueue<>();

	@Override
	public void onInitialize() {
		LOGGER.info("TimeTick initializing");

		ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level, chunk, oldStatus, newStatus) -> {
			if (IsBlockTicking(newStatus)) {
				if(!IsBlockTicking(oldStatus))
					TickChunk(level, chunk);
				return;
			}

			SaveChunkTime(level, chunk);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (TASK_QUEUE.isEmpty()) return;

			long startTime = System.nanoTime();
			long MAX_TIME_NS = 35_000_000;

			while (!TASK_QUEUE.isEmpty()) {
				Runnable task = TASK_QUEUE.poll();
				if (task != null) {
					try {
						task.run();
					} catch (Exception e) {
						LOGGER.error("Error processing growth task", e);
					}
				}

				if (System.nanoTime() - startTime > MAX_TIME_NS) {
					break;
				}
			}
		});
	}

	private void SaveChunkTime(ServerLevel level, LevelChunk chunk) {
		if (IsBlockTicking(chunk.getFullStatus())) return;

		CachedChunkData data = TimeTickComponents.CHUNK_DATA.get(chunk);

		if(data.tickTime > 0)
			return;

		List<BlockPos> growingBlocks = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.isRandomlyTicking()) continue;

			int startY = chunk.getMinY() + (i * 16);

			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = 0; y < 16; y++) {
						BlockState state = section.getBlockState(x, y, z);
						Block block = state.getBlock();

						if (shouldTrackBlock(block)) {
							BlockPos absolutePos = new BlockPos(
									chunk.getPos().getMinBlockX() + x,
									startY + y,
									chunk.getPos().getMinBlockZ() + z
							);
							if(block instanceof FarmlandBlock)
								growingBlocks.addFirst(absolutePos);
							else
								growingBlocks.add(absolutePos);
						}
					}
				}
			}
		}

		if (!growingBlocks.isEmpty()) {
			data.tickTime = level.getGameTime();
			data.positions.clear();
			data.positions.addAll(growingBlocks);

			TimeTickComponents.CHUNK_DATA.sync(chunk);
		}
	}

	private void TickChunk(ServerLevel level, LevelChunk chunk) {
		CachedChunkData data = TimeTickComponents.CHUNK_DATA.get(chunk);

		long currentTick = level.getGameTime();
		long lastSavedTime = data.tickTime;

		if (lastSavedTime <= 0) {
			return;
		}

		long ticksPassed = currentTick - lastSavedTime;

		if (ticksPassed > 0 && !data.positions.isEmpty()) {

			int randomTickSpeed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
			float expectedTicks = ticksPassed * (randomTickSpeed / 4096.0f);
			int baseCalls = (int) expectedTicks;
			float chanceForExtra = expectedTicks - baseCalls;

			List<BlockPos> blocksToTick = new ArrayList<>(data.positions);

			for (BlockPos pos : blocksToTick) {
				TASK_QUEUE.add(() -> {
					BlockState currentState = level.getBlockState(pos);

					if (shouldTrackBlock(currentState.getBlock())) {
						int calls = baseCalls;
						if (level.getRandom().nextFloat() < chanceForExtra) {
							calls++;
						}

						for (int i = 0; i < calls; i++) {
							BlockState stateInLoop = level.getBlockState(pos);
							if (shouldTrackBlock(stateInLoop.getBlock())) {
								stateInLoop.randomTick(level, pos, level.getRandom());
							} else {
								break;
							}
						}
					}
				});
			}
		}

		data.positions.clear();
		data.tickTime = -1;
	}

	private boolean IsBlockTicking(FullChunkStatus type) {
		return switch (type) {
			case ENTITY_TICKING, BLOCK_TICKING -> true;
			default -> false;
		};
	}

	private boolean shouldTrackBlock(Block block) {
		return block instanceof CropBlock
				|| block instanceof SaplingBlock
				|| block instanceof StemBlock
				|| block instanceof CocoaBlock
				|| block instanceof SugarCaneBlock
				|| block instanceof CactusBlock
				|| block instanceof SweetBerryBushBlock
				|| block instanceof LeavesBlock
				|| block instanceof FarmlandBlock;
	}
}