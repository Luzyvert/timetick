package com.luzyvert.timetick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TimeTick implements ModInitializer {
	public static final String MOD_ID = "timetick";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Queue<Runnable> TASK_QUEUE = new ConcurrentLinkedQueue<>();

	@Override
	public void onInitialize() {
		LOGGER.info("TimeTick initializing");

		ServerChunkEvents.CHUNK_UNLOAD.register(this::SaveChunkTime);

		ServerChunkEvents.CHUNK_LOAD.register(this::TickChunk);

		ChunkLevelEvents.CHUNK_LEVEL_TYPE_CHANGE.register( (world, chunk, oldLevelType, newLevelType) -> {
			if(chunk == null)
				return;

			//LOGGER.info("CHUNK_LEVEL_TYPE_CHANGE: {} (Ticking: {}), old: {} -> new: {}", chunk.getPos(), IsBlockTicking(newLevelType), oldLevelType, newLevelType);

			if(IsBlockTicking(newLevelType)) {
				TickChunk(world, chunk);
				return;
			}

			if (chunk instanceof WorldChunk worldChunk) {
				SaveChunkTime(world, worldChunk);
			}
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

	private void SaveChunkTime(ServerWorld world, WorldChunk chunk) {
		if (IsBlockTicking(chunk.getLevelType())) return;

		CachedChunkData data = TimeTickComponents.CHUNK_DATA.get(chunk);

		//LOGGER.info("Saving chunk time for {}", chunk.getPos());
		List<BlockPos> growingBlocks = new ArrayList<>();
		ChunkSection[] sections = chunk.getSectionArray();

		for (int i = 0; i < sections.length; i++) {
			ChunkSection section = sections[i];
			if (section == null || section.isEmpty() || !section.hasRandomTicks()) continue;

			int startY = chunk.getBottomY() + (i * 16);

			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = 0; y < 16; y++) {
						BlockState state = section.getBlockState(x, y, z);

						if (shouldTrackBlock(state.getBlock())) {
							BlockPos absolutePos = new BlockPos(
									chunk.getPos().getStartX() + x,
									startY + y,
									chunk.getPos().getStartZ() + z
							);
							growingBlocks.add(absolutePos);
						}
					}
				}
			}
		}

		if (!growingBlocks.isEmpty()) {
			data.tickTime = world.getTime();
			data.positions.clear();
			data.positions.addAll(growingBlocks);

			TimeTickComponents.CHUNK_DATA.sync(chunk);
		}
	}

	private void TickChunk(ServerWorld world, WorldChunk chunk) {
		CachedChunkData data = TimeTickComponents.CHUNK_DATA.get(chunk);

		long currentTick = world.getTime();
		long lastSavedTime = data.tickTime;

		if (lastSavedTime <= 0) {
			data.tickTime = currentTick;
			return;
		}

		long ticksPassed = currentTick - lastSavedTime;

		data.tickTime = currentTick;

		if (ticksPassed > 0 && !data.positions.isEmpty()) {

			int randomTickSpeed = world.getGameRules().getValue(GameRules.RANDOM_TICK_SPEED);
			float expectedTicks = ticksPassed * (randomTickSpeed / 4096.0f);
			int baseCalls = (int) expectedTicks;
			float chanceForExtra = expectedTicks - baseCalls;

			List<BlockPos> blocksToTick = new ArrayList<>(data.positions);

			for (BlockPos pos : blocksToTick) {
				TASK_QUEUE.add(() -> {
					if (!world.isChunkLoaded(pos)) return;

					BlockState currentState = world.getBlockState(pos);

					if (shouldTrackBlock(currentState.getBlock())) {
						int calls = baseCalls;
						if (world.random.nextFloat() < chanceForExtra) {
							calls++;
						}

						for (int i = 0; i < calls; i++) {
							BlockState stateInLoop = world.getBlockState(pos);
							if (shouldTrackBlock(stateInLoop.getBlock())) {
								stateInLoop.randomTick(world, pos, world.random);
							} else {
								break;
							}
						}
					}
				});
			}
		}
	}

	private boolean IsBlockTicking(ChunkLevelType type) {
		return type == ChunkLevelType.BLOCK_TICKING || type == ChunkLevelType.ENTITY_TICKING;
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