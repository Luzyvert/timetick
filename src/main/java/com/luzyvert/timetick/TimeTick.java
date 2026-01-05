package com.luzyvert.timetick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.SaplingBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TimeTick implements ModInitializer {
	public static final String MOD_ID = "timetick";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Stores: Chunk Key -> Data (Unload Tick + List of BlockPos)
	public static final HashMap<String, CachedChunkData> CHUNK_CACHE = new HashMap<>();

	private static final Queue<Runnable> TASK_QUEUE = new ConcurrentLinkedQueue<>();

	@Override
	public void onInitialize() {
		LOGGER.info("TimeTick initialized: Using In-Game Time strategy.");

		// 1. EVENT: CHUNK UNLOAD
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
					// STORE CURRENT WORLD TICK
					CHUNK_CACHE.put(chunk.getPos().toString(), new CachedChunkData(world.getTime(), growingBlocks));
				}
			});

			// 2. EVENT: CHUNK LOAD
			ServerChunkEvents.CHUNK_LOAD.register((ServerWorld world, WorldChunk chunk) -> {
				String chunkKey = chunk.getPos().toString();

				if (CHUNK_CACHE.containsKey(chunkKey)) {
					CachedChunkData data = CHUNK_CACHE.get(chunkKey);

					// CALCULATE TICKS PASSED
					long currentTick = world.getTime();
					long ticksPassed = currentTick - data.unloadTick;

					CHUNK_CACHE.remove(chunkKey);

					// 100 ticks = 5 seconds
					if (ticksPassed > 100) {
						LOGGER.info("Chunk {} loaded after {} ticks. Processing blocks.", chunkKey, ticksPassed);

						for (BlockPos pos : data.positions) {
							TASK_QUEUE.add(() -> {
								BlockState currentState = world.getBlockState(pos);
								if (currentState.getBlock() instanceof Fertilizable fertilizable) {
									if (fertilizable.isFertilizable(world, pos, currentState, false)) {
										// Optional: You can now use 'ticksPassed' to calculate how many times to grow
										// e.g. int growthCycles = (int) (ticksPassed / 24000); // Once per Minecraft day?
										if (fertilizable.canGrow(world, world.random, pos, currentState)) {
											fertilizable.grow(world, world.random, pos, currentState);
										}
									}
								}
							});
						}
					}
				}
			});

			// 3. EVENT: END SERVER TICK
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				if (TASK_QUEUE.isEmpty()) return;

				int operations = 0;
				int MAX_OPERATIONS_PER_TICK = 10;

				while (!TASK_QUEUE.isEmpty() && operations < MAX_OPERATIONS_PER_TICK) {
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
			});
		}

		public static class CachedChunkData {
			public long unloadTick; // Changed from Instant to long
			public List<BlockPos> positions;

			public CachedChunkData(long unloadTick, List<BlockPos> positions) {
				this.unloadTick = unloadTick;
				this.positions = positions;
			}
		}
	}