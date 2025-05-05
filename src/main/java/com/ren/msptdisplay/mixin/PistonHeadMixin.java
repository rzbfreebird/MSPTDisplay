package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonHeadBlock.class)
public class PistonHeadMixin {

    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void onStateReplacedStart(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "onStateReplaced", at = @At("RETURN"))
    private void onStateReplacedEnd(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }

    // 修复neighborUpdate方法签名
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