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

	// 平滑处理相关变量
	private static final int MSPT_HISTORY_SIZE = 60; // 保留60个tick的历史（约3秒）
	private final LinkedList<Float> serverMsptHistory = new LinkedList<>();
	private float smoothServerMspt = 0.0f;
	private int tickCounter = 0;

	// 数据包发送间隔（每秒发送1次）
	private static final int PACKET_SEND_INTERVAL = 20; // 20 ticks = 1秒

	private final Map<BlockPos, BlockUpdateInfo> blockUpdateTimes = new ConcurrentHashMap<>();
	private final Map<BlockPos, Integer> blockTypes = new ConcurrentHashMap<>();
	private final Map<BlockPos, Long> blockUpdateStartTimes = new ConcurrentHashMap<>();

	// 漏斗相关变量
	private final Map<BlockPos, Long> hopperUpdateTimes = new ConcurrentHashMap<>();

	// 新增的方块记录系统
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

	// 在MSPTDisplayMod.java中添加一个Map来跟踪每个位置在当前游戏刻的记录
	private Map<BlockPos, BlockRecording> blockRecordings = new HashMap<>();
	private int currentTick = 0;

	@Override
	public void onInitialize() {
		INSTANCE = this;

		// 注册服务器tick开始事件
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			lastTickStartTime = System.nanoTime();
			getInstance().onTickStart(server.getTicks());
		});

		// 注册服务器tick结束事件
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTickEnd);

		LOGGER.info("MSPT Display Mod 已初始化");
	}

	public static MSPTDisplayMod getInstance() {
		return INSTANCE;
	}

	// 在每个游戏刻开始时清除旧数据
	public void onTickStart(int tick) {
		if (tick != currentTick) {
			blockRecordings.clear();
			currentTick = tick;
		}
	}

	// 记录方块更新开始时间
	public void recordBlockStart(World world, BlockPos pos) {
		if (world.isClient) return;

		BlockPos posKey = pos.toImmutable();
		BlockRecording recording = blockRecordings.getOrDefault(posKey, new BlockRecording(posKey));
		recording.startTime = System.nanoTime();
		recording.active = true;
		blockRecordings.put(posKey, recording);

		// 保持对旧系统的兼容
		blockUpdateStartTimes.put(posKey, recording.startTime);
	}

	// 记录方块更新结束时间并计算持续时间
	public void recordBlockEnd(World world, BlockPos pos) {
		if (world.isClient) return;

		BlockPos posKey = pos.toImmutable();
		BlockRecording recording = blockRecordings.get(posKey);
		if (recording != null && recording.active) {
			long endTime = System.nanoTime();
			long duration = endTime - recording.startTime;
			// 如果已经有记录，只保留最长的时间
			if (recording.duration < duration) {
				recording.duration = duration;
			}
			recording.active = false;

			// 更新现有系统的记录
			recordBlockUpdateTime(world, posKey, duration);
		}

		// 保持对旧系统的兼容
		blockUpdateStartTimes.remove(posKey);
	}

	// 记录漏斗更新时间
	public void recordHopperTime(World world, BlockPos pos, long updateTimeNs) {
		if (world.isClient) return;

		BlockPos posKey = pos.toImmutable();

		// 记录漏斗的方块类型
		blockTypes.put(posKey, 8); // 8 表示漏斗

		// 更新漏斗的MSPT信息
		BlockUpdateInfo info = blockUpdateTimes.computeIfAbsent(posKey, p -> new BlockUpdateInfo());
		info.totalTime += updateTimeNs;
		info.updates++;
		info.lastUpdateTime = System.currentTimeMillis();
	}

	private void onServerTickEnd(MinecraftServer server) {
		tickCounter++;

		if (lastTickStartTime > 0) {
			long tickTime = System.nanoTime() - lastTickStartTime;
			float currentServerMspt = (float)(tickTime / 1_000_000.0); // 转换为毫秒

			// 添加到历史记录
			serverMsptHistory.add(currentServerMspt);
			while (serverMsptHistory.size() > MSPT_HISTORY_SIZE) {
				serverMsptHistory.removeFirst();
			}

			// 计算移动平均值
			float sum = 0;
			for (float mspt : serverMsptHistory) {
				sum += mspt;
			}
			float avgMspt = sum / serverMsptHistory.size();

			// 使用0.95的平滑因子，这会使变化非常缓慢
			if (smoothServerMspt == 0) {
				smoothServerMspt = avgMspt;
			} else {
				smoothServerMspt = smoothServerMspt * 0.95f + avgMspt * 0.05f;
			}

			// 更新总MSPT
			totalServerMspt = smoothServerMspt;

			// 每PACKET_SEND_INTERVAL个tick发送数据（约1秒一次）
			if (tickCounter % PACKET_SEND_INTERVAL == 0) {
				sendMsptData(server);
			}

			// 清理过期数据，防止内存泄漏
			cleanupOldData();
		}
	}

	// 发送MSPT数据
	private void sendMsptData(MinecraftServer server) {
		// 创建缓冲区
		PacketByteBuf buf = PacketByteBufs.create();

		// 1. 发送服务器总MSPT
		buf.writeFloat(smoothServerMspt);

		// 获取要发送的方块数据列表
		List<Map.Entry<BlockPos, BlockUpdateInfo>> sortedEntries = new ArrayList<>(blockUpdateTimes.entrySet());

		// 使用安全的排序方式
		sortedEntries.sort((a, b) -> {
			float msptA = a.getValue().getSmoothMsptNs();
			float msptB = b.getValue().getSmoothMsptNs();

			if (msptA != msptB) {
				// 降序排列，大的MSPT值在前
				return Float.compare(msptB, msptA);
			} else {
				// MSPT值相同时，使用BlockPos坐标进行稳定排序
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

		// 增加显示数量，从10个提高到30个
		int blockCount = Math.min(sortedEntries.size(), 100);
		buf.writeInt(blockCount);

		// 写入方块数据
		for (int i = 0; i < blockCount; i++) {
			Map.Entry<BlockPos, BlockUpdateInfo> entry = sortedEntries.get(i);
			BlockPos pos = entry.getKey();
			float msptNs = entry.getValue().getSmoothMsptNs();
			int blockType = blockTypes.getOrDefault(pos, 0);

			// 写入方块坐标
			buf.writeInt(pos.getX());
			buf.writeInt(pos.getY());
			buf.writeInt(pos.getZ());

			// 写入MSPT和方块类型
			buf.writeFloat(msptNs);
			buf.writeInt(blockType);
		}

		// 发送到所有玩家
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			try {
				ServerPlayNetworking.send(player, MSPT_PACKET_ID, buf);
			} catch (Exception e) {
				LOGGER.error("发送MSPT数据包时出错", e);
			}
		}
	}

	// 清理旧数据
	private void cleanupOldData() {
		long currentTime = System.currentTimeMillis();
		long cutoffTime = currentTime - 30000; // 30秒没有更新的数据将被删除（原来是10秒）

		// 只删除很久没更新的数据
		blockUpdateTimes.entrySet().removeIf(entry ->
				entry.getValue().lastUpdateTime < cutoffTime);

		// 同步清理blockTypes中不再存在的方块
		blockTypes.entrySet().removeIf(entry ->
				!blockUpdateTimes.containsKey(entry.getKey()));
	}

	// 记录方块更新时间
	public void recordBlockUpdateTime(World world, BlockPos pos, long updateTimeNs) {
		if (world.isClient) {
			return;
		}

		// 确保值不会太小
		updateTimeNs = Math.max(updateTimeNs, 100); // 至少100纳秒

		// 获取方块状态和方块对象
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		// 记录方块类型
		int blockType = getBlockType(block);

		// 活塞特殊处理逻辑
		boolean isPistonRelated = block == Blocks.PISTON || block == Blocks.STICKY_PISTON ||
				block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON;

		// 处理活塞及相关方块
		if (isPistonRelated) {
			// 尝试找到活塞基座
			BlockPos basePos = findPistonBase(world, pos);

			// 如果找到了基座并且不是当前位置
			if (basePos != null && !basePos.equals(pos)) {
				// 使用基座位置而不是当前位置
				pos = basePos;
				blockType = 5; // 活塞类型

				// 获取基座方块
				Block baseBlock = world.getBlockState(basePos).getBlock();

				// 区分普通活塞和粘性活塞
				if (baseBlock == Blocks.STICKY_PISTON) {
					blockType = 5; // 可以用不同的ID区分粘性活塞，比如8
				}
			}
		}

		// 储存方块类型信息
		blockTypes.put(pos, blockType);

		// 更新方块MSPT信息
		BlockUpdateInfo info = blockUpdateTimes.computeIfAbsent(pos, p -> new BlockUpdateInfo());
		info.totalTime += updateTimeNs;
		info.updates++;
		info.lastUpdateTime = System.currentTimeMillis();
	}

	// 增强的活塞基座查找方法
	private BlockPos findPistonBase(World world, BlockPos pos) {
		// 先检查自身位置
		Block block = world.getBlockState(pos).getBlock();

		// 如果是活塞基座，直接返回
		if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) {
			return pos;
		}

		// 如果是活塞头部或移动中的活塞
		if (block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON) {
			// 检查6个方向来寻找活塞基座
			for (Direction dir : Direction.values()) {
				BlockPos checkPos = pos.offset(dir);
				Block checkBlock = world.getBlockState(checkPos).getBlock();

				if (checkBlock == Blocks.PISTON || checkBlock == Blocks.STICKY_PISTON) {
					return checkPos;
				}
			}

			// 没有找到活塞基座时，使用额外的检查方法
			BlockPos result = findPistonBaseExtended(world, pos);
			if (result != null) {
				return result;
			}
		}

		return pos; // 找不到则返回原位置
	}

	// 扩展的活塞基座查找方法，检查更大范围
	private BlockPos findPistonBaseExtended(World world, BlockPos pos) {
		// 检查更大范围内的方块(2格范围)
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				for (int z = -2; z <= 2; z++) {
					// 跳过已检查的中心1x1x1区域
					if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1) continue;

					BlockPos checkPos = pos.add(x, y, z);
					Block checkBlock = world.getBlockState(checkPos).getBlock();

					if (checkBlock == Blocks.PISTON || checkBlock == Blocks.STICKY_PISTON) {
						// 检查此活塞是否可能与当前方块相关
						// 这里可以添加更复杂的逻辑来确认关联性
						return checkPos;
					}
				}
			}
		}

		return null;
	}

	// 使用自定义类存储方块更新信息
	private static class BlockUpdateInfo {
		long totalTime = 0;          // 总更新时间(纳秒)
		int updates = 0;             // 更新次数
		long lastUpdateTime = 0;     // 上次更新时间

		// 平滑处理变量
		float smoothedMspt = 0.0f;   // 平滑后的MSPT值(纳秒)
		final LinkedList<Float> msptHistory = new LinkedList<>(); // 历史MSPT值
		final int historySize = 20; // 保留历史数据点数量

		// 计算平滑MSPT(纳秒)
		float getSmoothMsptNs() {
			if (updates == 0) return 1000; // 返回1000纳秒（0.001毫秒）作为最小值

			// 计算当前平均值
			float currentAvg = (float)(totalTime / updates);

			// 添加到历史记录
			msptHistory.add(currentAvg);
			while (msptHistory.size() > historySize) {
				msptHistory.removeFirst();
			}

			// 计算移动平均值
			float sum = 0;
			for (float mspt : msptHistory) {
				sum += mspt;
			}
			float avgMspt = msptHistory.isEmpty() ? currentAvg : sum / msptHistory.size();

			// 使用0.95的平滑因子
			if (smoothedMspt == 0) {
				smoothedMspt = avgMspt;
			} else {
				smoothedMspt = smoothedMspt * 0.95f + avgMspt * 0.05f;
			}

			// 确保值不会太小
			return Math.max(smoothedMspt, 1000); // 最小1000纳秒
		}

		// 获取原始平均值（纳秒）
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
		// 从所有相关Map中移除此方块的数据
		blockUpdateTimes.remove(pos);
		blockTypes.remove(pos);
		blockRecordings.remove(pos);
		blockUpdateStartTimes.remove(pos);
		hopperUpdateTimes.remove(pos);
	}
}