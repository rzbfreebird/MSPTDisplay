package com.ren.msptdisplay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class MSPTDisplayClient implements ClientModInitializer {
    // 存储从服务器接收的MSPT数据
    private static final Map<BlockPos, Float> msptData = new HashMap<>();

    // 是否启用HUD显示
    private static boolean hudEnabled = true;

    // 是否在测量模式
    private static boolean measureMode = false;

    // 按键绑定
    private static KeyBinding keyToggleHud;
    private static KeyBinding keyToggleMode;

    @Override
    public void onInitializeClient() {
        System.out.println("[MSPT] 客户端初始化开始");

        // 注册按键绑定
        keyToggleHud = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.msptdisplay.togglehud", // 翻译键
                InputUtil.Type.KEYSYM,       // 输入类型
                GLFW.GLFW_KEY_H,             // 按键 - H
                "category.msptdisplay.keys"  // 类别
        ));

        keyToggleMode = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.msptdisplay.togglemode", // 翻译键
                InputUtil.Type.KEYSYM,        // 输入类型
                GLFW.GLFW_KEY_M,              // 按键 - M
                "category.msptdisplay.keys"   // 类别
        ));

        // 注册数据包接收处理器
        ClientPlayNetworking.registerGlobalReceiver(MSPTDisplayMod.MSPT_PACKET_ID, (client, handler, buf, responseSender) -> {
            try {
                // 立即读取所有数据
                int count = buf.readInt();
                System.out.println("[MSPT] 接收到MSPT数据: " + count + " 个方块");

                // 创建临时数据存储
                final Map<BlockPos, Float> tempData = new HashMap<>();

                // 读取每个方块的位置和MSPT值
                for (int i = 0; i < count; i++) {
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();
                    float mspt = buf.readFloat();

                    BlockPos pos = new BlockPos(x, y, z);
                    tempData.put(pos, mspt);

                    // 临时日志 - 检查数值
                    if (i < 5) { // 仅打印前5个以避免刷屏
                        System.out.println("[MSPT] 样本数据 " + i + ": 位置=" + pos + ", MSPT=" + mspt);
                    }
                }

                // 更新主线程数据
                client.execute(() -> {
                    msptData.clear();
                    msptData.putAll(tempData);
                });
            } catch (Exception e) {
                System.err.println("[MSPT] 处理MSPT数据包时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // 注册客户端Tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 处理按键
            if (keyToggleHud.wasPressed()) {
                hudEnabled = !hudEnabled;
                client.player.sendMessage(Text.literal("§a[MSPT] HUD显示" + (hudEnabled ? "§a已启用" : "§c已禁用")), false);
                System.out.println("[MSPT] HUD显示" + (hudEnabled ? "已启用" : "已禁用"));
            }

            if (keyToggleMode.wasPressed()) {
                measureMode = !measureMode;
                client.player.sendMessage(Text.literal("§a[MSPT] 测量模式" + (measureMode ? "§a已启用" : "§c已禁用")), false);
                System.out.println("[MSPT] 测量模式" + (measureMode ? "已启用" : "已禁用"));

                // 发送模式切换数据包到服务器
                ClientPlayNetworking.send(MSPTDisplayMod.MSPT_MODE_PACKET_ID, MSPTDisplayMod.createModePacket(measureMode));
            }

            // 清理过期的数据(在断开连接时)
            if (client.world == null && !msptData.isEmpty()) {
                msptData.clear();
            }
        });

        // 注册世界渲染事件
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!hudEnabled || MinecraftClient.getInstance().world == null) {
                return;
            }

            try {
                // 为每个保存的MSPT数据渲染HUD
                for (Map.Entry<BlockPos, Float> entry : msptData.entrySet()) {
                    BlockPos pos = entry.getKey();
                    float mspt = entry.getValue();

                    // 确保只在可见距离内渲染
                    if (MinecraftClient.getInstance().player != null) {
                        double distance = MinecraftClient.getInstance().player.getBlockPos().getSquaredDistance(pos);
                        if (distance <= 32 * 32) { // 32格距离的平方
                            MSPTHudRenderer.renderMsptText(context, pos, mspt);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[MSPT] 渲染MSPT值时出错: " + e.getMessage());
                e.printStackTrace();
            }
        });

        System.out.println("[MSPT] 客户端初始化完成");
    }

    // 获取MSPT数据（供其他类使用）
    public static Map<BlockPos, Float> getMsptData() {
        return msptData;
    }

    // 检查是否处于测量模式
    public static boolean isMeasureMode() {
        return measureMode;
    }
}