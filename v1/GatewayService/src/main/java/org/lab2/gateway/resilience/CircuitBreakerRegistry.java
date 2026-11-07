package org.lab2.gateway.resilience;

import org.lab2.gateway.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Хранит по одному Circuit Breaker на каждый downstream-сервис.
 */
@Component
public class CircuitBreakerRegistry {

    private final CircuitBreaker flight;
    private final CircuitBreaker ticket;
    private final CircuitBreaker bonus;

    public CircuitBreakerRegistry(AppProperties properties) {
        int threshold = properties.getCircuitBreaker().getThreshold();
        long resetTimeout = properties.getCircuitBreaker().getResetTimeout();
        this.flight = new CircuitBreaker("flight", threshold, resetTimeout);
        this.ticket = new CircuitBreaker("ticket", threshold, resetTimeout);
        this.bonus = new CircuitBreaker("bonus", threshold, resetTimeout);
    }

    public CircuitBreaker flight() {
        return flight;
    }

    public CircuitBreaker ticket() {
        return ticket;
    }

    public CircuitBreaker bonus() {
        return bonus;
    }
}
