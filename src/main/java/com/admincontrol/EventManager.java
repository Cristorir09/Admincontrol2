package com.admincontrol;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * Evento de reparto: divide a los participantes de forma ALEATORIA y EQUILIBRADA entre los anfitriones (admins)
 * y los teletransporta repartidos alrededor de cada anfitrión.
 */
public final class EventManager {
    private EventManager() {}

    private record Origin(RegistryKey<World> world, double x, double y, double z, float yaw, float pitch) {}

    private static final Map<UUID, Origin> ORIGINS = new LinkedHashMap<>();
    private static Map<UUID, List<UUID>> plan = new LinkedHashMap<>();
    private static int countdown = -1;
    private static final Random RNG = new Random();

    public static List<ServerPlayerEntity> hosts(MinecraftServer s) {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (AdminControl.CFG.eventHosts.contains(p.getUuidAsString())) l.add(p);
        }
        return l;
    }

    public static List<ServerPlayerEntity> participants(MinecraftServer s) {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            String u = p.getUuidAsString();
            if (AdminControl.CFG.eventHosts.contains(u) || AdminControl.CFG.eventExcluded.contains(u)) continue;
            l.add(p);
        }
        return l;
    }

    public static Map<UUID, List<UUID>> plan() {
        return plan;
    }

    public static boolean running() {
        return countdown >= 0;
    }

    public static int returnableCount() {
        return ORIGINS.size();
    }

    /** Baraja participantes y anfitriones y reparte por turnos: los grupos quedan con diferencia máxima de 1. */
    public static Map<UUID, List<UUID>> buildPlan(MinecraftServer s) {
        List<ServerPlayerEntity> hs = hosts(s);
        List<ServerPlayerEntity> ps = participants(s);
        Collections.shuffle(hs, RNG);
        Collections.shuffle(ps, RNG);
        Map<UUID, List<UUID>> np = new LinkedHashMap<>();
        for (ServerPlayerEntity h : hs) np.put(h.getUuid(), new ArrayList<>());
        if (!hs.isEmpty()) {
            for (int i = 0; i < ps.size(); i++) {
                np.get(hs.get(i % hs.size()).getUuid()).add(ps.get(i).getUuid());
            }
        }
        plan = np;
        return np;
    }

    /** Inicia la cuenta atrás (o ejecuta ya si es 0). Usa el reparto actual si existe, si no crea uno. */
    public static String start(MinecraftServer s, ServerPlayerEntity by) {
        if (countdown >= 0) return "§cYa hay un evento en cuenta atrás.";
        if (hosts(s).isEmpty()) return "§cNo hay anfitriones conectados. Elígelos primero.";
        if (participants(s).isEmpty()) return "§cNo hay participantes conectados.";
        if (plan.isEmpty()) buildPlan(s);
        int secs = Math.max(0, AdminControl.CFG.eventCountdown);
        if (secs == 0) {
            execute(s);
        } else {
            countdown = secs * 20;
        }
        return "§aEvento iniciado.";
    }

    public static void cancel(MinecraftServer s) {
        if (countdown < 0) return;
        countdown = -1;
        broadcastPlanned(s, "§c¡Evento cancelado!");
    }

    public static void tick(MinecraftServer s) {
        if (countdown < 0) return;
        if (countdown == 0) {
            countdown = -1;
            execute(s);
            return;
        }
        if (countdown % 20 == 0) {
            int sec = countdown / 20;
            broadcastPlanned(s, "§e¡Teletransporte en §6" + sec + "§e!");
            for (UUID id : allPlanned()) {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
                if (p != null) p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
            }
        }
        countdown--;
    }

    private static List<UUID> allPlanned() {
        List<UUID> all = new ArrayList<>();
        for (Map.Entry<UUID, List<UUID>> e : plan.entrySet()) {
            all.add(e.getKey());
            all.addAll(e.getValue());
        }
        return all;
    }

    private static void broadcastPlanned(MinecraftServer s, String msg) {
        for (UUID id : allPlanned()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
            if (p != null) p.sendMessage(Text.literal(msg), true);
        }
    }

    private static void execute(MinecraftServer s) {
        PlayerManager pm = s.getPlayerManager();
        int moved = 0;
        for (Map.Entry<UUID, List<UUID>> e : plan.entrySet()) {
            ServerPlayerEntity host = pm.getPlayer(e.getKey());
            if (host == null) continue;
            ServerWorld hw = (ServerWorld) host.getWorld();
            String hostName = host.getName().getString();
            for (UUID id : e.getValue()) {
                ServerPlayerEntity p = pm.getPlayer(id);
                if (p == null) continue;
                ORIGINS.put(id, new Origin(p.getWorld().getRegistryKey(), p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch()));
                Vec3d dest = AdminTools.scatterAround(hw, host.getBlockPos(), AdminControl.CFG.eventRadius, RNG);

                ServerWorld from = (ServerWorld) p.getWorld();
                from.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
                from.spawnParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 25, 0.3, 0.5, 0.3, 0.1);

                p.stopRiding();
                float yaw = (float) (Math.toDegrees(Math.atan2(host.getZ() - dest.z, host.getX() - dest.x))) - 90f;
                p.teleport(hw, dest.x, dest.y, dest.z, yaw, 0f);
                p.fallDistance = 0;

                hw.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);
                p.sendMessage(Text.literal("§6¡Evento! §fTe tocó el equipo de §e" + hostName), false);
                moved++;
            }
            host.sendMessage(Text.literal("§aTu equipo llegó: §f" + e.getValue().size() + " jugadores."), false);
        }
        plan = new LinkedHashMap<>();
        AdminControl.LOGGER.info("Evento: {} jugadores repartidos", moved);
    }

    /** Devuelve a todos a donde estaban antes del reparto. */
    public static int returnAll(MinecraftServer s) {
        int n = 0;
        for (Map.Entry<UUID, Origin> e : new ArrayList<>(ORIGINS.entrySet())) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getKey());
            if (p == null) continue;
            Origin o = e.getValue();
            ServerWorld w = s.getWorld(o.world());
            if (w == null) w = s.getOverworld();
            p.stopRiding();
            p.teleport(w, o.x(), o.y(), o.z(), o.yaw(), o.pitch());
            p.fallDistance = 0;
            p.sendMessage(Text.literal("§6Fin del evento: volviste a tu posición anterior."), true);
            n++;
        }
        ORIGINS.clear();
        return n;
    }
}
