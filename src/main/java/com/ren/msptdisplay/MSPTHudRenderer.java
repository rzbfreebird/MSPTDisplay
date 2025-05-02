package com.ren.msptdisplay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class MSPTHudRenderer {

    // 渲染MSPT文本
    public static void renderMsptText(WorldRenderContext context, BlockPos pos, float mspt) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();

        if (client.world == null || matrices == null) {
            return;
        }

        // 保存矩阵状态
        matrices.push();

        try {
            // 获取渲染位置
            Vec3d renderPos = context.camera().getPos();
            double x = pos.getX() - renderPos.x + 0.5;
            double y = pos.getY() - renderPos.y + 0.5;
            double z = pos.getZ() - renderPos.z + 0.5;

            // 移动到方块位置
            matrices.translate(x, y, z);

            // 使文本始终面向玩家
            matrices.multiply(context.camera().getRotation());

            // 缩放和稍微上移文本
            float scale = 0.015f; // 调整文本大小
            matrices.scale(-scale, -scale, scale);
            matrices.translate(0, -15, 0);

            // 准备文本渲染
            TextRenderer textRenderer = client.textRenderer;
            String msptStr = String.format("%.3f ms", mspt);

            // 确定文本颜色
            int color;
            if (mspt < 0.01f) {
                color = 0x00FF00; // 绿色 - 几乎没有影响
            } else if (mspt < 0.1f) {
                color = 0xFFFF00; // 黄色 - 轻微影响
            } else {
                color = 0xFF0000; // 红色 - 显著影响
            }

            float textWidth = textRenderer.getWidth(msptStr);

            // 在渲染文本前禁用深度测试，确保文本不被方块遮挡
            RenderSystem.disableDepthTest();

            // 正确创建 VertexConsumerProvider
            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());

            // 使用正确的TextRenderer方法 - 基于1.20.1的API
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

            // 使用完整的draw方法
            textRenderer.draw(
                    msptStr,                          // 文本字符串
                    -textWidth / 2,                   // x位置
                    0,                                // y位置
                    color,                            // 颜色
                    true,                             // 显示阴影
                    positionMatrix,                   // 变换矩阵
                    immediate,                        // 顶点消费者
                    TextRenderer.TextLayerType.NORMAL,// 文本层类型
                    0,                                // 背景色
                    15728880                          // 光照
            );

            // 确保所有内容都被渲染
            immediate.draw();

            // 恢复深度测试
            RenderSystem.enableDepthTest();

        } catch (Exception e) {
            System.err.println("[MSPT] 渲染文本时出错: " + e.getMessage());
            e.printStackTrace();
        }

        // 恢复矩阵状态
        matrices.pop();
    }
}