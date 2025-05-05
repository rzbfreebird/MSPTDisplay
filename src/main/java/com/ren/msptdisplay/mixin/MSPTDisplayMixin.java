package com.ren.msptdisplay.mixin;

import com.ren.msptdisplay.MSPTDisplayMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerWorld.class)
public class MSPTDisplayMixin {
    private static MSPTDisplayMod msptMod;

    private static MSPTDisplayMod getMod() {
        if (msptMod == null) {
            msptMod = MSPTDisplayMod.getInstance();
        }
        return msptMod;
    }

    @Inject(method = "updateNeighbors", at = @At("HEAD"))
    private void onUpdateNeighborsStart(BlockPos pos, Block block, CallbackInfo ci) {
        blockUpdateStartTimes.get().put(pos, System.nanoTime());

        World world = (World)(Object)this;

        if (block == Blocks.REDSTONE_WIRE || block == Blocks.REPEATER ||
                block == Blocks.COMPARATOR || block == Blocks.OBSERVER) {
            blockUpdateTypes.get().put(pos, getBlockTypeId(block));
        }
    }

    @Inject(method = "updateNeighbors", at = @At("RETURN"))
    private void onUpdateNeighborsEnd(BlockPos pos, Block block, CallbackInfo ci) {
        Long startTime = blockUpdateStartTimes.get().remove(pos);
        Integer blockType = blockUpdateTypes.get().remove(pos);

        if (startTime != null) {
            long processingTime = System.nanoTime() - startTime;

            World world = (World)(Object)this;

            getMod().recordBlockUpdateTime(world, pos, processingTime);
        }
    }

    private int getBlockTypeId(Block block) {
        if (block == Blocks.REDSTONE_WIRE) return 1;        // 红石粉
        if (block == Blocks.COMPARATOR) return 2;           // 比较器
        if (block == Blocks.REPEATER) return 3;             // 中继器
        if (block == Blocks.OBSERVER) return 4;             // 侦测器
        if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON) return 5; // 活塞
        if (block == Blocks.REDSTONE_LAMP) return 6;        // 红石灯
        if (block == Blocks.REDSTONE_TORCH) return 7;       // 红石火把
        return 0; // 其他方块
    }

    private static final ThreadLocal<Map<BlockPos, Long>> blockUpdateStartTimes =
            ThreadLocal.withInitial(HashMap::new);

    private static final ThreadLocal<Map<BlockPos, Integer>> blockUpdateTypes =
            ThreadLocal.withInitial(HashMap::new);
}