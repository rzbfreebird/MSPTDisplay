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
        // 记录更新开始时间
        blockUpdateStartTimes.get().put(pos, System.nanoTime());

        // 获取当前世界，这里要转换this为World类型
        World world = (World)(Object)this;

        // 对于特殊方块（红石线、中继器等），可能需要额外处理
        if (block == Blocks.REDSTONE_WIRE || block == Blocks.REPEATER ||
                block == Blocks.COMPARATOR || block == Blocks.OBSERVER) {
            // 特殊标记，可能在将来用于过滤或分类
            blockUpdateTypes.get().put(pos, getBlockTypeId(block));
        }
    }

    @Inject(method = "updateNeighbors", at = @At("RETURN"))
    private void onUpdateNeighborsEnd(BlockPos pos, Block block, CallbackInfo ci) {
        // 获取开始时间并移除
        Long startTime = blockUpdateStartTimes.get().remove(pos);
        Integer blockType = blockUpdateTypes.get().remove(pos);

        if (startTime != null) {
            long processingTime = System.nanoTime() - startTime;

            // 获取当前世界
            World world = (World)(Object)this;

            // 记录更新时间，通过MSPTDisplayMod来处理
            getMod().recordBlockUpdateTime(world, pos, processingTime);
        }
    }

    // 获取方块类型ID
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

    // 使用ThreadLocal存储位置和开始时间的映射，以支持并发
    private static final ThreadLocal<Map<BlockPos, Long>> blockUpdateStartTimes =
            ThreadLocal.withInitial(HashMap::new);

    // 存储方块类型信息
    private static final ThreadLocal<Map<BlockPos, Integer>> blockUpdateTypes =
            ThreadLocal.withInitial(HashMap::new);
}