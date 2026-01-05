package com.luzyvert.timetick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TimeTick implements ModInitializer {
	public static final String MOD_ID = "timetick";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final HashMap<String, CachedChunkData> CHUNK_CACHE = new HashMap<>();
	private static final Queue<Runnable> TASK_QUEUE = new ConcurrentLinkedQueue<>();

	@Override
	public void onInitialize() {
		LOGGER.info("TimeTick initializing");

		ServerChunkEvents.CHUNK_UNLOAD.register((ServerWorld world, WorldChunk chunk) -> {
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

							if (state.getBlock() instanceof SaplingBlock || state.getBlock() instanceof CropBlock) {
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

				if (!growingBlocks.isEmpty()) {
					CHUNK_CACHE.put(chunk.getPos().toString(), new CachedChunkData(world.getTime(), growingBlocks));
				}
			}
		});

		ServerChunkEvents.CHUNK_LOAD.register((ServerWorld world, WorldChunk chunk) -> {
			String chunkKey = chunk.getPos().toString();

			if (CHUNK_CACHE.containsKey(chunkKey)) {
				CachedChunkData data = CHUNK_CACHE.get(chunkKey);
				long currentTick = world.getTime();
				long ticksPassed = currentTick - data.tickTime;

				CHUNK_CACHE.remove(chunkKey);

				if (ticksPassed > 0) {
					LOGGER.info("Chunk {} loaded after {} ticks. Processing {} blocks.", chunkKey, ticksPassed, data.positions.size());

					int randomTickSpeed = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);

					float expectedTicks = ticksPassed * (randomTickSpeed / 4096.0f);
					int baseCalls = (int) expectedTicks;
					float chanceForExtra = expectedTicks - baseCalls;

					for (BlockPos pos : data.positions) {
						TASK_QUEUE.add(() -> {
							BlockState currentState = world.getBlockState(pos);

							if (currentState.getBlock() instanceof SaplingBlock || currentState.getBlock() instanceof CropBlock) {
								int calls = baseCalls;
								if (world.random.nextFloat() < chanceForExtra) {
									calls++;
								}

								for (int i = 0; i < calls; i++) {
									// Re-check state inside the loop in case the block broke or changed during previous random ticks
									BlockState stateInLoop = world.getBlockState(pos);
									if (stateInLoop.getBlock() instanceof SaplingBlock || stateInLoop.getBlock() instanceof CropBlock) {
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
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (TASK_QUEUE.isEmpty()) return;

			Instant time = Instant.now();
			try (ExitAction exitAction = new ExitAction(() -> {
				long seconds = Duration.between(time, Instant.now()).toMillis();
				LOGGER.info("SERVER_TICK: {} ms", seconds);
			})) {
				int operations = 0;
				int MAX_OPERATIONS_PER_TICK = 50;

				while (!TASK_QUEUE.isEmpty()) {
					Runnable task = TASK_QUEUE.poll();
					if (task != null) {
						try {
							task.run();
						} catch (Exception e) {
							LOGGER.error("Error processing growth task", e);
						}
						operations++;
					}
				}
			}
		});
	}


}