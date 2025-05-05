package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayClient;
import com.ren.msptdisplay.MSPTDisplayMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockMixin {
    // 只保留方块破坏事件监听，移除neighborUpdate方法注入
    @Inject(method = "onBreak", at = @At("HEAD"))
    private void onBlockBreak(World world, BlockPos pos, BlockState state, PlayerEntity player, CallbackInfo ci) {
        if (!world.isClient) {
            // 清除此位置的MSPT数据
            MSPTDisplayMod.getInstance().removeBlockData(pos);

            // 通知客户端移除此方块的数据
            if (player instanceof ServerPlayerEntity) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(pos);
                ServerPlayNetworking.send((ServerPlayerEntity) player,
                        MSPTDisplayClient.REMOVE_BLOCK_DATA_PACKET_ID, buf);
            }
        }
    }
}