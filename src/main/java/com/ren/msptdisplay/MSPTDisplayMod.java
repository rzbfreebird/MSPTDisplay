package com.ren.msptdisplay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MSPTDisplayMod implements ModInitializer {
	// 数据包ID
	public static final Identifier MSPT_PACKET_ID = new Identifier("msptdisplay", "mspt_data");
	public static final Identifier MSPT_MODE_PACKET_ID = new Identifier("msptdisplay", "mspt_mode");

	// 存储方块的时间消耗
	private static final Map<BlockPos, Long> prevBlockTimes = new ConcurrentHashMap<>();
	private static final Map<BlockPos, Long> currentBlockTimes = new ConcurrentHashMap<>();

	// 测量模式控制
	private static boolean isInMeasureMode = false;

	// 跟踪更新的方块
	private static final Map<BlockPos, BlockState> updatedBlocks = new ConcurrentHashMap<>();

	// 用于避免对同一方块多次记录的线程局部变量
	private static final ThreadLocal<Map<BlockPos, Long>> threadStartTimes = ThreadLocal.withInitial(HashMap::new);

	@Override
	public void onInitialize() {
		System.out.println("[MSPT] 初始化MSPT显示Mod");

		// 注册服务器tick开始事件
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			// 清除上一次的数据
			prevBlockTimes.clear();
			prevBlockTimes.putAll(currentBlockTimes);
			currentBlockTimes.clear();
			updatedBlocks.clear();
		});

		// 注册服务器tick结束事件
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 == 0) { // 每秒发送一次数据
				sendMsptData(server);
			}
		});

		// 注册模式切换数据包接收器
		ServerPlayNetworking.registerGlobalReceiver(MSPT_MODE_PACKET_ID, (server, player, handler, buf, responseSender) -> {
			boolean newMode = buf.readBoolean();
			server.execute(() -> {
				isInMeasureMode = newMode;
				System.out.println("[MSPT] 服务器设置测量模式为: " + (isInMeasureMode ? "启用" : "禁用"));

				// 清除所有数据
				prevBlockTimes.clear();
				currentBlockTimes.clear();
				updatedBlocks.clear();
			});
		});
	}

	// 创建模式切换数据包
	public static PacketByteBuf createModePacket(boolean mode) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(mode);
		return buf;
	}

	// 记录方块更新时间
	public static void recordBlockUpdateStart(BlockPos pos, World world) {
		if (!(world instanceof ServerWorld) || !isInMeasureMode) {
			return;
		}

		// 记录开始时间 - 使用线程局部变量防止嵌套调用问题
		Map<BlockPos, Long> startTimes = threadStartTimes.get();
		if (!startTimes.containsKey(pos)) {
			long startTime = System.nanoTime();
			startTimes.put(pos, startTime);
			updatedBlocks.put(pos, world.getBlockState(pos));
		}
	}

	// 记录方块更新结束
	public static void recordBlockUpdateEnd(BlockPos pos, World world) {
		if (!(world instanceof ServerWorld) || !isInMeasureMode) {
			return;
		}

		Map<BlockPos, Long> startTimes = threadStartTimes.get();
		Long startTime = startTimes.remove(pos);
		if (startTime == null) {
			return; // 没有记录起始时间，忽略
		}

		long endTime = System.nanoTime();
		long duration = endTime - startTime;

		// 只记录大于一定阈值的时间消耗（例如微秒级）
		if (duration > 100) { // 100纳秒 = 0.1微秒
			Long prevTime = currentBlockTimes.getOrDefault(pos, 0L);
			currentBlockTimes.put(pos, prevTime + duration);
		}

		System.out.println("[MSPT] 记录方块更新：位置=" + pos +
				", 耗时=" + duration + "ns (" + (duration/1_000_000.0f) + "ms)");
	}

	// 记录活塞更新开始
	public static void recordPistonUpdate(BlockPos pos, World world, Direction direction) {
		if (!(world instanceof ServerWorld) || !isInMeasureMode) {
			return;
		}

		// 记录活塞头位置
		updatedBlocks.put(pos, world.getBlockState(pos));

		// 也记录活塞臂的位置
		BlockPos armPos = pos.offset(direction);
		updatedBlocks.put(armPos, world.getBlockState(armPos));

		// 启动计时 - 使用线程局部变量
		Map<BlockPos, Long> startTimes = threadStartTimes.get();
		if (!startTimes.containsKey(pos)) {
			long startTime = System.nanoTime();
			startTimes.put(pos, startTime);
		}

		System.out.println("[MSPT] 开始记录活塞更新: " + pos);
	}

	// 记录活塞更新结束
	public static void recordPistonUpdateEnd(BlockPos pos, World world) {
		if (!(world instanceof ServerWorld) || !isInMeasureMode) {
			return;
		}

		Map<BlockPos, Long> startTimes = threadStartTimes.get();
		Long startTime = startTimes.remove(pos);
		if (startTime == null) {
			return; // 没有记录起始时间，忽略
		}

		long endTime = System.nanoTime();
		long duration = endTime - startTime;

		// 活塞消耗时间通常较大，直接记录
		Long prevTime = currentBlockTimes.getOrDefault(pos, 0L);
		currentBlockTimes.put(pos, prevTime + duration);

		System.out.println("[MSPT] 结束记录活塞更新: " + pos + ", 耗时: " + (duration / 1_000_000.0f) + "ms");
	}




	// 发送MSPT数据到客户端
	private static void sendMsptData(MinecraftServer server) {
		// 如果没有处于测量模式或没有数据，不发送
		if (!isInMeasureMode || currentBlockTimes.isEmpty()) {
			return;
		}

		// 创建数据包
		PacketByteBuf buf = PacketByteBufs.create();

		// 获取要发送的方块数量
		int count = Math.min(currentBlockTimes.size(), 1000); // 限制最大数量以防数据包过大
		buf.writeInt(count);

		// 写入每个方块的位置和MSPT值
		int written = 0;
		for (Map.Entry<BlockPos, Long> entry : currentBlockTimes.entrySet()) {
			if (written >= count) {
				break;
			}

			BlockPos pos = entry.getKey();
			long nanoTime = entry.getValue();

			// 只发送有显著时间的方块数据
			if (nanoTime <= 10) { // 跳过太小的值
				continue;
			}

			// 转换为毫秒
			float mspt = nanoTime / 1_000_000.0f;

			// 防止出现太小的值
			if (mspt < 0.0001f) {
				mspt = 0.0001f;
			}

			buf.writeInt(pos.getX());
			buf.writeInt(pos.getY());
			buf.writeInt(pos.getZ());
			buf.writeFloat(mspt);

			written++;

			// 调试输出
			if (written <= 3) { // 仅记录前3个
				System.out.println("[MSPT] 发送方块 " + pos + " MSPT值: " + mspt + " ms (" + nanoTime + " ns)");
			}
		}

		// 更新数据包中的计数
		if (written != count) {
			// 创建新缓冲区并重新写入正确的计数
			PacketByteBuf newBuf = PacketByteBufs.create();
			newBuf.writeInt(written);

			// 复制原缓冲区数据（跳过原来的计数）
			buf.readerIndex(4); // 跳过原计数
			newBuf.writeBytes(buf);
			buf = newBuf;
		}

		// 只在有数据时发送
		if (written > 0) {
			// 向所有在线玩家发送数据包
			final PacketByteBuf finalBuf = buf;
			for (ServerPlayerEntity player : PlayerLookup.all(server)) {
				ServerPlayNetworking.send(player, MSPT_PACKET_ID, finalBuf);
			}
		}

		System.out.println("[MSPT] 数据包发送状态：方块总数=" + currentBlockTimes.size() +
				", 有效数据数=" + written +
				", 是否发送=" + (written > 0));
	}

}