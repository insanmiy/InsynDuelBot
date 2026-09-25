package dev.insanmiy.practiceplugin.model;

import java.util.ArrayDeque;

public final class DamagePressure {
  private record Hit(int tick, double damage) {}

  private final ArrayDeque<Hit> hits = new ArrayDeque<>();

  public void record(int tick, double damage) {
    if (!Double.isFinite(damage) || damage <= 0) return;
    prune(tick);
    hits.addLast(new Hit(tick, damage));
    while (hits.size() > 20) hits.removeFirst();
  }

  public void reset() {
    hits.clear();
  }

  public double forecast(int tick, double exposedTicks) {
    prune(tick);
    if (hits.isEmpty() || exposedTicks <= 0) return 0;
    double interval =
        hits.size() < 2
            ? 20
            : Math.max(
                10,
                Math.min(
                    30,
                    (double) (hits.getLast().tick() - hits.getFirst().tick()) / (hits.size() - 1)));
    double average = hits.stream().mapToDouble(Hit::damage).average().orElse(0);
    double recency = Math.max(0, 1 - (tick - hits.getLast().tick()) / 60.0);
    return average * Math.min(40, exposedTicks) / interval * recency;
  }

  private void prune(int tick) {
    while (!hits.isEmpty() && tick - hits.getFirst().tick() >= 60) hits.removeFirst();
  }
}
