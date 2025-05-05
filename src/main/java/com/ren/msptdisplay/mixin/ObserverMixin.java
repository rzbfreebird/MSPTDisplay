package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.ObserverBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ObserverBlock.class)
public class ObserverMixin {

    // 监控scheduledTick方法
    @Inject(method = "scheduledTick", at = @At("HEAD"))
    private void onScheduledTickStart(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
    }

    @Inject(method = "scheduledTick", at = @At("RETURN"))
    private void onScheduledTickEnd(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
    }

    // 监控updateNeighbors方法（侦测器特有）
    @Inject(method = "updateNeighbors", at = @At("HEAD"))
    private void onUpdateNeighborsStart(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "updateNeighbors", at = @At("RETURN"))
    private void onUpdateNeighborsEnd(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }
}