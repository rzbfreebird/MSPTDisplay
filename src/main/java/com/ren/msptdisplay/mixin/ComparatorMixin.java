package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ComparatorBlock.class)
public class ComparatorMixin {

    @Inject(method = "scheduledTick", at = @At("HEAD"))
    private void onScheduledTickStart(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random, CallbackInfo ci) {
        MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
    }

    @Inject(method = "scheduledTick", at = @At("RETURN"))
    private void onScheduledTickEnd(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random, CallbackInfo ci) {
        MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateStart(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockStart(world, pos);
        }
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdateEnd(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) {
            MSPTDisplayMod.getInstance().recordBlockEnd(world, pos);
        }
    }
}