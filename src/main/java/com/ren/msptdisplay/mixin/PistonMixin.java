package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBlock.class)
public class PistonMixin {

    // 处理活塞移动尝试事件
    @Inject(method = "tryMove", at = @At("HEAD"))
    private void onTryMoveStart(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "tryMove", at = @At("RETURN"))
    private void onTryMoveEnd(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }

    // 处理方块事件
    @Inject(method = "onSyncedBlockEvent", at = @At("HEAD"))
    private void onBlockEventStart(BlockState state, World world, BlockPos pos, int type, int data, CallbackInfoReturnable<Boolean> cir) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "onSyncedBlockEvent", at = @At("RETURN"))
    private void onBlockEventEnd(BlockState state, World world, BlockPos pos, int type, int data, CallbackInfoReturnable<Boolean> cir) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }

    // 监控实际移动操作
    @Inject(method = "move", at = @At("HEAD"))
    private void onMoveStart(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void onMoveEnd(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }

    // 监控邻居更新事件 - 修正签名
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