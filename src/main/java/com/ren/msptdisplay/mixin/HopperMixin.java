package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public class HopperMixin {
    private static final ThreadLocal<Long> START_TIME_EXTRACT = new ThreadLocal<>();
    private static final ThreadLocal<Long> START_TIME_INSERT = new ThreadLocal<>();

    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"))
    private static void onExtractStart(World world, net.minecraft.block.entity.Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        START_TIME_EXTRACT.set(System.nanoTime());
    }

    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("RETURN"))
    private static void onExtractEnd(World world, net.minecraft.block.entity.Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;

        Long startTime = START_TIME_EXTRACT.get();
        if (startTime == null) return;

        long timeNs = System.nanoTime() - startTime;

        if (hopper instanceof HopperBlockEntity) {
            HopperBlockEntity hopperEntity = (HopperBlockEntity) hopper;
            BlockPos pos = BlockPos.ofFloored(hopperEntity.getHopperX(), hopperEntity.getHopperY(), hopperEntity.getHopperZ());
            MSPTDisplayMod.getInstance().recordHopperTime(world, pos, timeNs);
        }
    }

    @Inject(method = "insert(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/inventory/Inventory;)Z", at = @At("HEAD"))
    private static void onInsertStart(World world, BlockPos pos, BlockState state, Inventory inventory, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        START_TIME_INSERT.set(System.nanoTime());
    }

    @Inject(method = "insert(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/inventory/Inventory;)Z", at = @At("RETURN"))
    private static void onInsertEnd(World world, BlockPos pos, BlockState state, Inventory inventory, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;

        Long startTime = START_TIME_INSERT.get();
        if (startTime == null) return;

        long timeNs = System.nanoTime() - startTime;

        if (inventory instanceof HopperBlockEntity) {
            MSPTDisplayMod.getInstance().recordHopperTime(world, pos, timeNs);
        }
    }

    @Inject(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z", at = @At("HEAD"))
    private static void onItemEntityExtractStart(Inventory inventory, net.minecraft.entity.ItemEntity itemEntity, CallbackInfoReturnable<Boolean> cir) {
        if (inventory instanceof HopperBlockEntity && ((HopperBlockEntity)inventory).getWorld() != null && !((HopperBlockEntity)inventory).getWorld().isClient) {
            START_TIME_EXTRACT.set(System.nanoTime());
        }
    }

    @Inject(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z", at = @At("RETURN"))
    private static void onItemEntityExtractEnd(Inventory inventory, net.minecraft.entity.ItemEntity itemEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!(inventory instanceof HopperBlockEntity)) return;

        HopperBlockEntity hopper = (HopperBlockEntity)inventory;
        World world = hopper.getWorld();
        if (world == null || world.isClient) return;

        Long startTime = START_TIME_EXTRACT.get();
        if (startTime == null) return;

        long timeNs = System.nanoTime() - startTime;
        BlockPos pos = hopper.getPos();
        MSPTDisplayMod.getInstance().recordHopperTime(world, pos, timeNs);
    }
}