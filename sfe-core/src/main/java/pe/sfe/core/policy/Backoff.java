package pe.sfe.core.policy;

import java.time.Duration;
import java.util.function.DoubleSupplier;

/** Backoff exponencial con tope y jitter: base * 2^(intento-1), acotado por cap, +-jitter%. */
public record Backoff(Duration base, Duration cap, double jitter) {

    public static Backoff porDefecto() {
        return new Backoff(Duration.ofSeconds(10), Duration.ofMinutes(15), 0.2);
    }

    public Duration retraso(int intento, DoubleSupplier azar) {
        int exp = Math.min(Math.max(intento, 1) - 1, 30);
        double ms = Math.min((double) cap.toMillis(), base.toMillis() * Math.pow(2, exp));
        double factor = 1 + jitter * (2 * azar.getAsDouble() - 1);
        return Duration.ofMillis(Math.max(1, Math.round(ms * factor)));
    }
}
