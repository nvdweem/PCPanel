package dev.niels.sonar;

import java.util.Optional;

import dev.niels.sonar.model.SonarMode;
import dev.niels.sonar.model.SonarRoute;
import dev.niels.sonar.model.SonarState;

/**
 * SteelSeries Sonar's local HTTP API. Sonar has no push channel, so a caller that wants to follow changes
 * made in GG polls {@link #fetchMode()} and {@link #fetchState(SonarMode)}. Every call blocks on loopback
 * HTTP for at most a couple of seconds and never throws: an unreachable Sonar answers empty or false.
 */
public interface ISonarClient {
    /** Drops the discovered address, so the next call finds Sonar again (GG picks a new port per launch). */
    void invalidate();

    Optional<SonarMode> fetchMode();

    /** Every channel's level in the given mode, keyed by the route that writes it. */
    Optional<SonarState> fetchState(SonarMode mode);

    /** @param value 0..1 */
    boolean setVolume(SonarRoute route, double value);

    boolean setMute(SonarRoute route, boolean muted);
}
