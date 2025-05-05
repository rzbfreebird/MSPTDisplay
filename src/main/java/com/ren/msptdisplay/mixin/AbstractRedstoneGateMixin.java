package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRedstoneGateBlock.class)
public class AbstractRedstoneGateMixin {

    // 监控父类中scheduledTick方法
    @Inject(method = "scheduledTick", at = @At("HEAD"))
    private void onScheduledTickStart(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
    }

    @Inject(method = "scheduledTick", at = @At("RETURN"))
    private void onScheduledTickEnd(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
    }

    // 添加对updatePowered方法的监控（这个方法在父类中）
    @Inject(method = "updatePowered", at = @At("HEAD"))
    private void onUpdatePoweredStart(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "updatePowered", at = @At("RETURN"))
    private void onUpdatePoweredEnd(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }

    // 添加对neighborUpdate的监控（这个方法也在父类中）
    @Inject(method = "neighborUpdate", at = @At("HEAD"))
    private void onNeighborUpdateStart(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "neighborUpdate", at = @At("RETURN"))
    private void onNeighborUpdateEnd(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }
}