package com.admincontrol;

import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class RuleEnforcer {
    private RuleEnforcer() {}

    public static void tick(MinecraftServer server) {
        if (!RuleDefs.on("noDoorAirPockets")) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            if (p.hasStatusEffect(StatusEffects.WATER_BREATHING) || p.hasStatusEffect(StatusEffects.CONDUIT_POWER)) continue;
            if (!p.isTouchingWater()) continue;
            BlockPos eye = BlockPos.ofFloored(p.getEyePos());
            BlockState st = p.getWorld().getBlockState(eye);
            if (st.isIn(BlockTags.DOORS) || st.isIn(BlockTags.TRAPDOORS)) {
                int air = p.getAir() - 5;
                if (air <= -20) {
                    p.setAir(0);
                    p.damage(p.getWorld().getDamageSources().drown(), 2.0f);
                } else {
                    p.setAir(air);
                }
            }
        }
    }
}
