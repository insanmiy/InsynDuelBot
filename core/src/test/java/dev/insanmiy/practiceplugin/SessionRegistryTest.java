package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {
  @Test
  void botsResolveDirectlyAndClosingOneSessionKeepsAnother() {
    SessionRegistry<Object> registry = new SessionRegistry<>();
    UUID owner = UUID.randomUUID(), otherOwner = UUID.randomUUID();
    UUID bot = UUID.randomUUID(), otherBot = UUID.randomUUID();
    Object session = new Object(), other = new Object();
    registry.put(owner, session);
    registry.put(otherOwner, other);
    registry.registerBot(bot, session);
    registry.registerBot(otherBot, other);
    assertSame(session, registry.participant(bot));
    assertSame(session, registry.participant(owner));
    registry.remove(owner, other);
    assertSame(session, registry.participant(bot));
    registry.remove(owner, session);
    assertNull(registry.participant(bot));
    assertFalse(registry.isBot(bot));
    assertSame(other, registry.participant(otherBot));
    assertThrows(UnsupportedOperationException.class, () -> registry.values().clear());
  }

  @Test
  void unregisteringEliminatedBotsDoesNotRemoveTheirOwnersSession() {
    SessionRegistry<Object> registry = new SessionRegistry<>();
    UUID owner = UUID.randomUUID(), bot = UUID.randomUUID();
    Object session = new Object();
    registry.put(owner, session);
    registry.registerBot(bot, session);
    registry.unregisterBot(bot);
    assertNull(registry.participant(bot));
    assertSame(session, registry.get(owner));
    assertThrows(IllegalStateException.class, () -> registry.put(owner, new Object()));
  }
}
