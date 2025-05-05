package com.ren.msptdisplay;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class MSPTHudRenderer {

    public static void renderMsptText(WorldRenderContext context, BlockPos pos, float msptNs, int blockType) {
        if (!MSPTDisplayClient.isBlockMsptEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        World world = client.world;
        if (world == null || world.isAir(pos)) {
            MSPTDisplayClient.removeBlockData(pos);
            return;
        }

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();

        if (vertexConsumers == null) {
            vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
            if (vertexConsumers == null) return;
        }

        Vec3d camera = context.camera().getPos();

        double blockX = pos.getX() + 0.5;
        double blockY = pos.getY() + getBlockTypeHeight(blockType);
        double blockZ = pos.getZ() + 0.5;

        double distance = Math.sqrt(
                Math.pow(blockX - camera.x, 2) +
                        Math.pow(blockY - camera.y, 2) +
                        Math.pow(blockZ - camera.z, 2)
        );

        if (distance > 30) return;

        double x = blockX - camera.x;
        double y = blockY - camera.y;
        double z = blockZ - camera.z;

        matrices.push();
        matrices.translate(x, y, z);

        matrices.multiply(context.camera().getRotation());

        float scale = 0.03f;
        if (distance > 10) {
            scale = 0.03f + (float)(distance - 10) * 0.002f;
        }
        matrices.scale(-scale, -scale, scale);

        int color = getMsptColor(msptNs);

        String msptText = formatMsptToMs(msptNs);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        float textWidth = textRenderer.getWidth(msptText);

        int light = 15728880;
        textRenderer.draw(msptText, -textWidth / 2, 0, color, false,
                matrices.peek().getPositionMatrix(), vertexConsumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, light);

        matrices.pop();
    }

    private static String formatMsptToMs(float msptNs) {
        float msptMs = msptNs / 1_000_000.0f;

        msptMs = Math.max(msptMs, 0.001f);

        if (msptMs < 0.01f) {
            return String.format("%.4f ms", msptMs);
        } else if (msptMs < 0.1f) {
            return String.format("%.3f ms", msptMs);
        } else {
            return String.format("%.2f ms", msptMs);
        }
    }

    private static int getMsptColor(float msptNs) {
        float msptMs = msptNs / 1_000_000.0f;

        if (msptMs < 0.001) return 0xFF00FF00; // 绿色
        if (msptMs < 0.01) return 0xFF00FF00;  // 绿色
        if (msptMs < 0.1) return 0xFFAAFF00;   // 黄绿色
        if (msptMs < 0.5) return 0xFFFFFF00;   // 黄色
        if (msptMs < 1.0) return 0xFFFF8800;   // 橙色
        if (msptMs < 3.0) return 0xFFFF0000;   // 红色
        return 0xFFAA0000;                     // 深红色
    }

    private static double getBlockTypeHeight(int blockType) {
        return switch (blockType) {
            case 1 -> 0.3;  // 红石粉
            case 2 -> 0.5;  // 比较器
            case 3 -> 0.5;  // 中继器
            case 4 -> 1.3;  // 侦测器
            case 5 -> 1.3;  // 活塞
            case 6 -> 0.7;  // 红石灯
            case 7 -> 0.7;  // 红石火把
            case 8 -> 0.9;  // 漏斗
            default -> 0.5;
        };
    }
}