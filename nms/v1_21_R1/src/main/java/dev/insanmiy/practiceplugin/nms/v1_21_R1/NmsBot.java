package dev.insanmiy.practiceplugin.nms.v1_21_R1;

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
  private int ticks, strafe = 1, shieldUntil, resetSprintUntil, stuck;
  private float inputZ, inputX;
  private Location previous;
  private Vec3 pendingVelocity;
  private Integer pendingTeleport;

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
            public void send(Packet<?> packet) {

              if (packet instanceof ClientboundSetEntityMotionPacket motion
                  && motion.getId() == handle.getId())
                pendingVelocity = new Vec3(motion.getXa(), motion.getYa(), motion.getZa());
              if (packet instanceof ClientboundPlayerPositionPacket teleport)
                pendingTeleport = teleport.getId();
            }

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
              send(packet);
            }
          };
      handle.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
      handle.getBukkitEntity().setCanPickupItems(false);
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

  public static double attackCharge(Player player) {
    return ((CraftPlayer) player).getHandle().getAttackStrengthScale(.5f);
  }

  public static boolean criticalsEnabled(Player player) {
    return !((ServerLevel) ((CraftPlayer) player).getHandle().level())
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
    inputZ = 0;
    inputX = 0;
    handle.setJumping(false);
    teleporting = true;
    try {
      if (!handle.getBukkitEntity().teleport(location))
        throw new IllegalStateException("Bot teleport cancelled");
    } finally {
      teleporting = false;
    }
    acknowledgeTeleport();
    handle.setHealth(20);
    handle.resetAttackStrengthTicker();
    history.clear();
    stuck = 0;
    previous = location.clone();
    previousDecisionTick = ticks;
    shieldUntil = 0;
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
  public void configure(
      dev.insanmiy.practiceplugin.model.BotOptions options, boolean naturalRegeneration) {
    this.options = options;
    utility.options = options;
    utility.naturalRegeneration = naturalRegeneration;
  }

  @Override
  public void tick(Player target, Difficulty d, Arena arena, boolean fighting) {
    if (closed) return;
    this.currentDifficulty = d;
    ticks++;
    acknowledgeTeleport();
    // Apply knockback and optional sprint jump
    if (pendingVelocity != null) {
      handle.setDeltaMovement(pendingVelocity);
      boolean aggressiveKnockbackResist = d.sprintResetChance() >= 0.85 || d.decisionTicks() <= 2;
      if (aggressiveKnockbackResist
          && handle.onGround()
          && pendingVelocity.horizontalDistanceSqr() > 0.04) {
        handle.zza = 0.6f;
      }
      pendingVelocity = null;
    }
    // Pause when not fighting
    if (!fighting) {
      utility.pause(ticks);
      handle.zza = 0;
      handle.xxa = 0;
      inputZ = 0;
      inputX = 0;
      handle.setDeltaMovement(Vec3.ZERO);
      handle.stopUsingItem();
      return;
    }
    // Record target position history
    history.addLast(new Observation(target.getLocation(), target.isBlocking()));
    while (history.size() > d.perceptionTicks() + 1) history.removeFirst();
    boolean decided = false;
    if (!d.attacksEnabled()) {
      handle.zza = 0;
      handle.xxa = 0;
      inputZ = 0;
      inputX = 0;
      handle.setSprinting(false);
      handle.stopUsingItem();
    } else if (ticks % d.decisionTicks() == 0 || critical.tracking(ticks) || utility.busy(ticks)) {
      // Run combat decision check
      decide(target, history.getFirst(), d, arena);
      decided = true;
      inputZ = handle.zza;
      inputX = handle.xxa;
    }
    if (!decided && d.attacksEnabled() && !utility.busy(ticks)) {
      combatLook(target, history.getFirst(), d);
      handle.zza = inputZ;
      handle.xxa = inputX;
    }
    avoidHazards();
    inputZ = handle.zza;
    inputX = handle.xxa;

    Vec3 beforeTravel = handle.position();

    handle.setJumping(d.attacksEnabled() && handle.isInWater());
    var beforeLevel = handle.level();
    int beforeEpoch = movementEpoch;
    float travelZ = handle.zza * 0.72f;
    float travelX = handle.xxa * 0.72f;
    handle.travel(new Vec3(travelX, handle.yya, travelZ));
    if (handle.onGround()) {
      double maxSpeed = handle.isSprinting() ? 0.30 : 0.23;
      var speedAttr = handle.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
      if (speedAttr != null && speedAttr.getValue() > 0) {
        maxSpeed *= (speedAttr.getValue() / 0.1);
      }
      Vec3 dm = handle.getDeltaMovement();
      double horizSqr = dm.x * dm.x + dm.z * dm.z;
      if (horizSqr > maxSpeed * maxSpeed) {
        double factor = maxSpeed / Math.sqrt(horizSqr);
        handle.setDeltaMovement(dm.x * factor, dm.y, dm.z * factor);
      }
    }
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

  private void decide(Player target, Observation perceived, Difficulty d, Arena arena) {
    Location here = player().getLocation();
    Vector direction = perceived.location().toVector().subtract(here.toVector());
    double distance = direction.length();

    // Use utility items first
    if (utility.act(target, d, ticks)) return;
    combatLook(target, perceived, d);
    // Randomize strafe or reverse if stuck
    if (ticks % 40 < d.decisionTicks()) strafe = random.nextBoolean() ? 1 : -1;
    if (previous != null && here.distanceSquared(previous) < .015 && Math.abs(handle.zza) > .1)
      stuck += ticks - previousDecisionTick;
    else stuck = 0;
    previous = here;
    previousDecisionTick = ticks;
    if (stuck >= 20) strafe *= -1;
    if (handle.horizontalCollision && handle.onGround()) handle.jumpFromGround();

    // Switch to axe against shields
    int sword = weapon("_SWORD"), axe = weapon("_AXE");
    boolean playerShielding = perceived.blocking() || target.isBlocking();
    if (axe >= 0 && playerShielding)
      axeUntil = ticks + 25;
    int selected = axe >= 0 && ticks < axeUntil && playerShielding ? axe : (sword >= 0 ? sword : axe);
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
    if (distance > 3.0) {
      handle.zza = 1.0f;
    } else if (distance > 1.8) {
      handle.zza = 0.7f;
    } else {
      handle.zza = (float) Math.min(0.45f, Math.max(0.15f, 0.25f + (d.sprintResetChance() * 0.2f)));
    }
    handle.xxa = distance < 5 ? (float) (d.strafeStrength() * 0.55) * strafe : 0;
    handle.setSprinting(ticks >= resetSprintUntil && distance > 2.2);
    avoidHazards();
    if (ticks < shieldUntil) {
      handle.zza *= .2f;
      handle.xxa *= .2f;
      return;
    }
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
                dev.insanmiy.practiceplugin.Attributes.getValue(player(), dev.insanmiy.practiceplugin.Attributes.ATTACK_SPEED, 4.0),
                charge,
                d.attackCharge());
    boolean critEligible =
        options.criticals()
            && !handle.onClimbable()
            && !handle.isInWater()
            && !handle.isPassenger()
            && !player().hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
            && !((net.minecraft.server.level.ServerLevel) handle.level()).paperConfig().entities.behavior.disablePlayerCrits
            && !perceived.blocking();
    double critChance = d.decisionTicks() <= 1 ? 0.98 : (d.decisionTicks() <= 3 ? 0.85 : (d.decisionTicks() <= 6 ? 0.50 : 0.20));
    // Check if critical jump is ready
    CriticalWindow.Action criticalAction =
        critical.decide(
            ticks,
            handle.onGround(),
            handle.fallDistance > 0 && handle.getDeltaMovement().y < 0,
            critEligible,
            ready,
            distance <= 3.2 && player().hasLineOfSight(target),
            distance <= 2.9
                && !handle.horizontalCollision
                && ((ServerLevel) handle.level())
                    .noCollision(handle, handle.getBoundingBox().expandTowards(0, 1.3, 0))
                && random.nextDouble() < critChance);
    if (criticalAction != CriticalWindow.Action.NONE) {
      handle.setSprinting(false);
      handle.zza = 1.0f;
      handle.xxa *= .5f;
      if (criticalAction == CriticalWindow.Action.JUMP) {
        handle.jumpFromGround();
        return;
      }
      if (criticalAction == CriticalWindow.Action.WAIT) return;
    }
    boolean opponentHoldingAxe =
        target.getInventory().getItemInMainHand() != null
            && target.getInventory().getItemInMainHand().getType().name().endsWith("_AXE");
    // Attack target and reset sprint
    if (ready && hit != null && player().hasLineOfSight(target)) {
      swingClock.attempted(ticks);
      strike(player(), target);
      critical.attacked();
      if (random.nextDouble() < d.sprintResetChance()) {
        handle.setSprinting(false);
        resetSprintUntil = ticks + 1;
      }
    // Block with shield if safe
    } else if (distance < 3.6
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
        || handle.fallDistance <= 0
        || handle.getDeltaMovement().y >= 0
        || handle.onClimbable()
        || handle.isInWater()
        || handle.isPassenger()
        || handle.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
        || ((net.minecraft.server.level.ServerLevel) handle.level()).paperConfig().entities.behavior.disablePlayerCrits
        || !Kit.melee(player().getInventory().getItemInMainHand().getType())) return;
    double charge = handle.getAttackStrengthScale(0);
    double minCharge = currentDifficulty != null ? currentDifficulty.attackCharge() : 1.0;
    if (!swingClock.ready(
            ticks,
            dev.insanmiy.practiceplugin.Attributes.getValue(player(), dev.insanmiy.practiceplugin.Attributes.ATTACK_SPEED, 4.0),
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
    Location look = player().getEyeLocation().setDirection(direction);
    handle.setYRot(look.getYaw());
    handle.setXRot(look.getPitch());
    handle.setSprinting(false);
    swingClock.attempted(ticks);
    strike(player(), target);
    critical.attacked();
    resetSprintUntil = ticks + 1;
  }

  // Avoid falling into hazards
  private void avoidHazards() {
    if (!handle.onGround() && !handle.isInWater()) return;
    double yaw = Math.toRadians(handle.getYRot());
    Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
    Vector movement = forward.clone().multiply(handle.zza).add(side.clone().multiply(handle.xxa));
    if (movement.lengthSquared() < .001
        || TacticalMovement.clearPath(player().getLocation(), movement.normalize(), .7)) return;
    handle.zza = 0;
    handle.setSprinting(false);
    if (TacticalMovement.clearPath(player().getLocation(), side.clone().multiply(strafe), .7))
      handle.xxa = .55f * strafe;
    else if (TacticalMovement.clearPath(
        player().getLocation(), side.clone().multiply(-strafe), .7)) {
      strafe *= -1;
      handle.xxa = .55f * strafe;
    } else handle.xxa = 0;
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
    float turnSpeed = (float) Math.max(30.0, Math.min(360.0, baseSpeed));
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
      if (handle.connection != null) server.getPlayerList().remove(handle);
      else handle.discard();
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
    public void send(Packet<?> packet) {}

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {}

    @Override
    public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {}
  }
}
