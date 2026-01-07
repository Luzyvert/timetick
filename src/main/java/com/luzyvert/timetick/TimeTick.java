package com.luzyvert.timetick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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

	private static final Map<Long, Boolean> SPAWN_CHUNK_MAP = new HashMap<>();
	private final int spawnChunkRadius = 11;
	private int tickCounter = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("TimeTick initializing");

		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if(isSpawnChunk(world, chunk.getPos()) && !isSpawnChunkTicking(world, chunk.getPos()))
				return;
			TickChunk(world, chunk);
		});

		ChunkLevelEvents.CHUNK_LEVEL_TYPE_CHANGE.register( (world, chunk, oldLevelType, newLevelType) -> {
			if(chunk == null)
				return;

			//LOGGER.info("CHUNK_LEVEL_TYPE_CHANGE: {} (Ticking: {}), old: {} -> new: {}", chunk.getPos(), IsBlockTicking(newLevelType), oldLevelType, newLevelType);

			if(IsBlockTicking(newLevelType)) {
				if(!IsBlockTicking(oldLevelType))
					TickChunk(world, chunk);
				return;
			}

			SaveChunkTime(world, chunk);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (tickCounter++ % 20 == 0) {
				SpawnChunkLogic(server);
			}

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

	public boolean isSpawnChunk(ServerWorld world, ChunkPos chunkPos) {
		ChunkPos spawnChunk = new ChunkPos(world.getSpawnPos());

		int dx = Math.abs(chunkPos.x - spawnChunk.x);
		int dz = Math.abs(chunkPos.z - spawnChunk.z);

		return dx <= spawnChunkRadius && dz <= spawnChunkRadius;
	}

	public boolean isSpawnChunkTicking(ServerWorld world, ChunkPos chunkPos)
	{
		List<ServerPlayerEntity> players = world.getPlayers();

		for (ServerPlayerEntity player : players) {
			if (player.squaredDistanceTo(chunkPos.x, player.getY(), chunkPos.z) < 16384) {
				return true;
			}
		}
		return false;
	}

	private void SpawnChunkLogic(MinecraftServer server){
		ServerWorld world = server.getOverworld();
		ChunkPos spawnCenter = new ChunkPos(world.getSpawnPos());

		List<ServerPlayerEntity> players = world.getPlayers();

		for (int x = -spawnChunkRadius; x <= spawnChunkRadius; x++) {
			for (int z = -spawnChunkRadius; z <= spawnChunkRadius; z++) {

				long chunkPosLong = ChunkPos.toLong(spawnCenter.x + x, spawnCenter.z + z);

				if (world.shouldTickBlocksInChunk(chunkPosLong)) {

					boolean isRandomTicking = false;
					boolean prevRandomTicking = SPAWN_CHUNK_MAP.getOrDefault(chunkPosLong, false);

					double chunkX = (spawnCenter.x + x) * 16 + 8;
					double chunkZ = (spawnCenter.z + z) * 16 + 8;

					for (ServerPlayerEntity player : players) {
						if (player.squaredDistanceTo(chunkX, player.getY(), chunkZ) < 16384) {
							isRandomTicking = true;
							break;
						}
					}

					if (prevRandomTicking != isRandomTicking) {
						ChunkPos pos = new ChunkPos(chunkPosLong);

						if(isRandomTicking)
							TickChunk(world, world.getChunk(pos.x, pos.z));
						else
							SaveChunkTime(world, world.getChunk(pos.x, pos.z));

						SPAWN_CHUNK_MAP.put(chunkPosLong, isRandomTicking);
					}
				}
			}
		}
	}

	private void SaveChunkTime(ServerWorld world, WorldChunk chunk) {
		if (!isSpawnChunk(world, chunk.getPos()) && IsBlockTicking(chunk.getLevelType()))
			return;

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
						Block block = state.getBlock();

						if (shouldTrackBlock(block)) {
							BlockPos absolutePos = new BlockPos(
									chunk.getPos().getStartX() + x,
									startY + y,
									chunk.getPos().getStartZ() + z
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
			return;
		}

		long ticksPassed = currentTick - lastSavedTime;

		if (ticksPassed > 0 && !data.positions.isEmpty()) {
			//LOGGER.info("Chunk {} loaded after {} ticks. Processing {} blocks.", chunk.getPos().toString(), ticksPassed, data.positions.size());
			int randomTickSpeed = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
			float expectedTicks = ticksPassed * (randomTickSpeed / 4096.0f);
			int baseCalls = (int) expectedTicks;
			float chanceForExtra = expectedTicks - baseCalls;

			List<BlockPos> blocksToTick = new ArrayList<>(data.positions);

			for (BlockPos pos : blocksToTick) {
				TASK_QUEUE.add(() -> {
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

		data.positions.clear();
		data.tickTime = -1;
	}

	private boolean IsBlockTicking(ChunkLevelType type) {
        return switch (type) {
            case ENTITY_TICKING, BLOCK_TICKING -> true;
            default -> false;
        };
	}

	private boolean shouldTrackBlock(Block block) {
		return     block instanceof CropBlock
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