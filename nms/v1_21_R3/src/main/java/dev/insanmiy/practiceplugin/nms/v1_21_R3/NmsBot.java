package dev.insanmiy.practiceplugin.nms.v1_21_R3;

import dev.insanmiy.practiceplugin.Attributes;
import dev.insanmiy.practiceplugin.bot.*;

import com.mojang.authlib.GameProfile;
import dev.insanmiy.practiceplugin.config.*;
import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.model.AttackGate;
import dev.insanmiy.practiceplugin.model.AttackSequence;
import dev.insanmiy.practiceplugin.model.CriticalWindow;
import dev.insanmiy.practiceplugin.model.SwingClock;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("resource")
public final class NmsBot implements BotPlatform {
  private final MinecraftServer server;
  private final ServerPlayer handle;
  private final DummyConnection connection;
  private final UtilityBrain utility;
  private dev.insanmiy.practiceplugin.model.BotOptions options =
      dev.insanmiy.practiceplugin.model.BotOptions.defaults();
  private final SwingClock swingClock = new SwingClock();
  private final CriticalWindow critical = new CriticalWindow();
  private int axeUntil, movementEpoch, previousDecisionTick;
  private Difficulty currentDifficulty;

  private record Observation(Location location, boolean blocking) {}

  // Track past target positions
  private final ArrayDeque<Observation> history = new ArrayDeque<>();
  private final Random random = new Random();
  private boolean closed;
  private boolean teleporting;
  // Combat timers and strafe state
  private int ticks, strafe = 1, shieldUntil, resetSprintUntil, stuck, waterTicks;
  private Location previous;
  private Vec3 pendingVelocity;
  private Integer pendingTeleport;
  private TacticalPathfinder.Path currentPath;
  private int waypointIndex;
  private int lastPathTick;
  private Location lastPathTarget;

  public NmsBot(Player owner, Location spawn, String name, Consumer<UUID> register) {
    server = ((CraftServer) Bukkit.getServer()).getServer();
    GameProfile profile = new GameProfile(UUID.randomUUID(), name);
    // Copy owner skin and profile
    profile
        .getProperties()
        .putAll(((CraftPlayer) owner).getHandle().getGameProfile().getProperties());
    handle =
        new ServerPlayer(
            server,
            ((CraftWorld) spawn.getWorld()).getHandle(),
            profile,
            ClientInformation.createDefault());
    utility = new UtilityBrain(handle);
    register.accept(profile.getId());
    handle.getBukkitEntity().setPersistent(false);
    handle.absMoveTo(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());
    connection = new DummyConnection();
    CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
    try {
      server.getPlayerList().placeNewPlayer(connection, handle, cookie);
      // Capture velocity and teleport packets
      handle.connection =
          new ServerGamePacketListenerImpl(server, connection, handle, cookie) {
            @Override
            public void send(@NotNull Packet<?> packet) {

              if (packet instanceof ClientboundSetEntityMotionPacket motion
                  && motion.getId() == handle.getId())
                pendingVelocity = new Vec3(motion.getXa(), motion.getYa(), motion.getZa());
              if (packet instanceof ClientboundPlayerPositionPacket teleport)
                pendingTeleport = teleport.id();
            }

            @Override
            public void send(@NotNull Packet<?> packet, PacketSendListener listener) {
              send(packet);
            }
          };
      handle.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
      handle.getBukkitEntity().setCanPickupItems(false);
      handle.setClientLoaded(true);
      reset(spawn);
    } catch (RuntimeException | Error e) {
      close();
      throw e;
    }
  }

  @Override
  public Player player() {
    return handle.getBukkitEntity();
  }

  public static void stopUsing(Player player) {
    ((CraftPlayer) player).getHandle().stopUsingItem();
  }

  // Reset combat state before a round
  public static void prepareRound(Player player) {
    ServerPlayer handle = ((CraftPlayer) player).getHandle();
    handle.stopUsingItem();
    handle.resetAttackStrengthTicker();
    handle.setSprinting(false);
    synchronizeEquipment(player);
  }

  public static void synchronizeEquipment(Player player) {
    ((CraftPlayer) player).getHandle().detectEquipmentUpdatesPublic();
  }

  public static boolean criticalsEnabled(Player player) {
    return !((CraftPlayer) player).getHandle().level()
        .paperConfig()
        .entities
        .behavior
        .disablePlayerCrits;
  }

  // Swing and attack target
  public static void strike(Player attacker, Player target) {
    ServerPlayer nativeAttacker = ((CraftPlayer) attacker).getHandle();
    ServerPlayer nativeTarget = ((CraftPlayer) target).getHandle();
    AttackSequence.execute(
        () -> nativeAttacker.attack(nativeTarget),
        () -> nativeAttacker.swing(InteractionHand.MAIN_HAND),
        nativeAttacker::resetAttackStrengthTicker);
  }

  @Override
  public void reset(Location location) {
    reset(location, true);
  }

  @Override
  public void relocate(Location location) {
    reset(location, false);
  }

  private void reset(Location location, boolean newRound) {
    movementEpoch++;
    critical.reset();
    utility.reset(newRound);
    swingClock.reset(ticks);
    axeUntil = 0;
    handle.stopUsingItem();
    handle.setDeltaMovement(Vec3.ZERO);
    pendingVelocity = null;
    handle.zza = 0;
    handle.xxa = 0;
    handle.setJumping(false);
    teleporting = true;
    try {
      if (!handle.getBukkitEntity().teleport(location))
        throw new IllegalStateException("Bot Teleport Cancelled");
    } finally {
      teleporting = false;
    }
    acknowledgeTeleport();
    handle.setClientLoaded(true);
    handle.setHealth(handle.getMaxHealth());
    handle.resetAttackStrengthTicker();
    history.clear();
    stuck = 0;
    waterTicks = 0;
    previous = location.clone();
    previousDecisionTick = ticks;
    shieldUntil = 0;
    currentPath = null;
    waypointIndex = 0;
    lastPathTick = 0;
    lastPathTarget = null;
  }

  @Override
  public boolean isTeleporting() {
    return teleporting;
  }

  @Override
  public void onOpponentHit() {
    critical.opponentHit(ticks);
    utility.onOpponentHit(ticks);
  }

  @Override
  public void onIncomingDamage(double amount) {
    utility.recordDamage(ticks, amount);
    if (currentDifficulty != null) utility.checkTotemEmergency(ticks, currentDifficulty);
  }

  @Override
  public void onRepairImpact(boolean repaired) {
    utility.repairImpact(ticks, repaired);
  }

  @Override
  public void onOpponentPearled(Player opponent) {
    utility.onOpponentPearled(opponent, ticks);
  }

  @Override
  public void onTotemPopped() {
    utility.onTotemPopped(ticks);
  }

  @Override
  public void configure(
      dev.insanmiy.practiceplugin.model.BotOptions options, boolean naturalRegeneration) {
    this.options = options;
    utility.options = options;
    utility.naturalRegeneration = naturalRegeneration;
  }

  private boolean isBotInWater() {
    if (handle.isInWater()) return true;
    Location loc = player().getLocation();
    return TacticalMovement.water(loc.getBlock())
        || TacticalMovement.water(loc.clone().add(0, 0.5, 0).getBlock());
  }

  private void boostOutOfWater() {
    if (handle.onGround()) {
      handle.jumpFromGround();
    } else if (handle.getDeltaMovement().y < 0.36
        && handle.level().noCollision(handle, handle.getBoundingBox().expandTowards(0, 1.0, 0))) {
      Vec3 vel = handle.getDeltaMovement();
      handle.setDeltaMovement(vel.x, 0.36, vel.z);
    }
  }

  @Override
  public void tick(Player target, Difficulty d, Arena arena, boolean fighting) {
    if (closed) return;
    this.currentDifficulty = d;
    utility.escapePearlChance = d.pearlChance();
    ticks++;
    acknowledgeTeleport();
    if (shieldUntil > 0 && (ticks >= shieldUntil || player().hasCooldown(Material.SHIELD))) {
      shieldUntil = 0;
      handle.stopUsingItem();
    }
    // Apply knockback and optional sprint jump
    if (pendingVelocity != null) {
      handle.setDeltaMovement(pendingVelocity);
      boolean aggressiveKnockbackResist = d.sprintResetChance() >= 0.85 || d.decisionTicks() <= 2;
      if (aggressiveKnockbackResist
          && handle.onGround()
          && pendingVelocity.horizontalDistanceSqr() > 0.02) {
        if (pendingVelocity.y <= 0.05) handle.jumpFromGround();
        handle.zza = 1.0f;
        handle.setSprinting(true);
      }
      pendingVelocity = null;
    }
    // Pause when not fighting
    if (!fighting) {
      utility.pause(ticks);
      handle.zza = 0;
      handle.xxa = 0;
      handle.setDeltaMovement(Vec3.ZERO);
      handle.stopUsingItem();
      return;
    }
    if (ticks >= resetSprintUntil
        && !handle.isSprinting()
        && handle.zza > 0
        && d.attacksEnabled()
        && ticks >= shieldUntil
        && (!critical.tracking(ticks) || handle.onGround())) {
      handle.setSprinting(true);
    }
    // Record target position history
    history.addLast(new Observation(target.getLocation(), target.isBlocking()));
    while (history.size() > d.perceptionTicks() + 1) history.removeFirst();
    boolean decided = false;
    if (!d.attacksEnabled()) {
      handle.zza = 0;
      handle.xxa = 0;
      handle.setSprinting(false);
      handle.stopUsingItem();
    } else if (ticks % d.decisionTicks() == 0 || critical.tracking(ticks) || utility.busy(ticks)) {
      // Run combat decision check
      decide(target, history.getFirst(), d);
      decided = true;
    }
    if (!decided && d.attacksEnabled() && !utility.busy(ticks) && !isBotInWater())
      combatLook(target, history.getFirst(), d);
    avoidHazards();
    if (handle.horizontalCollision && handle.onGround() && handle.zza > 0) {
      handle.jumpFromGround();
    }

    Vec3 beforeTravel = handle.position();

    boolean inWater = isBotInWater();
    boolean deepWater =
        inWater
            && (handle.isUnderWater()
                || TacticalMovement.water(player().getLocation().clone().add(0, 0.85, 0).getBlock()));
    // Sprinting in water triggers surface sprint-swimming, which looks like running on water.
    if (inWater) handle.setSprinting(false);
    handle.setJumping(
        d.attacksEnabled()
            && inWater
            && (deepWater || (handle.horizontalCollision && handle.zza > 0)));
    if (d.attacksEnabled() && inWater && handle.horizontalCollision && handle.zza > 0) {
      boostOutOfWater();
    }
    var beforeLevel = handle.level();
    int beforeEpoch = movementEpoch;
    // Process entity tick
    handle.doTick();

    if (beforeEpoch == movementEpoch
        && beforeLevel == handle.level()) {
      Vec3 moved = handle.position().subtract(beforeTravel);
      handle.doCheckFallDamage(moved.x, moved.y, moved.z, handle.onGround());
      if (beforeEpoch == movementEpoch) handle.checkMovementStatistics(moved.x, moved.y, moved.z);
    }
    // Attempt critical hit while falling
    if (d.attacksEnabled() && beforeEpoch == movementEpoch) tryFallingCritical(target);
    ((net.minecraft.server.level.ServerLevel) handle.level()).getChunkSource().move(handle);
    // Send look rotation to nearby players
    BotLook.publish(handle);
  }

  private void acknowledgeTeleport() {
    if (pendingTeleport != null) {
      int id = pendingTeleport;
      pendingTeleport = null;
      handle.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
    }
  }

  private void decide(Player target, Observation perceived, Difficulty d) {
    Location here = player().getLocation();
    Vector direction = perceived.location().toVector().subtract(here.toVector());
    double distance = direction.length();

    // Use utility items first
    if (utility.act(target, d, ticks)) return;
    combatLook(target, perceived, d);
    // Randomize strafe or reverse if stuck
    int elapsedTicks = Math.max(1, ticks - previousDecisionTick);
    if (ticks % 40 < d.decisionTicks()) strafe = random.nextBoolean() ? 1 : -1;
    if (previous != null && here.distanceSquared(previous) < .015 && (Math.abs(handle.zza) > .1 || Math.abs(handle.xxa) > .1))
      stuck += elapsedTicks;
    else stuck = 0;
    boolean inWater = isBotInWater();
    if (inWater) waterTicks += elapsedTicks;
    else waterTicks = 0;
    previous = here;
    previousDecisionTick = ticks;
    if (stuck >= 15 && handle.horizontalCollision && (handle.onGround() || inWater)) {
      boostOutOfWater();
    }
    if (stuck >= 25) {
      strafe *= -1;
      currentPath = null;
    }

    // Switch to axe against shields
    int sword = weapon("_SWORD"), axe = weapon("_AXE");
    boolean playerShielding = perceived.blocking() || target.isBlocking();
    if (axe >= 0 && playerShielding)
      axeUntil = ticks + 25;
    int selected = axe >= 0 && ticks < axeUntil ? axe : (sword >= 0 ? sword : axe);
    boolean switchedWeapon = selected >= 0 && selected != player().getInventory().getHeldItemSlot();
    if (selected >= 0) player().getInventory().setHeldItemSlot(selected);
    if (switchedWeapon) {
      utility.combatSelected();
      handle.stopUsingItem();
      handle.resetAttackStrengthTicker();
      synchronizeEquipment(player());
      swingClock.reset(ticks);
    }

    // Update movement inputs
    double horizontalDist = Math.hypot(direction.getX(), direction.getZ());
    double verticalDiff = perceived.location().getY() - here.getY();
    boolean closeCombat =
        horizontalDist <= 3.2
            && Math.abs(verticalDiff) <= 1.4
            && player().hasLineOfSight(target);

    // Prioritize exiting water onto dry land only when not already in close combat range
    Location waterExit = (!closeCombat && inWater) ? TacticalMovement.findWaterExit(here, target.getLocation()) : null;
    if (inWater && waterExit != null && waterTicks < 80) {
      double wx = waterExit.getX() - here.getX();
      double wy = waterExit.getY() - here.getY();
      double wz = waterExit.getZ() - here.getZ();
      double exitDistSq = wx * wx + wz * wz;

      if (exitDistSq <= 16.0) {
        currentPath = null;
        waypointIndex = 0;
        float exitYaw = (float) Math.toDegrees(Math.atan2(-wx, wz));
        BotLook.towards(handle, exitYaw, wy > 0.2 ? -20f : 0f, 180f);
        double curYawRad = Math.toRadians(handle.getYRot());
        double sin = Math.sin(curYawRad), cos = Math.cos(curYawRad);
        double hDist = Math.sqrt(exitDistSq);
        if (hDist > 0.05) {
          double nx = wx / hDist;
          double nz = wz / hDist;
          handle.zza = (float) (-nx * sin + nz * cos);
          handle.xxa = (float) (nx * cos + nz * sin);
          handle.setSprinting(handle.zza > 0.1f);
        } else {
          handle.zza = 1.0f;
          handle.xxa = 0;
          handle.setSprinting(true);
        }
        if ((wy > 0.25 && exitDistSq <= 1.8) || handle.horizontalCollision) {
          boostOutOfWater();
          handle.zza = 1.0f;
          handle.setSprinting(true);
        }
        return;
      }
    }

    if (closeCombat) {
      currentPath = null;
      waypointIndex = 0;
      handle.zza = distance > 1.1 ? 1.0f : Math.clamp((float) (0.35f + d.sprintResetChance() * 0.45f), 0.25f, 0.9f);
      handle.xxa = distance < 5 ? (float) d.strafeStrength() * strafe : 0;
      boolean canSprint = ticks >= resetSprintUntil
          && (distance > 1.0 || d.sprintResetChance() >= 0.7)
          && (!critical.tracking(ticks) || handle.onGround());
      handle.setSprinting(canSprint);
    } else {
      Location navGoal = (inWater && waterExit != null) ? waterExit : target.getLocation();
      if (currentPath == null
          || ticks - lastPathTick > 12
          || lastPathTarget == null
          || lastPathTarget.distanceSquared(navGoal) > 3.0
          || waypointIndex >= currentPath.size()) {
        lastPathTick = ticks;
        lastPathTarget = navGoal.clone();
        currentPath = TacticalPathfinder.findPath(player().getWorld(), here, navGoal, 1200);
        waypointIndex = 0;
      }

      if (currentPath != null && !currentPath.isEmpty() && waypointIndex < currentPath.size()) {
        TacticalPathfinder.Waypoint wp = currentPath.get(waypointIndex);
        double wx = wp.x() + 0.5 - here.getX();
        double wy = wp.y() - here.getY();
        double wz = wp.z() + 0.5 - here.getZ();
        double wDistSq = wx * wx + wz * wz;

        if (wDistSq < 0.40 && Math.abs(wy) < 1.0) {
          waypointIndex++;
          if (waypointIndex < currentPath.size()) {
            wp = currentPath.get(waypointIndex);
            wx = wp.x() + 0.5 - here.getX();
            wy = wp.y() - here.getY();
            wz = wp.z() + 0.5 - here.getZ();
            wDistSq = wx * wx + wz * wz;
          }
        }

        // Cut corners if line of sight to next waypoint is clear
        if (waypointIndex + 1 < currentPath.size()) {
          TacticalPathfinder.Waypoint nextWp = currentPath.get(waypointIndex + 1);
          Vector toNext = new Vector(nextWp.x() + 0.5 - here.getX(), 0, nextWp.z() + 0.5 - here.getZ());
          double nextDist = toNext.length();
          if (nextDist > 0.1 && Math.abs(nextWp.y() - here.getY()) <= 1.2
              && TacticalMovement.clearPath(here, toNext.clone().normalize(), nextDist)) {
            waypointIndex++;
            wp = nextWp;
            wx = wp.x() + 0.5 - here.getX();
            wy = wp.y() - here.getY();
            wz = wp.z() + 0.5 - here.getZ();
            wDistSq = wx * wx + wz * wz;
          }
        }

        float wpYaw = (float) Math.toDegrees(Math.atan2(-wx, wz));
        boolean seeTarget = !inWater && player().hasLineOfSight(target);
        float aimYaw = (distance < 7.0 && seeTarget)
            ? player().getEyeLocation().setDirection(perceived.location().clone().add(0, target.getHeight() * .65, 0).toVector().subtract(player().getEyeLocation().toVector())).getYaw()
            : wpYaw;
        float aimPitch = (float) (wy > 0.4 ? -25 : (wy < -0.4 ? 20 : (Math.sin(ticks * .075) * d.aimError())));
        BotLook.towards(handle, aimYaw, aimPitch, 180f);

        double curYawRad = Math.toRadians(handle.getYRot());
        double sin = Math.sin(curYawRad), cos = Math.cos(curYawRad);
        double hDist = Math.sqrt(wDistSq);
        if (hDist > 0.05) {
          double nx = wx / hDist;
          double nz = wz / hDist;
          handle.zza = (float) (-nx * sin + nz * cos);
          handle.xxa = (float) (nx * cos + nz * sin);
          handle.setSprinting(handle.zza > 0.2f);
        } else {
          handle.zza = 0.6f;
          handle.xxa = 0;
        }

        if (((wp.action() == TacticalPathfinder.ActionType.JUMP_UP || wy > 0.35) && wDistSq <= 1.8
                || handle.horizontalCollision)
            && (handle.onGround() || inWater)) {
          boostOutOfWater();
          handle.setSprinting(true);
          handle.zza = 1.0f;
        }
      } else {
        // No path found (wall / pit / obstruction)
        if (verticalDiff < -1.5 && horizontalDist > 1.8) {
          Vector toAbove = new Vector(target.getLocation().getX() - here.getX(), 0, target.getLocation().getZ() - here.getZ());
          if (toAbove.lengthSquared() > 0.01) {
            float walkYaw = (float) Math.toDegrees(Math.atan2(-toAbove.getX(), toAbove.getZ()));
            BotLook.towards(handle, walkYaw, 25f, 180f);
            handle.zza = 1.0f;
            handle.xxa = 0;
            handle.setSprinting(true);
            if (handle.horizontalCollision && (handle.onGround() || inWater)) {
              boostOutOfWater();
            }
          }
        } else {
          combatLook(target, perceived, d);
          handle.zza = 0.7f;
          handle.xxa = 0;
          if (handle.horizontalCollision && (handle.onGround() || inWater)) {
            boostOutOfWater();
          }
        }
      }
      return;
    }
    if (ticks < shieldUntil && !player().hasCooldown(Material.SHIELD)) {
      handle.zza *= .2f;
      handle.xxa *= .2f;
      return;
    }
    shieldUntil = 0;
    handle.stopUsingItem();
    // Check line of sight and obstructions
    Vector facing = player().getEyeLocation().getDirection();
    var hit = target.getBoundingBox().expand(0.2).rayTrace(player().getEyeLocation().toVector(), facing, 3.2);
    double rayLength =
        hit == null ? 3.2 : hit.getHitPosition().distance(player().getEyeLocation().toVector());
    var obstruction = CombatSight.obstruction(player(), facing, rayLength);
    if (obstruction != null) {
      if (obstruction.getType() == Material.COBWEB) utility.clearWeb(obstruction, ticks);
      return;
    }
    double charge = handle.getAttackStrengthScale(0);
    boolean ready =
        AttackGate.ready(charge, switchedWeapon, selected >= 0, d.attackCharge())
            && swingClock.ready(
                ticks,
                Attributes.getValue(player(), Attributes.ATTACK_SPEED, 4.0),
                charge,
                d.attackCharge());
    boolean critEligible =
        options.criticals()
            && d.critChance() > 0.0
            && !handle.onClimbable()
            && !handle.isInWater()
            && !handle.isPassenger()
            && !handle.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
            && !handle.level().paperConfig().entities.behavior.disablePlayerCrits
            && !perceived.blocking();
    boolean inAttackRange = (hit != null || distance <= 3.2) && player().hasLineOfSight(target);
    boolean canJump =
        !handle.horizontalCollision
            && handle.level()
                .noCollision(handle, handle.getBoundingBox().expandTowards(0, 1.3, 0));
    boolean chooseJump = inAttackRange && canJump && random.nextDouble() < d.critChance();

    boolean falling = !handle.onGround()
        && handle.getDeltaMovement().y < 0
        && (handle.fallDistance > 0 || handle.getDeltaMovement().y < -0.05);
    if (falling && handle.fallDistance <= 0.0f) {
      handle.fallDistance = (float) Math.max(0.1, -handle.getDeltaMovement().y);
    }

    // Check if critical jump is ready
    CriticalWindow.Action criticalAction =
        critical.decide(
            ticks,
            handle.onGround(),
            falling,
            critEligible,
            ready,
            inAttackRange,
            chooseJump);
    if (criticalAction != CriticalWindow.Action.NONE) {
      handle.setSprinting(false);
      handle.zza = 1.0f;
      handle.xxa *= .5f;
      if (criticalAction == CriticalWindow.Action.JUMP) {
        handle.jumpFromGround();
        resetSprintUntil = ticks + 12;
        return;
      }
      if (criticalAction == CriticalWindow.Action.WAIT) return;
    }
    boolean opponentHoldingAxe =
        target.getInventory().getItemInMainHand().getType().name().endsWith("_AXE");
    // Attack target and reset sprint
    if (ready && hit != null && player().hasLineOfSight(target)) {
      handle.setSprinting(false);
      swingClock.attempted(ticks);
      strike(player(), target);
      critical.attacked();
      if (random.nextDouble() < d.sprintResetChance()) {
        int sprintDelay = Math.max(1, (int) Math.round((1.0 - d.sprintResetChance()) * 5.0 + 1.0));
        resetSprintUntil = ticks + sprintDelay;
      } else {
        resetSprintUntil = ticks + 1;
      }
    // Block with shield if safe
    } else if (distance < 3.6
        && distance > 0.05
        && target
                .getEyeLocation()
                .getDirection()
                .dot(
                    player()
                        .getEyeLocation()
                        .toVector()
                        .subtract(target.getEyeLocation().toVector())
                        .normalize())
            > .35
        && player().getInventory().getItemInOffHand().getType() == Material.SHIELD
        && !player().hasCooldown(Material.SHIELD)
        && !(opponentHoldingAxe && (d.counters() || d.decisionTicks() <= 2))
        && random.nextDouble() < d.shieldChance()) {
      handle.startUsingItem(InteractionHand.OFF_HAND);
      shieldUntil = ticks + d.shieldTicks();
    }
  }

  // Critical strike while falling
  private void tryFallingCritical(Player target) {
    if (!critical.tracking(ticks)
        || utility.busy(ticks)
        || handle.onGround()
        || handle.getDeltaMovement().y >= 0
        || handle.onClimbable()
        || handle.isInWater()
        || handle.isPassenger()
        || handle.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
        || handle.level().paperConfig().entities.behavior.disablePlayerCrits
        || !Kit.melee(player().getInventory().getItemInMainHand().getType())) return;
    if (handle.fallDistance <= 0.0f) {
      handle.fallDistance = (float) Math.max(0.1, -handle.getDeltaMovement().y);
    }
    double charge = handle.getAttackStrengthScale(0);
    double minCharge = currentDifficulty != null ? currentDifficulty.attackCharge() : 1.0;
    if (!swingClock.ready(
            ticks,
            Attributes.getValue(player(), Attributes.ATTACK_SPEED, 4.0),
            charge,
            minCharge)
        || charge < minCharge) return;
    Vector direction = player().getEyeLocation().getDirection();
    var hit = target.getBoundingBox().expand(0.2).rayTrace(player().getEyeLocation().toVector(), direction, 3.2);
    if (hit == null
        || CombatSight.obstruction(
                player(),
                direction,
                hit.getHitPosition().distance(player().getEyeLocation().toVector()))
            != null) return;
    handle.setSprinting(false);
    swingClock.attempted(ticks);
    strike(player(), target);
    critical.attacked();
    double sprintChance = currentDifficulty != null ? currentDifficulty.sprintResetChance() : 0.85;
    if (random.nextDouble() < sprintChance) {
      int sprintDelay = Math.max(1, (int) Math.round((1.0 - sprintChance) * 5.0 + 1.0));
      resetSprintUntil = ticks + sprintDelay;
    } else {
      resetSprintUntil = ticks + 1;
    }
  }

  // Avoid falling into hazards
  private void avoidHazards() {
    if (isBotInWater()) return;
    if (!handle.onGround() || (handle.zza == 0 && handle.xxa == 0)) return;
    if (currentPath != null && !currentPath.isEmpty() && waypointIndex < currentPath.size()) return;
    double yaw = Math.toRadians(handle.getYRot());
    double sin = Math.sin(yaw), cos = Math.cos(yaw);
    double moveX = -sin * handle.zza + cos * handle.xxa;
    double moveZ = cos * handle.zza + sin * handle.xxa;
    double lenSq = moveX * moveX + moveZ * moveZ;
    if (lenSq < .001) return;

    Location loc = player().getLocation();
    double invLen = 1.0 / Math.sqrt(lenSq);
    if (TacticalMovement.clearPath(loc, new Vector(moveX * invLen, 0, moveZ * invLen), .7)) return;

    handle.zza = 0;
    handle.setSprinting(false);
    Vector sideStrafe = new Vector(cos * strafe, 0, sin * strafe);
    if (TacticalMovement.clearPath(loc, sideStrafe, .7)) {
      handle.xxa = .55f * strafe;
    } else if (TacticalMovement.clearPath(loc, sideStrafe.multiply(-1), .7)) {
      strafe *= -1;
      handle.xxa = .55f * strafe;
    } else {
      handle.xxa = 0;
    }
  }

  // Aim towards target
  private void combatLook(Player target, Observation perceived, Difficulty difficulty) {
    Vector direction =
        perceived
            .location()
            .clone()
            .add(0, target.getHeight() * .65, 0)
            .toVector()
            .subtract(player().getEyeLocation().toVector());
    Location aim = player().getEyeLocation().setDirection(direction);
    float survey = dev.insanmiy.practiceplugin.model.LookMotion.glance(ticks, direction.length());

    float error = (float) (Math.sin(ticks * .075) * difficulty.aimError());
    double baseSpeed = (450.0 / (difficulty.decisionTicks() + 0.25))
        - (difficulty.aimError() * 5.0)
        - (difficulty.perceptionTicks() * 10.0);
    float turnSpeed = (float) Math.clamp(baseSpeed, 30.0, 360.0);
    BotLook.towards(
        handle, aim.getYaw() + error + survey, aim.getPitch() + (survey == 0 ? 0 : 3), turnSpeed);
  }

  private int weapon(String suffix) {
    for (int i = 0; i < 9; i++) {
      ItemStack item = player().getInventory().getItem(i);
      if (item != null && item.getType().name().endsWith(suffix)) return i;
    }
    return -1;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    try {
      server.getPlayerList().remove(handle);
    } finally {
      connection.active = false;
      connection.channel.close();
      history.clear();
    }
  }

  // Fake connection for the bot entity
  private static final class DummyConnection extends Connection {
    boolean active = true;

    DummyConnection() {
      super(PacketFlow.SERVERBOUND);
      channel = new EmbeddedChannel();
      address = new InetSocketAddress("127.0.0.1", 0);
      Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
    }

    @Override
    public boolean isConnected() {
      return active;
    }

    @Override
    public void send(@NotNull Packet<?> packet) {}

    @Override
    public void send(@NotNull Packet<?> packet, PacketSendListener listener) {}

    @Override
    public void send(@NotNull Packet<?> packet, PacketSendListener listener, boolean flush) {}
  }
}
