package dev.insanmiy.practiceplugin.nms.v1_21_R3;

import dev.insanmiy.practiceplugin.bot.*;

import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.model.DamagePressure;
import dev.insanmiy.practiceplugin.model.HandTiming;
import dev.insanmiy.practiceplugin.model.HealingPlan;
import dev.insanmiy.practiceplugin.model.RepairCadence;
import dev.insanmiy.practiceplugin.model.SplashPlan;
import dev.insanmiy.practiceplugin.model.WebCadence;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

final class UtilityBrain {
  private final ServerPlayer handle;
  private int nextPotion, nextPearl, nextBow, nextTotem;
  private int nextFood;
  private int nextHeal, seekingSpaceSince = -1;
  private boolean pendingHeal;
  private final DamagePressure pressure = new DamagePressure();
  boolean naturalRegeneration = true;
  dev.insanmiy.practiceplugin.model.BotOptions options =
      dev.insanmiy.practiceplugin.model.BotOptions.defaults();
  private final RepairCadence repair = new RepairCadence();
  private final WebCadence webs = new WebCadence();
  private int lastHit = -1000;
  private BlockPos mining;
  private int miningSince;
  private boolean miningStopSent;
  private int preparingSplash = -1, splashUntil, pendingPotion = -1;
  private Vector splashDirection;
  private Location placedWater;
  private int nextWater, retrieveWater;
  private final HandTiming handTiming = new HandTiming();
  private PreparedAction prepared;
  private Player opponent;
  private int currentTick;
  private int nextEscapeCheck;
  private Location escapeOrigin, escapeTarget;
  private Vector cachedEscape;
  private boolean repairWorking;
  private int repairSeekingSince = -1, nextRepairSeek, nextArmorSwap;

  private record PreparedAction(
      int slot, ItemStack item, Runnable action, java.util.function.BooleanSupplier valid) {}

  UtilityBrain(ServerPlayer handle) {
    this.handle = handle;
  }

  void reset(boolean newRound) {
    nextPotion = nextPearl = nextBow = nextTotem = 0;
    nextFood = 0;
    nextHeal = 0;
    seekingSpaceSince = -1;
    pendingHeal = false;
    pressure.reset();
    if (newRound) repair.reset();
    webs.reset();
    lastHit = -1000;
    abortMining();
    preparingSplash = -1;
    pendingPotion = -1;
    splashUntil = 0;
    splashDirection = null;
    placedWater = null;
    nextWater = retrieveWater = 0;
    prepared = null;
    handTiming.reset();
    opponent = null;
    escapeOrigin = escapeTarget = null;
    cachedEscape = null;
    nextEscapeCheck = 0;
    repairWorking = false;
    repairSeekingSince = -1;
    nextRepairSeek = nextArmorSwap = 0;
  }

  private Player player() {
    return handle.getBukkitEntity();
  }

  boolean busy(int tick) {
    return repairWorking
        || prepared != null
        || handTiming.waiting(tick)
        || handTiming.settling(tick)
        || mining != null
        || preparingSplash >= 0
        || tick < splashUntil
        || handle.isUsingItem();
  }

  void onOpponentHit(int tick) {
    lastHit = tick;
    repair.stop(tick);
  }

  void recordDamage(int tick, double amount) {
    pressure.record(tick, amount);
  }

  void repairImpact(int tick, boolean repaired) {
    repair.landed(tick, repaired);
  }

  void onOpponentPearled(Player target, int tick) {
    if (!player().hasCooldown(Material.ENDER_PEARL)) {
      nextPearl = Math.min(nextPearl, tick);
    }
  }

  void pause(int tick) {
    repair.stop(tick);
    repairWorking = false;
  }

  boolean act(Player target, Difficulty difficulty, int tick) {
    currentTick = tick;
    repairWorking = false;
    opponent = target;
    Player self = player();
    double distance = self.getLocation().distance(target.getLocation());
    double fraction =
        self.getHealth() / self.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
    HealingPlan.State healing = healingState(target, difficulty, tick);
    boolean emergency =
        HealingPlan.urgent(healing)
            && healing.splash()
            && healing.maximum() - healing.health() >= 2;

    if (emergency
        && preparingSplash < 0
        && tick >= splashUntil
        && !(handle.isUsingItem() && handle.getUseItemRemainingTicks() <= 6)) {
      prepared = null;
      handTiming.reset();
      handle.stopUsingItem();
      abortMining();
      repair.stop(tick);
    }
    if (prepared != null) {
      PreparedAction intent = prepared;
      if (self.getInventory().getHeldItemSlot() != intent.slot()
          || !intent.item().isSimilar(self.getInventory().getItemInMainHand())
          || !intent.valid().getAsBoolean()) {
        prepared = null;
        handTiming.reset();
        repair.stop(tick);
      } else {
        if (handTiming.ready(tick)) {
          prepared = null;
          intent.action().run();
          handTiming.used(tick);
        }
        return true;
      }
    }
    if (handTiming.settling(tick)) return true;
    manageOffhand(tick, difficulty, fraction);
    Material active = self.getInventory().getItemInMainHand().getType();
    if (tick < splashUntil) {
      followSplash();
      return true;
    }
    if (preparingSplash >= 0 && pendingPotion >= 0) {
      ItemStack pending = self.getInventory().getItem(pendingPotion);
      if (pending != null && pending.getType() == Material.SPLASH_POTION) {
        if (selfPotion(pendingPotion, target, tick, emergency ? .2 : fraction)) {
          if (pendingHeal) nextHeal = tick + 12;
          else nextPotion = tick + 24;
          pendingHeal = false;
        }
        return true;
      }
      preparingSplash = pendingPotion = -1;
    }
    if (handle.isUsingItem() && active == Material.POTION) {
      retreat();
      return true;
    }
    if (handle.isUsingItem() && active == Material.BOW) {
      aim(target, 3.0, difficulty.aimError());
      handle.zza = distance < 8 ? -.5f : 0;
      handle.xxa = .25f;
      handle.setSprinting(false);
      if (distance < 4 || fraction < .4) {
        handle.stopUsingItem();
        return false;
      }
      if (handle.getTicksUsingItem() >= 20 && self.hasLineOfSight(target)) {
        handle.releaseUsingItem();
        handTiming.used(tick);
        nextBow = tick + Math.max(6, difficulty.decisionTicks() * 2);
      }
      return true;
    }

    if (handle.isUsingItem() && active.isEdible()) {
      retreat();
      return true;
    }
    if (water(tick)) {
      repair.stop(tick);
      return true;
    }

    if (options.useHealing() && heal(target, tick, fraction, healing)) return true;
    preparingSplash = -1;
    if (escapeWeb(tick)) {
      repair.stop(tick);
      return true;
    }
    if (replaceArmor(tick)) return true;
    if (!options.useUtilities()) return false;

    if (potUp(target, difficulty, tick)) return true;

    if (repair(tick, distance, fraction)) return true;
    if (tick >= nextPearl
        && !self.hasCooldown(Material.ENDER_PEARL)
        && distance > 10
        && distance <= 48
        && fraction > .35
        && self.hasLineOfSight(target)) {
      int pearl = find(i -> i.getType() == Material.ENDER_PEARL);
      if (pearl >= 0
          && target.getLocation().getY() > target.getWorld().getMinHeight() + 2
          && !target.getLocation().getBlock().isLiquid()) {
        aim(target, 1.5, difficulty.aimError());
        prepare(
            pearl,
            tick,
            () -> {
              aim(target, 1.5, difficulty.aimError());
              BotLook.publish(handle);
              nativeUse();
              nextPearl = tick + 100;
            },
            () ->
                player().hasLineOfSight(target)
                    && player().getLocation().distanceSquared(target.getLocation()) > 64);
        return true;
      }
    }
    if (webs.ready(tick) && distance < 3.5 && distance > 1.8 && self.hasLineOfSight(target)) {
      int web = find(i -> i.getType() == Material.COBWEB);
      Location at = target.getLocation().getBlock().getLocation();
      Vector towardSelf =
          self.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
      boolean approaching = target.getVelocity().clone().setY(0).dot(towardSelf.normalize()) > .04;
      boolean opportunity = approaching || target.isBlocking() || fraction < .6;
      if (web >= 0
          && webs.shouldPlace(tick, distance, nearbyWeb(at), opportunity)
          && webReplaceable(at.getBlock())
          && at.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
        BlockPos support = new BlockPos(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ());
        lookAt(at.clone().add(.5, 0, .5));
        prepare(
            web,
            tick,
            () -> {
              lookAt(at.clone().add(.5, 0, .5));
              handle.gameMode.useItemOn(
                  handle,
                  ((net.minecraft.server.level.ServerLevel) handle.level()),
                  handle.getMainHandItem(),
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(
                      new Vec3(at.getX() + .5, at.getY(), at.getZ() + .5),
                      Direction.UP,
                      support,
                      false));
              handle.swing(InteractionHand.MAIN_HAND);
              webs.attempted(currentTick, at.getBlock().getType() == Material.COBWEB);
            },
            () ->
                webReplaceable(at.getBlock())
                    && at.clone().subtract(0, 1, 0).getBlock().getType().isSolid()
                    && at.clone().add(.5, .5, .5).distanceSquared(player().getEyeLocation())
                        <= 20.25
                    && at.distanceSquared(target.getLocation()) < 4
                    && !nearbyWeb(at));
        return true;
      }
    }
    if (tick >= nextPotion && distance >= 4 && distance <= 8 && self.hasLineOfSight(target)) {
      for (PotionEffectType effect :
          List.of(
              PotionEffectType.POISON,
              PotionEffectType.SLOWNESS,
              PotionEffectType.WEAKNESS,
              PotionEffectType.INSTANT_DAMAGE)) {
        if (target.hasPotionEffect(effect)) continue;
        int potion = potion(effect, true);
        if (potion >= 0) {
          aim(target, .5, difficulty.aimError());
          prepare(
              potion,
              tick,
              () -> {
                aim(target, .5, difficulty.aimError());
                nativeUse();
                nextPotion = tick + 60;
              },
              () ->
                  player().hasLineOfSight(target)
                      && player().getLocation().distanceSquared(target.getLocation()) >= 9);
          return true;
        }
      }
    }
    if (tick >= nextBow
        && distance >= 7
        && distance <= 26
        && self.hasLineOfSight(target)
        && find(i -> dev.insanmiy.practiceplugin.config.Kit.ammunition(i.getType())) >= 0) {
      int bow = find(i -> i.getType() == Material.BOW);
      if (bow >= 0) {
        aim(target, 3, difficulty.aimError());
        prepare(
            bow,
            tick,
            this::nativeUse,
            () ->
                player().hasLineOfSight(target)
                    && player().getLocation().distanceSquared(target.getLocation()) >= 16);
        return true;
      }
    }
    return false;
  }

  private boolean repair(int tick, double distance, double healthFraction) {
    int xp = find(i -> i.getType() == Material.EXPERIENCE_BOTTLE);
    double damage = damagedMendingArmor();
    boolean safe =
        distance >= (repair.active() ? 4.5 : 6) && healthFraction >= .5 && tick - lastHit >= 20;
    if (!repair.update(tick, damage, safe, xp >= 0)) {
      if (!repair.wantsRepair(tick, damage, xp >= 0) || healthFraction < .5) {
        repairSeekingSince = -1;
        return false;
      }

      if (tick < nextRepairSeek || escapeDirection() == null) return false;
      if (repairSeekingSince < 0) repairSeekingSince = tick;
      if (tick - repairSeekingSince >= 60) {
        repairSeekingSince = -1;
        nextRepairSeek = tick + 100;
        return false;
      }
      repairWorking = true;
      retreatForRepair();
      return true;
    }
    repairSeekingSince = -1;
    repairWorking = true;
    retreatForRepair();
    if (repair.ready(tick)) {
      int before = player().getInventory().getItem(xp).getAmount();
      handle.setXRot(90);
      prepare(
          xp,
          tick,
          () -> {
            handle.setXRot(90);
            nativeUse();
            if (player().getInventory().getItemInMainHand().getAmount() < before)
              repair.thrown(currentTick);
            else repair.stop(currentTick);
          },
          () ->
              opponent != null
                  && player().getLocation().distanceSquared(opponent.getLocation()) >= 20.25
                  && currentTick - lastHit >= 20
                  && player().getHealth()
                      >= player().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                          * .5);
    }
    return true;
  }

  private void retreatForRepair() {
    retreat();
    if (handle.zza > 0) {
      handle.zza = 1;
      handle.setSprinting(true);
    }
  }

  private boolean webReplaceable(org.bukkit.block.Block block) {
    return block.getType() != Material.LAVA
        && ((org.bukkit.craftbukkit.CraftWorld) block.getWorld())
            .getHandle()
            .getBlockState(new BlockPos(block.getX(), block.getY(), block.getZ()))
            .canBeReplaced();
  }

  private boolean replaceArmor(int tick) {
    if (tick < nextArmorSwap) return false;
    ItemStack[] worn = player().getInventory().getArmorContents();
    String[] suffixes = {"_BOOTS", "_LEGGINGS", "_CHESTPLATE", "_HELMET"};
    for (int part = 0; part < worn.length; part++) {
      ItemStack current = worn[part];
      double remaining = remainingDurability(current);
      if (remaining > .15) continue;
      String suffix = suffixes[part];
      int spare =
          find(
              item ->
                  item.getType().name().endsWith(suffix)
                      && remainingDurability(item) >= .5
                      && (current == null
                          || current.getType().isAir()
                          || item.getType() == current.getType()));
      if (spare < 0) continue;
      repair.stop(tick);
      nextArmorSwap = tick + 100;

      prepare(spare, tick, this::nativeUse, () -> true);
      return true;
    }
    nextArmorSwap = tick + 20;
    return false;
  }

  private double remainingDurability(ItemStack item) {
    if (item == null || item.getType().isAir()) return 0;
    if (!(item.getItemMeta() instanceof Damageable damage)
        || item.getType().getMaxDurability() <= 0) return 1;
    return 1 - (double) damage.getDamage() / item.getType().getMaxDurability();
  }

  private boolean water(int tick) {
    Player self = player();
    if (placedWater != null && tick >= retrieveWater && self.getFireTicks() <= 0) {
      int bucket = find(i -> i.getType() == Material.BUCKET);
      if (bucket >= 0
          && placedWater.getWorld().equals(self.getWorld())
          && placedWater.distanceSquared(self.getEyeLocation()) <= 16
          && placedWater.getBlock().getType() == Material.WATER) {
        Location look =
            self.getEyeLocation()
                .setDirection(
                    placedWater
                        .clone()
                        .add(.5, .5, .5)
                        .toVector()
                        .subtract(self.getEyeLocation().toVector()));
        handle.setYRot(look.getYaw());
        handle.setXRot(look.getPitch());
        BotLook.publish(handle);
        Location source = placedWater.clone();
        prepare(
            bucket,
            tick,
            () -> {
              lookAt(source.clone().add(.5, .5, .5));
              nativeUse();
              placedWater = null;
            },
            () ->
                source.getBlock().getType() == Material.WATER
                    && source.distanceSquared(player().getEyeLocation()) <= 16);
        return true;
      }
      if (tick >= retrieveWater + 80) placedWater = null;
    }
    boolean trappedInWeb = inWeb(self);
    if (tick < nextWater
        || self.getWorld().getEnvironment() == World.Environment.NETHER
        || !(self.getFireTicks() > 0 && !self.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)
            || handle.fallDistance > 3
            || trappedInWeb)) return false;
    int bucket = find(i -> i.getType() == Material.WATER_BUCKET);
    if (bucket < 0) return false;
    var hit =
        self.getWorld()
            .rayTraceBlocks(
                self.getEyeLocation(), new Vector(0, -1, 0), 4.5, FluidCollisionMode.NEVER, true);
    if (hit == null
        || hit.getHitBlock() == null
        || hit.getHitBlockFace() != org.bukkit.block.BlockFace.UP) return false;
    var destination = hit.getHitBlock().getRelative(org.bukkit.block.BlockFace.UP);
    if (!(destination.getType().isAir() || destination.getType() == Material.COBWEB)) return false;
    handle.setXRot(90);
    BotLook.publish(handle);
    prepare(
        bucket,
        tick,
        () -> {
          handle.setXRot(90);
          nativeUse();
          nextWater = currentTick + (trappedInWeb ? 15 : 200);
          placedWater = destination.getLocation();
          retrieveWater = currentTick + (trappedInWeb ? 4 : 20);
        },
        () ->
            destination.getLocation().distanceSquared(player().getEyeLocation()) <= 20.25
                && (destination.getType().isAir()
                    || destination.getType() == Material.COBWEB
                    || destination.getType() == Material.WATER));
    handle.zza = handle.xxa = 0;
    handle.setSprinting(false);
    return true;
  }

  private boolean inWeb(Player player) {
    var body = player.getBoundingBox();
    for (int x = (int) Math.floor(body.getMinX() + .001);
        x <= (int) Math.floor(body.getMaxX() - .001);
        x++)
      for (int y = (int) Math.floor(body.getMinY() + .001);
          y <= (int) Math.floor(body.getMaxY() - .001);
          y++)
        for (int z = (int) Math.floor(body.getMinZ() + .001);
            z <= (int) Math.floor(body.getMaxZ() - .001);
            z++) {
          if (player.getWorld().getBlockAt(x, y, z).getType() == Material.COBWEB) return true;
        }
    return false;
  }

  private boolean nearbyWeb(Location at) {
    for (int x = -2; x <= 2; x++)
      for (int y = 0; y <= 1; y++)
        for (int z = -2; z <= 2; z++)
          if (at.getBlock().getRelative(x, y, z).getType() == Material.COBWEB) return true;
    return false;
  }

  private double damagedMendingArmor() {
    double worst = 0;
    for (ItemStack item : player().getInventory().getArmorContents()) {
      if (item != null
          && item.containsEnchantment(org.bukkit.enchantments.Enchantment.MENDING)
          && item.getItemMeta() instanceof Damageable damage
          && item.getType().getMaxDurability() > 0)
        worst = Math.max(worst, (double) damage.getDamage() / item.getType().getMaxDurability());
    }
    return worst;
  }

  private boolean escapeWeb(int tick) {
    org.bukkit.block.Block web = null;
    var body = player().getBoundingBox();
    for (int x = (int) Math.floor(body.getMinX() + .001);
        x <= (int) Math.floor(body.getMaxX() - .001);
        x++)
      for (int y = (int) Math.floor(body.getMinY() + .001);
          y <= (int) Math.floor(body.getMaxY() - .001);
          y++)
        for (int z = (int) Math.floor(body.getMinZ() + .001);
            z <= (int) Math.floor(body.getMaxZ() - .001);
            z++) {
          var candidate = player().getWorld().getBlockAt(x, y, z);
          if (candidate.getType() == Material.COBWEB) web = candidate;
        }
    if (web == null) {
      if (mining != null) {
        var current = player().getWorld().getBlockAt(mining.getX(), mining.getY(), mining.getZ());
        if (current.getType() == Material.COBWEB
            && current.getLocation().add(.5, .5, .5).distanceSquared(player().getEyeLocation())
                <= 9) return clearWeb(current, tick);
        abortMining();
      }
      return false;
    }
    if (water(tick)) {
      abortMining();
      return true;
    }
    return clearWeb(web, tick);
  }

  boolean clearWeb(org.bukkit.block.Block web, int tick) {
    if (web.getType() != Material.COBWEB) {
      abortMining();
      return false;
    }
    Vector direction =
        web.getLocation().add(.5, .5, .5).toVector().subtract(player().getEyeLocation().toVector());

    var first =
        direction.lengthSquared() < .0001
            ? null
            : CombatSight.obstruction(
                player(), direction.clone().normalize(), Math.min(3, direction.length()));
    if (first == null || first.getType() != Material.COBWEB) {
      abortMining();
      return true;
    }
    web = first;
    direction =
        web.getLocation().add(.5, .5, .5).toVector().subtract(player().getEyeLocation().toVector());
    int sword = find(i -> i.getType() == Material.SHEARS);
    if (sword < 0) sword = find(i -> i.getType().name().endsWith("_SWORD"));
    if (sword < 0) sword = find(i -> i.getType().name().endsWith("_AXE"));

    if (sword < 0) return true;
    int tool = select(sword);
    handTiming.equip(handIdentity(tool), tick);
    Location look = player().getEyeLocation().setDirection(direction);
    handle.setYRot(look.getYaw());
    handle.setXRot(look.getPitch());
    handle.setYHeadRot(handle.getYRot());
    BotLook.publish(handle);
    if (!handTiming.ready(tick)) {
      handle.zza = handle.xxa = 0;
      handle.setSprinting(false);
      return true;
    }
    BlockPos pos = new BlockPos(web.getX(), web.getY(), web.getZ());
    if (!pos.equals(mining)) {
      abortMining();
      mining = pos;
      miningSince = tick;
      miningStopSent = false;
      handle.gameMode.handleBlockBreakAction(
          pos,
          ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
          Direction.UP,
          handle.level().getMaxY(),
          0);
    } else if (!miningStopSent && tick - miningSince >= miningTicks(pos)) {
      handle.gameMode.handleBlockBreakAction(
          pos,
          ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
          Direction.UP,
          handle.level().getMaxY(),
          0);
      miningStopSent = true;
    } else if (miningStopSent && tick - miningSince > miningTicks(pos) + 40) {

      abortMining();
    }
    handle.swing(InteractionHand.MAIN_HAND);
    handle.zza = 0;
    handle.xxa = 0;
    handle.setSprinting(false);
    return true;
  }

  private void abortMining() {
    if (mining != null)
      handle.gameMode.handleBlockBreakAction(
          mining,
          ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
          Direction.UP,
          handle.level().getMaxY(),
          0);
    mining = null;
    miningStopSent = false;
  }

  private int miningTicks(BlockPos pos) {
    float progress =
        ((net.minecraft.server.level.ServerLevel) handle.level())
            .getBlockState(pos)
            .getDestroyProgress(handle, ((net.minecraft.server.level.ServerLevel) handle.level()), pos);
    return progress > 0 ? (int) Math.ceil(1 / progress) : 200;
  }

  private boolean selfPotion(int slot, Player target, int tick, double healthFraction) {
    if (player().getInventory().getItem(slot).getType() != Material.SPLASH_POTION) {
      retreat();
      prepare(slot, tick, this::nativeUse, () -> true);
      return true;
    }
    if (preparingSplash < 0) {
      preparingSplash = tick;
      slot = select(slot);
    }
    pendingPotion = slot;
    Vector escape = escapeDirection();
    splashDirection = escape;
    if (splashDirection == null) {
      splashDirection =
          player().getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
      if (splashDirection.lengthSquared() < .001) splashDirection = new Vector(1, 0, 0);
      splashDirection.normalize();
    }
    followSplash();

    if (!SplashPlan.turnComplete(tick - preparingSplash)) return false;
    if (!SplashPlan.shouldThrow(
        player().getLocation().distance(target.getLocation()),
        healthFraction,
        tick - preparingSplash,
        escape != null)) return false;
    abortMining();
    select(slot);

    handle.setXRot(escape == null ? 85 : 65);
    BotLook.publish(handle);
    nativeUse();
    splashUntil = tick + 8;
    preparingSplash = -1;
    pendingPotion = -1;
    return true;
  }

  private void followSplash() {
    Location look = player().getLocation().setDirection(splashDirection);
    BotLook.towards(handle, look.getYaw(), 65, 55);
    boolean safe = TacticalMovement.clearPath(player().getLocation(), splashDirection, .7);
    safe &=
        Math.abs(
                dev.insanmiy.practiceplugin.model.LookMotion.delta(handle.getYRot(), look.getYaw()))
            < 25;
    handle.zza = safe ? 1 : 0;
    handle.xxa = 0;
    handle.setSprinting(safe);
    BotLook.publish(handle);
  }

  private HealingPlan.State healingState(Player target, Difficulty difficulty, int tick) {
    Player self = player();
    double distance = self.getLocation().distance(target.getLocation());
    Vector toward = self.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
    double closing =
        toward.lengthSquared() < .001
            ? .2
            : Math.max(.12, target.getVelocity().clone().setY(0).dot(toward.normalize()));
    double exposed = Math.max(0, 35 - Math.max(0, distance - 3.5) / closing);
    boolean covered = !self.hasLineOfSight(target);
    return new HealingPlan.State(
        self.getHealth(),
        self.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
        self.getAbsorptionAmount(),
        self.getFoodLevel(),
        self.getSaturation(),
        distance,
        pressure.forecast(tick, covered ? exposed * .4 : exposed),
        difficulty.healThreshold(),
        options.useHealing()
            && tick >= nextHeal
            && potion(PotionEffectType.INSTANT_HEALTH, true) >= 0,
        tick >= nextFood && find(i -> golden(i.getType())) >= 0,
        tick >= nextFood && find(i -> i.getType().isEdible() && !golden(i.getType())) >= 0,
        self.hasPotionEffect(PotionEffectType.REGENERATION),
        naturalRegeneration
            && Boolean.TRUE.equals(self.getWorld().getGameRuleValue(GameRule.NATURAL_REGENERATION)),
        (self.getHealth()
                    < self.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * .9
                || self.getFoodLevel() < 20)
            && escapeDirection() != null,
        covered,
        seekingSpaceSince < 0 ? 0 : tick - seekingSpaceSince);
  }

  private boolean heal(Player target, int tick, double fraction, HealingPlan.State state) {
    HealingPlan.Action action = HealingPlan.choose(state);
    if (action == HealingPlan.Action.RETREAT) {
      if (seekingSpaceSince < 0) seekingSpaceSince = tick;
      retreat();
      return true;
    }
    if (action == HealingPlan.Action.NONE) {
      seekingSpaceSince = -1;

      if (tick >= nextHeal && fraction <= .5 && state.distance() >= 5) {
        int drink = potion(PotionEffectType.INSTANT_HEALTH, false);
        if (drink >= 0 && player().getInventory().getItem(drink).getType() == Material.POTION) {
          selfPotion(drink, target, tick, fraction);
          nextHeal = tick + 40;
          return true;
        }
      }
      return false;
    }
    seekingSpaceSince = -1;
    abortMining();
    repair.stop(tick);
    if (action == HealingPlan.Action.SPLASH) {
      pendingHeal = true;
      if (selfPotion(
          potion(PotionEffectType.INSTANT_HEALTH, true),
          target,
          tick,
          HealingPlan.urgent(state) ? .2 : fraction)) {
        nextHeal = tick + 12;
        pendingHeal = false;
      }
      return true;
    }
    int food =
        action == HealingPlan.Action.GOLDEN ? find(i -> golden(i.getType())) : ordinaryFood();
    if (food < 0) return false;
    retreat();
    prepare(
        food,
        tick,
        () -> {
          nativeUse();
          nextFood = currentTick + 10;
        },
        () -> true);
    return true;
  }

  private int ordinaryFood() {
    int food = -1;
    double best = -Double.MAX_VALUE;
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = player().getInventory().getItem(slot);
      if (item == null || !item.getType().isEdible() || golden(item.getType())) continue;
      var nativeItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
      var nutrition = nativeItem.get(net.minecraft.core.component.DataComponents.FOOD);
      if (nutrition == null) continue;
      double score = nutrition.nutrition() + nutrition.saturation();
      if (Set.of(
              Material.ROTTEN_FLESH,
              Material.PUFFERFISH,
              Material.SPIDER_EYE,
              Material.POISONOUS_POTATO,
              Material.CHICKEN,
              Material.SUSPICIOUS_STEW)
          .contains(item.getType())) score -= 100;
      if (score > best) {
        best = score;
        food = slot;
      }
    }
    return food;
  }

  private static boolean golden(Material type) {
    return type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE;
  }

  private int potion(PotionEffectType effect, boolean splashOnly) {
    return find(
        item -> {
          if (item == null || item.getType().isAir()) return false;
          if (item.getType() != Material.POTION && item.getType() != Material.SPLASH_POTION)
            return false;
          if (splashOnly && item.getType() != Material.SPLASH_POTION) return false;
          if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
          if (meta.getBasePotionType() != null
              && meta.getBasePotionType().getPotionEffects().stream()
                  .anyMatch(e -> e.getType().equals(effect))) return true;
          return meta.getCustomEffects().stream().anyMatch(e -> e.getType().equals(effect));
        });
  }

  private int find(Predicate<ItemStack> predicate) {
    for (int i = 0; i < 36; i++) {
      ItemStack item = player().getInventory().getItem(i);
      if (item != null && !item.getType().isAir() && predicate.test(item)) return i;
    }
    return -1;
  }

  private int select(int slot) {
    handle.stopUsingItem();
    if (slot >= 9) {
      int hotbar = 7;

      for (int i = 8; i >= 0; i--) {
        ItemStack item = player().getInventory().getItem(i);
        if (item == null
            || item.getType().isAir()
            || !item.getType().name().endsWith("_SWORD")
                && !item.getType().name().endsWith("_AXE")) {
          hotbar = i;
          break;
        }
      }
      ItemStack displaced = player().getInventory().getItem(hotbar);
      player().getInventory().setItem(hotbar, player().getInventory().getItem(slot));
      player().getInventory().setItem(slot, displaced);
      slot = hotbar;
    }
    if (player().getInventory().getHeldItemSlot() != slot) handle.resetAttackStrengthTicker();
    player().getInventory().setHeldItemSlot(slot);
    NmsBot.synchronizeEquipment(player());
    return slot;
  }

  void combatSelected() {
    handTiming.reset();
  }

  private String handIdentity(int slot) {
    ItemStack item = player().getInventory().getItem(slot);
    return slot
        + ":"
        + item.getType()
        + ":"
        + (item.getItemMeta() instanceof PotionMeta p ? p.getBasePotionType() : "");
  }

  private void prepare(
      int slot, int tick, Runnable action, java.util.function.BooleanSupplier valid) {
    int selected = select(slot);
    handTiming.equip(handIdentity(selected), tick);
    BotLook.publish(handle);
    if (handTiming.ready(tick)) {
      if (valid.getAsBoolean()) {
        action.run();
        handTiming.used(tick);
      }
      return;
    }
    prepared =
        new PreparedAction(
            selected, player().getInventory().getItemInMainHand().clone(), action, valid);
    handle.zza = handle.xxa = 0;
    handle.setSprinting(false);
  }

  private void lookAt(Location location) {
    Location look =
        player()
            .getEyeLocation()
            .setDirection(location.toVector().subtract(player().getEyeLocation().toVector()));
    handle.setYRot(look.getYaw());
    handle.setXRot(look.getPitch());
    BotLook.publish(handle);
  }

  private void nativeUse() {
    handle.gameMode.useItem(
        handle, ((net.minecraft.server.level.ServerLevel) handle.level()), handle.getMainHandItem(), InteractionHand.MAIN_HAND);
    if (!handle.isUsingItem()) handle.swing(InteractionHand.MAIN_HAND);
    NmsBot.synchronizeEquipment(player());
  }

  private void retreat() {
    if (opponent != null) {
      Vector away = escapeDirection();
      if (away != null) {
        float yaw = player().getLocation().setDirection(away).getYaw();
        BotLook.towards(handle, yaw, 15, 40);
        handle.zza =
            Math.abs(dev.insanmiy.practiceplugin.model.LookMotion.delta(handle.getYRot(), yaw)) < 30
                ? .65f
                : 0;
      } else handle.zza = 0;
    } else handle.zza = -.65f;
    handle.xxa = 0;
    handle.setSprinting(false);
  }

  private Vector escapeDirection() {
    if (opponent == null) return null;
    Location self = player().getLocation(), target = opponent.getLocation();
    if (escapeOrigin == null
        || !escapeOrigin.getWorld().equals(self.getWorld())
        || !escapeTarget.getWorld().equals(target.getWorld())
        || currentTick >= nextEscapeCheck
        || escapeOrigin.distanceSquared(self) > 1
        || escapeTarget.distanceSquared(target) > 2
        || cachedEscape != null && !TacticalMovement.clearPath(self, cachedEscape, .7)) {
      cachedEscape = TacticalMovement.retreatDirection(self, target);
      escapeOrigin = self;
      escapeTarget = target;
      nextEscapeCheck = currentTick + 5;
    }
    return cachedEscape == null ? null : cachedEscape.clone();
  }

  private void aim(Player target, double speed, double error) {
    double distance = player().getEyeLocation().distance(target.getEyeLocation());
    Vector predicted = target.getVelocity().clone().multiply(Math.min(8, distance / speed));
    Vector direction =
        target
            .getEyeLocation()
            .toVector()
            .add(predicted)
            .subtract(player().getEyeLocation().toVector());
    direction.setY(direction.getY() + .025 * Math.pow(distance / speed, 2));
    Location look = player().getEyeLocation().setDirection(direction);
    handle.setYRot(look.getYaw() + (float) ((Math.random() * 2 - 1) * error));
    handle.setXRot(look.getPitch());
    handle.setYHeadRot(handle.getYRot());
  }

  private boolean potUp(Player target, Difficulty difficulty, int tick) {
    if (tick < nextPotion) return false;
    Player self = player();
    for (PotionEffectType effect :
        List.of(
            PotionEffectType.SPEED,
            PotionEffectType.STRENGTH,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.RESISTANCE)) {
      var active = self.getPotionEffect(effect);
      if (active != null && active.getDuration() > 60) continue;
      int potSlot = potion(effect, false);
      if (potSlot >= 0) {
        ItemStack item = self.getInventory().getItem(potSlot);
        if (item == null) continue;
        if (item.getType() == Material.SPLASH_POTION) {
          int selected = select(potSlot);
          handle.setXRot(85f);
          BotLook.publish(handle);
          nativeUse();
          handTiming.used(tick);
          nextPotion = tick + 6;
          return true;
        } else if (item.getType() == Material.POTION) {
          retreat();
          prepare(
              potSlot,
              tick,
              () -> {
                nativeUse();
                nextPotion = currentTick + 15;
              },
              () -> true);
          return true;
        }
      }
    }
    return false;
  }

  void manageOffhand(int tick, Difficulty difficulty, double healthFraction) {
    if (tick < nextTotem) return;
    Player self = player();
    ItemStack offhand = self.getInventory().getItemInOffHand();
    Material offhandType = offhand.getType();
    int totemSlot = find(i -> i.getType() == Material.TOTEM_OF_UNDYING);
    int shieldSlot = find(i -> i.getType() == Material.SHIELD);
    double currentHealth = self.getHealth();

    double dangerThreshold = Math.max(0.40, difficulty.healThreshold());
    boolean inDanger = currentHealth <= (dangerThreshold * 20.0) || healthFraction <= dangerThreshold;

    if (totemSlot >= 0) {
      if (offhandType == Material.TOTEM_OF_UNDYING) {
        return;
      }
      if (offhandType.isAir() || inDanger) {
        ItemStack totemItem = self.getInventory().getItem(totemSlot);
        ItemStack displaced = offhandType.isAir() ? null : offhand.clone();
        self.getInventory().setItem(totemSlot, displaced);
        self.getInventory().setItemInOffHand(totemItem);
        NmsBot.synchronizeEquipment(self);
        nextTotem = tick + Math.max(1, difficulty.decisionTicks());
        return;
      }
    }

    if (!inDanger
        && currentHealth >= 16.0
        && healthFraction >= 0.8
        && offhandType != Material.SHIELD
        && shieldSlot >= 0) {
      ItemStack shieldItem = self.getInventory().getItem(shieldSlot);
      ItemStack displaced = offhandType.isAir() ? null : offhand.clone();
      self.getInventory().setItem(shieldSlot, displaced);
      self.getInventory().setItemInOffHand(shieldItem);
      NmsBot.synchronizeEquipment(self);
      nextTotem = tick + Math.max(3, difficulty.decisionTicks() * 2);
      return;
    }

    if (offhandType.isAir() && shieldSlot >= 0) {
      ItemStack shieldItem = self.getInventory().getItem(shieldSlot);
      self.getInventory().setItem(shieldSlot, null);
      self.getInventory().setItemInOffHand(shieldItem);
      NmsBot.synchronizeEquipment(self);
      nextTotem = tick + Math.max(3, difficulty.decisionTicks() * 2);
    }
  }

  void checkTotemEmergency(int tick, Difficulty difficulty) {
    Player self = player();
    ItemStack offhand = self.getInventory().getItemInOffHand();
    if (offhand.getType() == Material.TOTEM_OF_UNDYING) return;
    int totemSlot = find(i -> i.getType() == Material.TOTEM_OF_UNDYING);
    if (totemSlot < 0) return;
    double currentHealth = self.getHealth();
    double maxHealth = self.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
    if (currentHealth <= 10.0 || (currentHealth / maxHealth) <= 0.5) {
      ItemStack totemItem = self.getInventory().getItem(totemSlot);
      ItemStack displaced = offhand.getType().isAir() ? null : offhand.clone();
      self.getInventory().setItem(totemSlot, displaced);
      self.getInventory().setItemInOffHand(totemItem);
      NmsBot.synchronizeEquipment(self);
      nextTotem = tick + 2;
    }
  }
}
