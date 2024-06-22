package me.matsubara.cigarette.cigarette;

import com.google.common.base.Preconditions;
import lombok.Getter;
import me.matsubara.cigarette.CigarettePlugin;
import me.matsubara.cigarette.command.MainCommand;
import me.matsubara.cigarette.util.PluginUtils;
import me.matsubara.cigarette.util.stand.PacketStand;
import me.matsubara.cigarette.util.stand.StandSettings;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public final class Cigarette extends BukkitRunnable {

    private final CigarettePlugin plugin;

    private final Player owner;
    private final CigaretteType type;
    private final PacketStand stand;
    private final int taskId;
    private final Map<UUID, Long> secondHandSmoke;
    private int count = 0;

    public Cigarette(@NotNull CigarettePlugin plugin, @NotNull Player owner, @NotNull CigaretteType type) {
        this.plugin = plugin;
        this.owner = owner;
        this.type = type;

        // Copy location from the player and add an offset.
        Location location = owner.getLocation().clone();
        location.add(PluginUtils.offsetVector(type.getMaterialType().getOffset(), location.getYaw(), location.getPitch()));

        this.stand = new PacketStand(location.add(getHeight()), createSetttings(), true, plugin.getConfig().getInt("render-distance"));

        // Play lit sound.
        playSound(stand.getLocation(), type.getLightSound());
        if (!type.getEffects().isEmpty()) owner.addPotionEffects(type.getEffects());
        show(true);

        this.taskId = runTaskTimer(plugin, 0L, 1L).getTaskId();

        this.secondHandSmoke = new HashMap<>();
        plugin.getCigaretteManager().getCigarettes().add(this);
    }

    @Override
    public void run() {
        // Extinguish if time is over.
        if (count / 20 == type.getDuration()) {
            owner.sendMessage(plugin.getString(MainCommand.MSG_EXTINGUISH));
            extinguish();
            cancel();
        }

        // Spawn particles & play ambient sound.
        if (count % 40 == 0 && isVisible()) {
            Location effectsLocation = type.getMaterialType().getEffectsLocationFromPlayer(owner);
            playSound(effectsLocation, type.getSmokeSound());
            if (type.getSmoke() != null) type.getSmoke().playAt(effectsLocation);
        }

        if (type.isSecondHandSmoke()) {
            for (Entity near : owner.getNearbyEntities(2.5, 2.5d, 2.5d)) {
                if (!(near instanceof Player player)
                        || player.equals(owner)
                        || player.hasPermission("cigarette.bypass.secondhandsmoke")) continue;

                Long last = secondHandSmoke.getOrDefault(player.getUniqueId(), 0L);
                if (System.currentTimeMillis() < last) continue;

                // Half of resting time.
                int time = Math.max(1, (type.getDuration() - count / 20) / 2);
                for (PotionEffect effect : type.getEffects()) {
                    player.addPotionEffect(new PotionEffect(
                            effect.getType(),
                            time * 20,
                            effect.getAmplifier(),
                            effect.isAmbient(),
                            effect.hasParticles(),
                            effect.hasIcon()));
                }

                secondHandSmoke.put(player.getUniqueId(), System.currentTimeMillis() + time * 1000L);
            }
        }

        // Teleport ciggy.
        Location location = owner.getLocation().clone();
        location.add(PluginUtils.offsetVector(type.getMaterialType().getOffset(), location.getYaw(), location.getPitch()));

        stand.teleport(location.add(getHeight()));

        float pitch = owner.getLocation().getPitch();
        show((pitch > -14.0f) && (pitch < 20.0f));

        count++;
    }

    private Vector getHeight() {
        return type.getMaterialType().getHeight(owner.isSneaking());
    }

    public void extinguish() {
        playSound(stand.getLocation(), type.getExtinguishSound());
        stand.destroy();
        plugin.getCigaretteManager().getCigarettes().remove(this);

        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);

        if (owner == null || !owner.isOnline()) return;
        for (PotionEffect effect : type.getEffects()) {
            owner.removePotionEffect(effect.getType());
        }
    }

    private void playSound(@NotNull Location location, String soundName) {
        if (soundName == null) return;

        Preconditions.checkArgument(location.getWorld() != null, "World can't be null.");
        String[] split = StringUtils.split(StringUtils.deleteWhitespace(soundName), ',');
        if (split.length == 0) split = StringUtils.split(soundName, ' ');

        Sound sound = Sound.valueOf(split[0]);

        float volume = 1.0f;
        float pitch = 1.0f;

        try {
            if (split.length > 1) {
                volume = Float.parseFloat(split[1]);
                if (split.length > 2) pitch = Float.parseFloat(split[2]);
            }
        } catch (NumberFormatException ignored) {
        }

        location.getWorld().playSound(location, sound, volume, pitch);
    }

    public boolean isVisible() {
        if (!stand.getSettings().hasEquipment()) return false;


        ItemStack handItem = stand.getSettings().getEquipment().get(PacketStand.ItemSlot.MAINHAND);
        return handItem != null && handItem.getType() != Material.AIR.asItemType();
    }

    public void show(boolean show) {
        stand.setEquipment(show ? type.getItem() : new ItemStack(Material.AIR), PacketStand.ItemSlot.MAINHAND);
    }

    private @NotNull StandSettings createSetttings() {
        StandSettings settings = new StandSettings();

        settings.setArms(false);
        settings.setBasePlate(false);
        settings.setInvisible(true);
        settings.setRightArmPose(type.getMaterialType().getAngle());
        settings.setSmall(type.getMaterialType().isSmall());

        return settings;
    }
}
