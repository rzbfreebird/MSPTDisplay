package com.ren.msptdisplay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MSPTDisplayMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("msptdisplay");
	public static final Identifier MSPT_PACKET_ID = new Identifier("msptdisplay", "mspt_data");

	private static MSPTDisplayMod INSTANCE;
	private long lastTickStartTime = 0;
	private float totalServerMspt = 0.0f;

	private static final int MSPT_HISTORY_SIZE = 60;
	private final LinkedList<Float> serverMsptHistory = new LinkedList<>();
	private float smoothServerMspt = 0.0f;
	private int tickCounter = 0;

	private static final int PACKET_SEND_INTERVAL = 20;

	private final Map<BlockPos, BlockUpdateInfo> blockUpdateTimes = new ConcurrentHashMap<>();
	private final Map<BlockPos, Integer> blockTypes = new ConcurrentHashMap<>();
	private final Map<BlockPos, Long> blockUpdateStartTimes = new ConcurrentHashMap<>();

	private final Map<BlockPos, Long> hopperUpdateTimes = new ConcurrentHashMap<>();

	private static class BlockRecording {
		public BlockPos pos;
		public long startTime;
		public long duration;
		public boolean active;

		public BlockRecording(BlockPos pos) {
			this.pos = pos;
			this.startTime = 0;
			this.duration = 0;
			this.active = false;
		}
	}

	private Map<BlockPos, BlockRecording> blockRecordings = new HashMap<>();
	private int currentTick = 0;

	@Override
	public void onInitialize() {
		INSTANCE = this;

		ServerTickEvents.START_SERVER_TICK.register(server -> {
			lastTickStartTime = System.nanoTime();
			getInstance().onTickStart(server.getTicks());
		});

		ServerTickEvents.END_SERVER_TICK.register(this::onServerTickEnd);

		LOGGER.info("MSPT Display Mod 已初始化");
	}

	public static MSPTDisplayMod getInstance() {
		return INSTANCE;
	}

	public void onTickStart(int tick) {
		if (tick != currentTick) {
			blockRecordings.clear();
			currentTick = tick;
		}
	}

	public void recordBlockStart(World world, BlockPos pos) {
		if (world.isClient) return;

		BlockPos posKey = pos.toImmutable();
		BlockRecording recording = blockRecordings.getOrDefault(posKey, new BlockRecording(posKey));
		recording.startTime = System.nanoTime();
		recording.active = true;
		blockRecordings.put(posKey, recording);

		blockUpdateStartTimes.put(posKey, recording.startTime);
	}

	public void recordBlockEnd(World world, BlockPos pos) {
		if (world.isClient) return;

		BlockPos posKey = pos.toImmutable();
		BlockRecording recording = blockRecordings.get(posKey);
		if (recording != null && recording.active) {
			long endTime = System.nanoTime();
			long duration = endTime - recording.startTime;
			if (recording.duration < duration) {
				recording.duration = duration;
			}
			recording.active = false;

			recordBlockUpdateTime(world, posKey, duration);
		}

		blockUpdateStartTimes.remove(posKey);
	}

	public void recordHopperTime(World world, BlockPos pos, long updateTimeNs) {
		if (world.isClient) return;

		BlockPos posKey = pos.toImmutable();

		blockTypes.put(posKey, 8);

		BlockUpdateInfo info = blockUpdateTimes.computeIfAbsent(posKey, p -> new BlockUpdateInfo());
		info.totalTime += updateTimeNs;
		info.updates++;
		info.lastUpdateTime = System.currentTimeMillis();
	}

	private void onServerTickEnd(MinecraftServer server) {
		tickCounter++;

		if (lastTickStartTime > 0) {
			long tickTime = System.nanoTime() - lastTickStartTime;
			float currentServerMspt = (float)(tickTime / 1_000_000.0);

			serverMsptHistory.add(currentServerMspt);
			while (serverMsptHistory.size() > MSPT_HISTORY_SIZE) {
				serverMsptHistory.removeFirst();
			}

			float sum = 0;
			for (float mspt : serverMsptHistory) {
				sum += mspt;
			}
			float avgMspt = sum / serverMsptHistory.size();

			if (smoothServerMspt == 0) {
				smoothServerMspt = avgMspt;
			} else {
				smoothServerMspt = smoothServerMspt * 0.95f + avgMspt * 0.05f;
			}

			totalServerMspt = smoothServerMspt;

			if (tickCounter % PACKET_SEND_INTERVAL == 0) {
				sendMsptData(server);
			}

			cleanupOldData();
		}
	}

	private void sendMsptData(MinecraftServer server) {
		PacketByteBuf buf = PacketByteBufs.create();

		buf.writeFloat(smoothServerMspt);

		List<Map.Entry<BlockPos, BlockUpdateInfo>> sortedEntries = new ArrayList<>(blockUpdateTimes.entrySet());

		sortedEntries.sort((a, b) -> {
			float msptA = a.getValue().getSmoothMsptNs();
			float msptB = b.getValue().getSmoothMsptNs();

			if (msptA != msptB) {
				return Float.compare(msptB, msptA);
			} else {
				BlockPos posA = a.getKey();
				BlockPos posB = b.getKey();

				if (posA.getX() != posB.getX()) {
					return Integer.compare(posA.getX(), posB.getX());
				} else if (posA.getY() != posB.getY()) {
					return Integer.compare(posA.getY(), posB.getY());
				} else {
					return Integer.compare(posA.getZ(), posB.getZ());
				}
			}
		});

		int blockCount = Math.min(sortedEntries.size(), 100);
		buf.writeInt(blockCount);

		for (int i = 0; i < blockCount; i++) {
			Map.Entry<BlockPos, BlockUpdateInfo> entry = sortedEntries.get(i);
			BlockPos pos = entry.getKey();
			float msptNs = entry.getValue().getSmoothMsptNs();
			int blockType = blockTypes.getOrDefault(pos, 0);

			buf.writeInt(pos.getX());
			buf.writeInt(pos.getY());
			buf.writeInt(pos.getZ());

			buf.writeFloat(msptNs);
			buf.writeInt(blockType);
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			try {
				ServerPlayNetworking.send(player, MSPT_PACKET_ID, buf);
			} catch (Exception e) {
				LOGGER.error("发送MSPT数据包时出错", e);
			}
		}
	}

	private void cleanupOldData() {
		long currentTime = System.currentTimeMillis();
		long cutoffTime = currentTime - 30000;

		blockUpdateTimes.entrySet().removeIf(entry ->
				entry.getValue().lastUpdateTime < cutoffTime);

		blockTypes.entrySet().removeIf(entry ->
				!blockUpdateTimes.containsKey(entry.getKey()));
	}

	public void recordBlockUpdateTime(World world, BlockPos pos, long updateTimeNs) {
		if (world.isClient) {
			return;
		}

		updateTimeNs = Math.max(updateTimeNs, 100);

		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		int blockType = getBlockType(block);

		boolean isPistonRelated = block == Blocks.PISTON || block == Blocks.STICKY_PISTON ||
				block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON;

		if (isPistonRelated) {
			BlockPos basePos = findPistonBase(world, pos);

			if (basePos != null && !basePos.equals(pos)) {
				pos = basePos;
				blockType = 5;

				Block baseBlock = world.getBlockState(basePos).getBlock();

				if (baseBlock == Blocks.STICKY_PISTON) {
					blockType = 5;
				}
			}
		}

		blockTypes.put(pos, blockType);

		BlockUpdateInfo info = blockUpdateTimes.computeIfAbsent(pos, p -> new BlockUpdateInfo());
		info.totalTime += updateTimeNs;
		info.updates++;
		info.lastUpdateTime = System.currentTimeMillis();
	}

	private BlockPos findPistonBase(World world, BlockPos pos) {
		Block block = world.getBlockState(pos).getBlock();

		if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) {
			return pos;
		}

		if (block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON) {
			for (Direction dir : Direction.values()) {
				BlockPos checkPos = pos.offset(dir);
				Block checkBlock = world.getBlockState(checkPos).getBlock();

				if (checkBlock == Blocks.PISTON || checkBlock == Blocks.STICKY_PISTON) {
					return checkPos;
				}
			}

			BlockPos result = findPistonBaseExtended(world, pos);
			if (result != null) {
				return result;
			}
		}

		return pos;
	}

	private BlockPos findPistonBaseExtended(World world, BlockPos pos) {
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1) continue;

					BlockPos checkPos = pos.add(x, y, z);
					Block checkBlock = world.getBlockState(checkPos).getBlock();

					if (checkBlock == Blocks.PISTON || checkBlock == Blocks.STICKY_PISTON) {
						return checkPos;
					}
				}
			}
		}

		return null;
	}

	private static class BlockUpdateInfo {
		long totalTime = 0;
		int updates = 0;
		long lastUpdateTime = 0;

		float smoothedMspt = 0.0f;
		final LinkedList<Float> msptHistory = new LinkedList<>();
		final int historySize = 20;

		float getSmoothMsptNs() {
			if (updates == 0) return 1000;

			float currentAvg = (float)(totalTime / updates);

			msptHistory.add(currentAvg);
			while (msptHistory.size() > historySize) {
				msptHistory.removeFirst();
			}

			float sum = 0;
			for (float mspt : msptHistory) {
				sum += mspt;
			}
			float avgMspt = msptHistory.isEmpty() ? currentAvg : sum / msptHistory.size();

			if (smoothedMspt == 0) {
				smoothedMspt = avgMspt;
			} else {
				smoothedMspt = smoothedMspt * 0.95f + avgMspt * 0.05f;
			}

			return Math.max(smoothedMspt, 1000); // 最小1000纳秒
		}

		float getAverageNs() {
			if (updates == 0) return 0;
			return (float)totalTime / updates;
		}
	}

	// 获取方块类型ID
	private int getBlockType(Block block) {
		if (block instanceof RedstoneWireBlock) return 1; // 红石粉
		if (block == Blocks.COMPARATOR) return 2;         // 比较器
		if (block == Blocks.REPEATER) return 3;           // 中继器
		if (block == Blocks.OBSERVER) return 4;           // 侦测器
		if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) return 5; // 活塞
		if (block == Blocks.REDSTONE_LAMP) return 6;      // 红石灯
		if (block == Blocks.REDSTONE_TORCH) return 7;     // 红石火把
		if (block == Blocks.HOPPER) return 8;             // 漏斗
		return 0; // 其他方块
	}

	public void removeBlockData(BlockPos pos) {
		blockUpdateTimes.remove(pos);
		blockTypes.remove(pos);
		blockRecordings.remove(pos);
		blockUpdateStartTimes.remove(pos);
		hopperUpdateTimes.remove(pos);
	}
}