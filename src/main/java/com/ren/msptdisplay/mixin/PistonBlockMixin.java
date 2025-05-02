package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBlock.class)
public class PistonBlockMixin {

    @Inject(method = "onSyncedBlockEvent", at = @At("HEAD"))
    private void onEventStart(BlockState state, World world, BlockPos pos, int type, int data, CallbackInfoReturnable<Boolean> cir) {
        MSPTDisplayMod.recordBlockUpdateStart(pos, world);
    }

    @Inject(method = "onSyncedBlockEvent", at = @At("RETURN"))
    private void onEventEnd(BlockState state, World world, BlockPos pos, int type, int data, CallbackInfoReturnable<Boolean> cir) {
        MSPTDisplayMod.recordBlockUpdateEnd(pos, world);
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void onMoveStart(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
        MSPTDisplayMod.recordPistonUpdate(pos, world, dir);
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void onMoveEnd(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
        MSPTDisplayMod.recordPistonUpdateEnd(pos, world);
    }
}