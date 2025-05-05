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
    private static final Map<BlockPos, Float> msptData = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> blockTypes = new ConcurrentHashMap<>();

    public static final Identifier REMOVE_BLOCK_DATA_PACKET_ID = new Identifier("msptdisplay", "remove_block_data");

    private static float serverTotalMspt = 0.0f;

    private static final double MAX_RENDER_DISTANCE_SQ = 30 * 30;
    private static final int MAX_RENDER_COUNT = 100;

    private static long lastDataReceiveTime = 0;

    private static boolean serverMsptEnabled = true;
    private static boolean blockMsptEnabled = true;

    private static KeyBinding toggleServerMsptKey;
    private static KeyBinding toggleBlockMsptKey;

    @Override
    public void onInitializeClient() {
        toggleServerMsptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.msptdisplay.toggle_server_mspt",
                GLFW.GLFW_KEY_M,
                "key.categories.msptdisplay"
        ));

        toggleBlockMsptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.msptdisplay.toggle_block_mspt",
                GLFW.GLFW_KEY_B,
                "key.categories.msptdisplay"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleServerMsptKey.wasPressed()) {
                serverMsptEnabled = !serverMsptEnabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            serverMsptEnabled ? "服务器MSPT显示: §a已启用" : "服务器MSPT显示: §c已禁用"
                    ), true);
                }
            }

            if (toggleBlockMsptKey.wasPressed()) {
                blockMsptEnabled = !blockMsptEnabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                            blockMsptEnabled ? "方块MSPT显示: §a已启用" : "方块MSPT显示: §c已禁用"
                    ), true);
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(MSPTDisplayMod.MSPT_PACKET_ID, (client, handler, buf, responseSender) -> {
            try {
                if (buf.readableBytes() >= 4) {
                    float newServerMspt = buf.readFloat();

                    if (buf.readableBytes() >= 4) {
                        int count = buf.readInt();

                        Map<BlockPos, Float> newMsptData = new HashMap<>();
                        Map<BlockPos, Integer> newBlockTypes = new HashMap<>();

                        for (int i = 0; i < count && buf.readableBytes() >= 20; i++) {
                            int x = buf.readInt();
                            int y = buf.readInt();
                            int z = buf.readInt();
                            BlockPos pos = new BlockPos(x, y, z);

                            float msptNs = buf.readFloat();
                            int blockType = buf.readInt();

                            newMsptData.put(pos, msptNs);
                            newBlockTypes.put(pos, blockType);
                        }

                        client.execute(() -> {
                            serverTotalMspt = newServerMspt;
                            msptData.clear();
                            msptData.putAll(newMsptData);
                            blockTypes.clear();
                            blockTypes.putAll(newBlockTypes);
                            lastDataReceiveTime = System.currentTimeMillis();
                        });
                    } else {
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

        ClientPlayNetworking.registerGlobalReceiver(REMOVE_BLOCK_DATA_PACKET_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                removeBlockData(pos);
            });
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!serverMsptEnabled) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            if (System.currentTimeMillis() - lastDataReceiveTime > 10000) {
                return;
            }

            String msptText = String.format("服务器MSPT: %.2f ms", serverTotalMspt);

            int color;
            if (serverTotalMspt < 10) color = 0xFF00FF00;      // <10ms - 绿色
            else if (serverTotalMspt < 30) color = 0xFFFFFF00; // <30ms - 黄色
            else if (serverTotalMspt < 50) color = 0xFFFF8800; // <50ms - 橙色
            else color = 0xFFFF0000;                          // >50ms - 红色

            TextRenderer textRenderer = client.textRenderer;
            int width = textRenderer.getWidth(msptText);
            int x = (client.getWindow().getScaledWidth() - width) / 2;
            int y = client.getWindow().getScaledHeight() - 30;

            drawContext.drawText(textRenderer, msptText, x, y, color, true);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!blockMsptEnabled) return;

            if (context.world() == null || context.camera() == null) return;

            if (System.currentTimeMillis() - lastDataReceiveTime > 10000) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            Vec3d playerPos = client.player.getPos();

            int renderCount = 0;

            for (Map.Entry<BlockPos, Float> entry : msptData.entrySet()) {
                BlockPos pos = entry.getKey();
                float msptNs = entry.getValue();
                int blockType = blockTypes.getOrDefault(pos, 0);

                double distanceSq = pos.getSquaredDistance(playerPos);

                if (distanceSq < MAX_RENDER_DISTANCE_SQ && renderCount < MAX_RENDER_COUNT) {
                    MSPTHudRenderer.renderMsptText(context, pos, msptNs, blockType);
                    renderCount++;
                }
            }
        });
    }

    public static Map<BlockPos, Float> getMsptData() {
        return msptData;
    }

    public static Map<BlockPos, Integer> getBlockTypes() {
        return blockTypes;
    }

    public static void removeBlockData(BlockPos pos) {
        msptData.remove(pos);
        blockTypes.remove(pos);
    }

    public static boolean isServerMsptEnabled() {
        return serverMsptEnabled;
    }

    public static boolean isBlockMsptEnabled() {
        return blockMsptEnabled;
    }
}