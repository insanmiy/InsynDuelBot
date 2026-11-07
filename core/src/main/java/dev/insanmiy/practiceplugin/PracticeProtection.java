package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.bot.*;
import dev.insanmiy.practiceplugin.config.Kit;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

final class PracticeProtection implements Listener {
  private final PracticePlugin plugin;

  PracticeProtection(PracticePlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin);
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    if (plugin.isBot(e.getPlayer())) {
      e.joinMessage(null);
      return;
    }
    if (plugin.recovery() != null && plugin.sessionFor(e.getPlayer()) == null)
      plugin.getServer()
          .getScheduler()
          .runTask(
              plugin,
              () -> {
                if (plugin.sessionFor(e.getPlayer()) == null) plugin.recovery().restore(e.getPlayer());
              });
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (plugin.isBot(e.getPlayer())) {
      e.quitMessage(null);
      return;
    }
    UUID uid = e.getPlayer().getUniqueId();
    plugin.lastSelections().remove(uid);
    if (plugin.menu() != null) plugin.menu().cleanup(uid);
    if (plugin.participant(e.getPlayer())) plugin.stop(session, plugin.messages() != null ? plugin.messages().get("session.player-disconnected") : "Player Disconnected.");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void healingPressure(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player p)) return;
    PracticeSession session = plugin.sessionFor(p);
    if (session == null || !session.fighting()) return;
    BotPlatform b = session.findBot(p);
    if (b != null) b.onIncomingDamage(event.getFinalDamage());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void resurrect(EntityResurrectEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    PracticeSession session = plugin.sessionFor(p);
    if (session == null || !session.fighting()) return;
    BotPlatform b = session.findBot(p);
    if (b != null) b.onTotemPopped();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void damage(EntityDamageEvent e) {
    Entity source = e instanceof EntityDamageByEntityEvent by ? by.getDamager() : null;
    if (source instanceof Projectile projectile
        && projectile.getShooter() instanceof Entity shooter) source = shooter;
    PracticeSession victimSession = plugin.sessionFor(e.getEntity());
    PracticeSession attackSession = plugin.sessionFor(source);
    if (e instanceof EntityDamageByEntityEvent && attackSession != victimSession) {
      e.setCancelled(true);
      return;
    }
    if (victimSession == null) return;
    if (source instanceof Player attacker && e.getEntity() instanceof Player target) {
      if (victimSession.isBotPlayer(attacker) && victimSession.isBotPlayer(target)) {
        e.setCancelled(true);
        return;
      }
    }
    if (!victimSession.fighting() || victimSession.endingRound) {
      e.setCancelled(true);
      return;
    }
    if (!(e.getEntity() instanceof Player damaged)) return;
    double amount = Math.clamp(e.getFinalDamage(), 0.0, damaged.getHealth());
    if (e.getFinalDamage() >= damaged.getHealth()) victimSession.finishingBlow = e;
    if (damaged.equals(victimSession.owner)) {
      victimSession.round.hurt(amount);
      victimSession.total.hurt(amount);
    } else {
      victimSession.round.hit(amount, victimSession.tick);
      victimSession.total.hit(amount, victimSession.tick);
      if (amount > 0 && source != null && source.equals(victimSession.owner)) {
        BotPlatform b = victimSession.findBot(damaged);
        if (b != null) b.onOpponentHit();
        else if (victimSession.bot != null) victimSession.bot.onOpponentHit();
      }
    }

  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void reportDamage(EntityDamageByEntityEvent e) {
    PracticeSession session = plugin.sessionFor(e.getEntity());
    if (session != null
        && e.getDamager() instanceof Player attacker
        && session.participant(attacker)
        && e.getEntity() instanceof Player target
        && plugin.participant(target)) session.damageDiagnostics.report(e, session);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void regain(EntityRegainHealthEvent e) {
    PracticeSession session = plugin.sessionFor(e.getEntity());
    if (e.getEntity() instanceof Player p
        && plugin.participant(p)
        && (!session.fighting()
            || !session.kit.regeneration()
                && e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void swing(PlayerAnimationEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null
        && session.fighting()
        && e.getPlayer().equals(session.owner)
        && e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
      session.round.swing();
      session.total.swing();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void consume(PlayerItemConsumeEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null
        && session.fighting()
        && e.getPlayer().equals(session.owner)
        && e.getItem().getType() == Material.GOLDEN_APPLE) {
      session.round.apple();
      session.total.apple();
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void move(PlayerMoveEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null
        && !session.fighting()
        && !session.internalTeleport
        && !(e instanceof PlayerTeleportEvent)) {
      if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ()) {
        Location to = e.getFrom().clone();
        to.setYaw(e.getTo().getYaw());
        to.setPitch(e.getTo().getPitch());
        e.setTo(to);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void teleport(PlayerTeleportEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (plugin.participant(e.getPlayer()) && !session.internalTeleport && !session.closing) {
      if (plugin.isBot(e.getPlayer())) {
        BotPlatform b = session.findBot(e.getPlayer());
        if (b != null && b.isTeleporting()) return;
      }
      if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
          || e.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
        if (session.fighting() && e.getPlayer().equals(session.owner)) {
          for (BotPlatform b : session.aliveBots()) {
            if (b != null) b.onOpponentPearled(session.owner);
          }
        }
        return;
      }
      plugin.getServer()
          .getScheduler()
          .runTask(
              plugin,
              () -> {
                if (plugin.sessions().get(session.owner.getUniqueId()) == session
                    && e.getPlayer().equals(session.owner))
                  session.followTeleport(session.owner.getLocation());
              });
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void respawn(PlayerRespawnEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null) {
      e.setRespawnLocation(session.arena.playerSpawn());
    } else {
      plugin.getServer()
          .getScheduler()
          .runTask(
              plugin,
              () -> {
                if (plugin.sessionFor(e.getPlayer()) == null && plugin.recovery().pending(e.getPlayer()))
                  plugin.recovery().restore(e.getPlayer());
              });
    }
  }

  @EventHandler
  public void gameMode(PlayerGameModeChangeEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null && !plugin.isBot(e.getPlayer())
        && !session.internalGameMode && !session.closing) e.setCancelled(true);
  }

  @EventHandler
  public void drop(PlayerDropItemEvent e) {
    if (plugin.participant(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler
  public void pickup(EntityPickupItemEvent e) {
    if (e.getEntity() instanceof Player p && plugin.participant(p)) e.setCancelled(true);
  }

  private boolean practiceProjectile(Projectile p) {
    return p.getPersistentDataContainer().has(plugin.gearKey());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void launch(ProjectileLaunchEvent e) {
    if (!(e.getEntity().getShooter() instanceof Player shooter) || !plugin.participant(shooter)) return;
    PracticeSession session = plugin.sessionFor(shooter);
    session.projectiles.removeIf(p -> !p.isValid());
    if (!session.fighting() || session.projectiles.size() >= 128) {
      e.setCancelled(true);
      return;
    }
    e.getEntity()
        .getPersistentDataContainer()
        .set(plugin.gearKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    e.getEntity().setPersistent(false);
    if (e.getEntity() instanceof AbstractArrow arrow)
      arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    plugin.projectiles().add(e.getEntity());
    session.projectiles.add(e.getEntity());
    session.round.utility();
    session.total.utility();
  }

  @SuppressWarnings("UnstableApiUsage")
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void projectileHit(ProjectileHitEvent e) {
    if (!practiceProjectile(e.getEntity())) return;
    PracticeSession session = plugin.sessionFor(e.getEntity());

    if (session == null
        || !session.fighting()
        || e.getEntity() instanceof AbstractArrow && e.getHitBlock() != null
        || e.getHitEntity() instanceof Player p && !session.participant(p)) {
      e.setCancelled(true);
      e.getEntity().remove();
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void combustion(EntityCombustByEntityEvent e) {
    Entity source = e.getCombuster();
    if (source instanceof Projectile projectile
        && projectile.getShooter() instanceof Entity shooter) source = shooter;
    PracticeSession attacker = plugin.sessionFor(source), victim = plugin.sessionFor(e.getEntity());
    if ((attacker != null || victim != null) && (attacker != victim || !attacker.fighting()))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void splash(PotionSplashEvent e) {
    PracticeSession session = plugin.sessionFor(e.getEntity());
    boolean ours = practiceProjectile(e.getEntity());
    if (ours && (session == null || !session.fighting())) {
      e.setCancelled(true);
      return;
    }
    for (LivingEntity entity : e.getAffectedEntities()) {
      boolean fighter = entity instanceof Player p && plugin.participant(p);
      if (ours ? plugin.sessionFor(entity) != session : fighter) e.setIntensity(entity, 0);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void lingering(LingeringPotionSplashEvent event) {
    if (!practiceProjectile(event.getEntity())) return;
    PracticeSession session = plugin.sessionFor(event.getEntity());
    if (session == null || !session.fighting()) {
      event.setCancelled(true);
      return;
    }
    plugin.clouds().register(session.owner.getUniqueId(), event.getAreaEffectCloud());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void cloudApply(AreaEffectCloudApplyEvent event) {
    PracticeSession session = plugin.sessionFor(event.getEntity());
    plugin.clouds().filterTargets(event, session != null && session.fighting(), entity -> {
      PracticeSession target = plugin.sessionFor(entity);
      return target == null ? null : target.owner.getUniqueId();
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void experienceBottle(ExpBottleEvent e) {
    PracticeSession session = plugin.sessionFor(e.getEntity());
    if (!practiceProjectile(e.getEntity())) return;
    int xp = e.getExperience();
    e.setExperience(0);
    if (session != null
        && session.fighting()
        && e.getEntity().getShooter() instanceof Player p
        && plugin.participant(p)) {
      long before = MendingExperience.equippedDamage(p);
      MendingExperience.award(p, xp);
      BotPlatform b = session.findBot(p);
      if (b != null)
        b.onRepairImpact(MendingExperience.equippedDamage(p) < before);
    }
  }

  private static final Set<Material> DRINKABLES =
      Set.of(
          Material.POTION,
          Material.HONEY_BOTTLE,
          Material.MUSHROOM_STEW,
          Material.RABBIT_STEW,
          Material.BEETROOT_SOUP,
          Material.SUSPICIOUS_STEW);

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void potionBottle(PlayerItemConsumeEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null && DRINKABLES.contains(e.getItem().getType())) {
      plugin.getServer()
          .getScheduler()
          .runTask(
              plugin,
              () -> {
                if (plugin.sessions().get(session.owner.getUniqueId()) != session
                    || !plugin.participant(e.getPlayer())) return;
                var inventory = e.getPlayer().getInventory();
                for (int slot = 0; slot < 36; slot++) {
                  var remainder = inventory.getItem(slot);
                  if (remainder != null
                      && !remainder.hasItemMeta()
                      && (remainder.getType() == Material.BOWL
                          || remainder.getType() == Material.GLASS_BOTTLE))
                    inventory.setItem(slot, null);
                }
              });
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void pourWater(PlayerBucketEmptyEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())) {
      e.setCancelled(true);
      return;
    }
    if (!plugin.participant(e.getPlayer())) return;
    if (!session.fighting()
        || (e.getBucket() != Material.WATER_BUCKET && e.getBucket() != Material.LAVA_BUCKET)) {
      e.setCancelled(true);
      return;
    }
    tagBucketResult(e);
    try {
      Material fluid = e.getBucket() == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
      if (!plugin.blocks().recordFluid(e.getBlock(), null, session.owner.getUniqueId(), fluid)){
        e.setCancelled(true);
        plugin.messages().send(e.getPlayer(), "session.water-journal-failed");
      }
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      plugin.getLogger().severe("Water Journal Failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fillWater(PlayerBucketFillEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())
        || plugin.blocks().owns(e.getBlock())
            && (session == null
                || !session.owner.getUniqueId().equals(plugin.blocks().owner(e.getBlock())))){
      e.setCancelled(true);
      return;
    }
    if (!plugin.participant(e.getPlayer())){
      return;
    }
    if (!session.fighting()
        || e.getItemStack() == null
        || (e.getItemStack().getType() != Material.WATER_BUCKET
            && e.getItemStack().getType() != Material.LAVA_BUCKET)){
      e.setCancelled(true);
    }
    else {
      tagBucketResult(e);
    }
  }

  private void tagBucketResult(PlayerBucketEvent e) {
    if (e.getItemStack() == null) return;
    var item = e.getItemStack().clone();
    dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
        meta ->
            meta.getPersistentDataContainer()
                .set(plugin.gearKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1));
    e.setItemStack(item);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void waterFlow(BlockFromToEvent e) {
    if (!plugin.blocks().owns(e.getBlock())){
      return;
    }
    PracticeSession source = plugin.sessions().get(plugin.blocks().owner(e.getBlock()));
    if (protectedFrom(source, e.getToBlock().getLocation())){
      e.setCancelled(true);
      return;
    }
    try {
      if (!plugin.blocks().recordFluid(e.getToBlock(), e.getBlock(), plugin.blocks().owner(e.getBlock()))){
        e.setCancelled(true);
      }
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      plugin.getLogger().severe("Water Flow Journal Failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void waterFreeze(BlockFormEvent e) {
    if (plugin.blocks().owns(e.getBlock())){
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void practiceIgnition(BlockIgniteEvent e) {
    var source = e.getIgnitingBlock();

    if (source != null && (plugin.blocks().owns(source) || plugin.blocks().fire(source))){
      e.setCancelled(true);
      return;
    }
    PracticeSession session = e.getPlayer() == null ? null : plugin.sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())){
      e.setCancelled(true);
      return;
    }
    if (session == null){
      return;
    }
    if (!session.fighting()){
      e.setCancelled(true);
      return;
    }
    try {
      if (!plugin.blocks().recordFire(e.getBlock(), session.owner.getUniqueId())){
        e.setCancelled(true);
      }
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      plugin.getLogger().severe("Fire Journal Failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void practiceFireSpread(BlockSpreadEvent e) {
    if (plugin.blocks().fire(e.getSource())){
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void loadedEntities(org.bukkit.event.world.EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities())
      if (entity instanceof Projectile p && practiceProjectile(p) && !plugin.projectiles().contains(p))
        p.remove();
      else if (entity instanceof AreaEffectCloud cloud && plugin.clouds().marked(cloud) && plugin.clouds().owner(cloud) == null)
        cloud.remove();
  }

  @EventHandler
  public void extendPiston(BlockPistonExtendEvent e) {
    if (e.getBlocks().stream().anyMatch(plugin.blocks()::owns)){
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void retractPiston(BlockPistonRetractEvent e) {
    if (e.getBlocks().stream().anyMatch(plugin.blocks()::owns)){
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void burnWeb(BlockBurnEvent e) {
    if (plugin.blocks().owns(e.getBlock())
        || (e.getIgnitingBlock() != null && plugin.blocks().fire(e.getIgnitingBlock()))
        || protectedFrom(null, e.getBlock().getLocation())){
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())
        || plugin.blocks().owns(e.getBlock())
            && (session == null || !session.owner.getUniqueId().equals(plugin.blocks().owner(e.getBlock())))){
      e.setCancelled(true);
      return;
    }
    if (dev.insanmiy.practiceplugin.model.BlockAccess.shouldCancel(
        plugin.participant(e.getPlayer()), session != null && session.fighting())){
      e.setCancelled(true);
      return;
    }
    if (plugin.participant(e.getPlayer())) {

      e.setDropItems(false);
      e.setExpToDrop(0);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void placeBlock(BlockPlaceEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())){
      e.setCancelled(true);
      return;
    }
    if (dev.insanmiy.practiceplugin.model.BlockAccess.shouldCancel(
        plugin.participant(e.getPlayer()), session != null && session.fighting())){
      e.setCancelled(true);
      return;
    }
    if (plugin.participant(e.getPlayer()) && session.fighting()){
      try {
        if (plugin.blocks().record(e.getBlock(), e.getBlockReplacedState(), session.owner.getUniqueId())){
          return;
        }
      } catch (java.io.IOException failure) {
        plugin.getLogger().severe("Block Placement Cancelled: Journal Could Not Be Saved.");
      }
      e.setCancelled(true);
      plugin.messages().send(e.getPlayer(), "session.block-journal-failed");
    }
  }

  private boolean inArena(Location l) {
    return plugin.sessions().values().stream().anyMatch(session -> session.arena.contains(l));
  }

  private boolean protectedFrom(PracticeSession actor, Location location) {
    return plugin.sessions().values().stream().anyMatch(s -> s != actor && s.arena.contains(location));
  }

  @EventHandler
  public void explode(EntityExplodeEvent e) {
    e.blockList().removeIf(b -> inArena(b.getLocation()) || plugin.blocks().owns(b));
  }

  @EventHandler
  public void explodeBlock(BlockExplodeEvent e) {
    e.blockList().removeIf(b -> inArena(b.getLocation()) || plugin.blocks().owns(b));
  }

  @SuppressWarnings("UnstableApiUsage")
  @EventHandler(priority = EventPriority.HIGH)
  public void interact(PlayerInteractEvent e) {
    Player player = e.getPlayer();
    PracticeSession session = plugin.sessionFor(player);
    if (session == null || !plugin.participant(player)) return;

    if (Kit.isVersionAtLeast1_21()
        && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
        && e.getItem() != null
        && e.getItem().getType() == Material.WIND_CHARGE) {
      if (session.fighting()) {
        if (!player.hasCooldown(Material.WIND_CHARGE)) {
          try {
            WindCharge charge = BotAdapters.getAdapter().launchWindCharge(player);
            if (charge != null) {
              player.setCooldown(Material.WIND_CHARGE, 10);
              try {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 0.5f, 1.0f);
              } catch (Exception | LinkageError ignored) {}
              if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.swingOffHand(); else player.swingMainHand();
              charge
                  .getPersistentDataContainer()
                  .set(plugin.gearKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
              charge.setPersistent(false);
              plugin.projectiles().add(charge);
              session.projectiles.add(charge);
              session.round.utility();
              session.total.utility();
              if (player.getGameMode() != GameMode.CREATIVE) { e.getItem().subtract(1); player.updateInventory(); }
            }
          } catch (Exception ex) {
            plugin.getLogger().fine("Wind charge launch error: " + ex.getMessage());
          }
        }
      }
      e.setUseItemInHand(Event.Result.DENY);
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setCancelled(true);
      return;
    }

    if (e.getClickedBlock() != null && protectedFrom(session, e.getClickedBlock().getLocation())) {
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setUseItemInHand(Event.Result.DENY);
      return;
    }
    if (!session.fighting()) {
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setUseItemInHand(Event.Result.DENY);
    }
  }

  @EventHandler
  public void interactEntity(PlayerInteractEntityEvent e) {
    if (plugin.participant(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler
  public void inventory(InventoryClickEvent e) {
    PracticeSession session = plugin.sessionFor(e.getWhoClicked());
    if (e.getWhoClicked() instanceof Player p
        && plugin.participant(p)
        && (session != null && session.closing
            || e.getView().getTopInventory().getType() != InventoryType.CRAFTING
            || e.getRawSlot() < 5
            || e.getAction() == InventoryAction.DROP_ALL_SLOT
            || e.getAction() == InventoryAction.DROP_ONE_SLOT
            || e.getAction() == InventoryAction.DROP_ALL_CURSOR
            || e.getAction() == InventoryAction.DROP_ONE_CURSOR)) e.setCancelled(true);
  }

  @EventHandler
  public void drag(InventoryDragEvent e) {
    if (e.getWhoClicked() instanceof Player p && plugin.participant(p)) {
      PracticeSession session = plugin.sessionFor(p);
      if (session != null
          && (session.closing
              || e.getView().getTopInventory().getType() != InventoryType.CRAFTING
              || e.getRawSlots().stream().anyMatch(slot -> slot < 5))) {
        e.setCancelled(true);
      }
    }
  }

  @EventHandler
  public void open(InventoryOpenEvent e) {
    if (e.getPlayer() instanceof Player p && plugin.participant(p)) e.setCancelled(true);
  }

  @EventHandler
  public void target(EntityTargetLivingEntityEvent e) {
    if (e.getTarget() instanceof Player p && plugin.participant(p)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void death(PlayerDeathEvent e) {
    PracticeSession session = plugin.sessionFor(e.getEntity());
    if (plugin.participant(e.getEntity())) {
      e.getDrops().clear();
      e.setDroppedExp(0);
      e.deathMessage(null);
      if (session != null && session.fighting() && !session.endingRound) {
        session.captureLayout();
        boolean isOwner = e.getEntity().equals(session.owner);

        boolean revived = ServerFeatures.revive(e);
        e.setKeepInventory(true);
        e.setKeepLevel(true);

        if (isOwner) {
          session.endingRound = true;
          plugin.messages().send(session.owner, "combat.lethal-hit", "%target%", "You");
          plugin.getServer()
              .getScheduler()
              .runTask(
                  plugin,
                  () -> {
                    if (plugin.sessions().get(session.owner.getUniqueId()) != session) return;
                    if (!revived) {
                      session.owner.spigot().respawn();
                    }
                    session.endingRound = false;
                    session.win(false);
                  });
        } else {
          BotPlatform deadBot = session.findBot((Player) e.getEntity());
          session.eliminateBot(deadBot);
          boolean allDead = session.aliveBotCount() <= 0;
          if (allDead) {
            session.endingRound = true;
            plugin.messages().send(session.owner, "combat.lethal-hit", "%target%", "Bot");
            plugin.getServer()
                .getScheduler()
                .runTask(
                    plugin,
                    () -> {
                      if (plugin.sessions().get(session.owner.getUniqueId()) != session) return;
                      session.endingRound = false;
                      session.win(true);
                    });
          } else {
            plugin.messages().send(session.owner, "combat.bot-eliminated",
                "%remaining%", String.valueOf(session.aliveBotCount()));
            plugin.getServer()
                .getScheduler()
                .runTask(
                    plugin,
                    () -> {
                      if (plugin.sessions().get(session.owner.getUniqueId()) != session) return;
                      session.removeEliminatedBot(deadBot);
                    });
          }
        }
        return;
      }
      plugin.getServer()
          .getScheduler()
          .runTask(plugin, () -> plugin.stop(session, "Unexpected Player Death; Practice Aborted."));
    }
  }}
