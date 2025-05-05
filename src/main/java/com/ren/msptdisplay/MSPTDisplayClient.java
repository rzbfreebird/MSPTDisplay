package com.ren.msptdisplay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MSPTDisplayClient implements ClientModInitializer {
    // 存储MSPT数据和方块类型
    private static final Map<BlockPos, Float> msptData = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> blockTypes = new ConcurrentHashMap<>();

    // 新增: 方块数据删除的网络包ID
    public static final Identifier REMOVE_BLOCK_DATA_PACKET_ID = new Identifier("msptdisplay", "remove_block_data");

    // 存储服务器总MSPT值
    private static float serverTotalMspt = 0.0f;

    // 渲染配置
    private static final double MAX_RENDER_DISTANCE_SQ = 30 * 30; // 最远渲染30格
    private static final int MAX_RENDER_COUNT = 100; // 每帧最多渲染100个

    // 上次收到数据包的时间
    private static long lastDataReceiveTime = 0;

    // 显示开关状态
    private static boolean serverMsptEnabled = true;
    private static boolean blockMsptEnabled = true;

    // 快捷键绑定
    private static KeyBinding toggleServerMsptKey;
    private static KeyBinding toggleBlockMsptKey;

    @Override
    public void onInitializeClient() {
        // 注册快捷键
        toggleServerMsptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.msptdisplay.toggle_server_mspt", // 翻译键
                GLFW.GLFW_KEY_M, // 默认为M键
                "key.categories.msptdisplay" // 分类
        ));

        toggleBlockMsptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.msptdisplay.toggle_block_mspt", // 翻译键
                GLFW.GLFW_KEY_B, // 默认为B键
                "key.categories.msptdisplay" // 分类
        ));

        // 处理快捷键
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 切换服务器MSPT显示
            if (toggleServerMsptKey.wasPressed()) {
                serverMsptEnabled = !serverMsptEnabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            serverMsptEnabled ? "服务器MSPT显示: §a已启用" : "服务器MSPT显示: §c已禁用"
                    ), true);
                }
            }

            // 切换方块MSPT显示
            if (toggleBlockMsptKey.wasPressed()) {
                blockMsptEnabled = !blockMsptEnabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            blockMsptEnabled ? "方块MSPT显示: §a已启用" : "方块MSPT显示: §c已禁用"
                    ), true);
                }
            }
        });

        // 注册数据包接收处理器
        ClientPlayNetworking.registerGlobalReceiver(MSPTDisplayMod.MSPT_PACKET_ID, (client, handler, buf, responseSender) -> {
            try {
                // 确保缓冲区有足够数据
                if (buf.readableBytes() >= 4) {
                    // 1. 读取服务器总MSPT
                    float newServerMspt = buf.readFloat();

                    // 2. 读取方块数量
                    if (buf.readableBytes() >= 4) {
                        int count = buf.readInt();

                        // 读取方块数据
                        Map<BlockPos, Float> newMsptData = new HashMap<>();
                        Map<BlockPos, Integer> newBlockTypes = new HashMap<>();

                        for (int i = 0; i < count && buf.readableBytes() >= 20; i++) {
                            // 读取坐标（使用3个int而不是BlockPos）
                            int x = buf.readInt();
                            int y = buf.readInt();
                            int z = buf.readInt();
                            BlockPos pos = new BlockPos(x, y, z);

                            // 读取MSPT和方块类型
                            float msptNs = buf.readFloat();
                            int blockType = buf.readInt();

                            newMsptData.put(pos, msptNs);
                            newBlockTypes.put(pos, blockType);
                        }

                        // 更新数据
                        client.execute(() -> {
                            serverTotalMspt = newServerMspt;
                            msptData.clear();
                            msptData.putAll(newMsptData);
                            blockTypes.clear();
                            blockTypes.putAll(newBlockTypes);
                            lastDataReceiveTime = System.currentTimeMillis();
                        });
                    } else {
                        // 只更新服务器MSPT
                        client.execute(() -> {
                            serverTotalMspt = newServerMspt;
                            lastDataReceiveTime = System.currentTimeMillis();
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("处理MSPT数据包时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // 注册方块数据删除包
        ClientPlayNetworking.registerGlobalReceiver(REMOVE_BLOCK_DATA_PACKET_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                removeBlockData(pos);
            });
        });

        // 注册HUD渲染回调来显示服务器总MSPT
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!serverMsptEnabled) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            // 检查数据是否过期
            if (System.currentTimeMillis() - lastDataReceiveTime > 10000) {
                return;
            }

            // 在屏幕底部显示服务器总MSPT
            String msptText = String.format("服务器MSPT: %.2f ms", serverTotalMspt);

            // 根据MSPT值设置颜色
            int color;
            if (serverTotalMspt < 10) color = 0xFF00FF00;      // <10ms - 绿色
            else if (serverTotalMspt < 30) color = 0xFFFFFF00; // <30ms - 黄色
            else if (serverTotalMspt < 50) color = 0xFFFF8800; // <50ms - 橙色
            else color = 0xFFFF0000;                          // >50ms - 红色

            // 计算居中位置
            TextRenderer textRenderer = client.textRenderer;
            int width = textRenderer.getWidth(msptText);
            int x = (client.getWindow().getScaledWidth() - width) / 2;
            int y = client.getWindow().getScaledHeight() - 30; // 距底部30像素

            // 使用DrawContext直接绘制文本
            drawContext.drawText(textRenderer, msptText, x, y, color, true);
        });

        // 注册世界渲染事件来显示方块MSPT
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!blockMsptEnabled) return;

            if (context.world() == null || context.camera() == null) return;

            // 检查数据是否过期
            if (System.currentTimeMillis() - lastDataReceiveTime > 10000) {
                return;
            }

            // 获取玩家位置
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            Vec3d playerPos = client.player.getPos();

            // 限制每帧渲染的方块数量
            int renderCount = 0;

            // 遍历所有方块数据并渲染
            for (Map.Entry<BlockPos, Float> entry : msptData.entrySet()) {
                BlockPos pos = entry.getKey();
                float msptNs = entry.getValue();
                int blockType = blockTypes.getOrDefault(pos, 0);

                // 计算到玩家的距离
                double distanceSq = pos.getSquaredDistance(playerPos);

                // 只渲染30格内的方块，且限制数量
                if (distanceSq < MAX_RENDER_DISTANCE_SQ && renderCount < MAX_RENDER_COUNT) {
                    MSPTHudRenderer.renderMsptText(context, pos, msptNs, blockType);
                    renderCount++;
                }
            }
        });
    }

    // 获取MSPT数据
    public static Map<BlockPos, Float> getMsptData() {
        return msptData;
    }

    // 获取方块类型
    public static Map<BlockPos, Integer> getBlockTypes() {
        return blockTypes;
    }

    // 新增: 移除方块数据的方法
    public static void removeBlockData(BlockPos pos) {
        msptData.remove(pos);
        blockTypes.remove(pos);
    }

    // 新增: 检查服务器MSPT是否启用
    public static boolean isServerMsptEnabled() {
        return serverMsptEnabled;
    }

    // 新增: 检查方块MSPT是否启用
    public static boolean isBlockMsptEnabled() {
        return blockMsptEnabled;
    }
}