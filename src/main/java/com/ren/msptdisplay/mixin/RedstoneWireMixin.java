package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RedstoneWireBlock.class)
public class RedstoneWireMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateStart(@NotNull World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdateEnd(@NotNull World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"))
    private void onGetStateStart(BlockState state, Direction direction, BlockState neighborState,
                                 WorldAccess world, BlockPos pos, BlockPos neighborPos,
                                 CallbackInfoReturnable<BlockState> cir) {
        if (world instanceof World && !((World)world).isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart((World)world, pos);
        }
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"))
    private void onGetStateEnd(BlockState state, Direction direction, BlockState neighborState,
                               WorldAccess world, BlockPos pos, BlockPos neighborPos,
                               CallbackInfoReturnable<BlockState> cir) {
        if (world instanceof World && !((World)world).isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd((World)world, pos);
        }
    }

    @Inject(method = "neighborUpdate", at = @At("HEAD"))
    public void onNeighborUpdateStart(BlockState state, @NotNull World world, BlockPos pos,
                                      Block sourceBlock, BlockPos sourcePos, boolean notify,
                                      CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "neighborUpdate", at = @At("RETURN"))
    public void onNeighborUpdateEnd(BlockState state, @NotNull World world, BlockPos pos,
                                    Block sourceBlock, BlockPos sourcePos, boolean notify,
                                    CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }
}