package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RedstoneWireBlock.class)
public class RedstoneWireMixin {

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateStart(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        MSPTDisplayMod.recordBlockUpdateStart(pos, world);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdateEnd(World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        MSPTDisplayMod.recordBlockUpdateEnd(pos, world);
    }
}