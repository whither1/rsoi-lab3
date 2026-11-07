package org.lab2.gateway.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

/**
 * Простой in-memory Circuit Breaker.
 *
 * <p>Пока сервис отвечает, цепь замкнута ({@link State#CLOSED}). После {@code failureThreshold}
 * подряд идущих ошибок цепь размыкается ({@link State#OPEN}) и запросы к реальному сервису не
 * выполняются — сразу возвращается fallback. Через {@code resetTimeoutMillis} цепь переходит в
 * полуоткрытое состояние ({@link State#HALF_OPEN}) и один запрос выполняется для проверки
 * состояния сервиса: успех замыкает цепь, ошибка снова её размыкает.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    /** Сколько раз пробуем выполнить запрос при ошибках соединения. */
    private static final int MAX_ATTEMPTS = 4;

    /** Пауза между попытками, мс (даёт пулу соединений выбросить «протухший» коннект). */
    private static final long RETRY_BACKOFF_MS = 250L;

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final long resetTimeoutMillis;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0L;

    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMillis) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMillis = resetTimeoutMillis;
    }

    /**
     * Выполняет операцию чтения. При разомкнутой цепи сразу возвращает fallback, не обращаясь к
     * сервису. Ошибки 4xx пробрасываются как есть (сервис ответил, он доступен), а ошибки
     * недоступности/5xx учитываются как отказ, после чего возвращается fallback.
     */
    public <T> T execute(Supplier<T> call, Supplier<T> fallback) {
        if (!allowRequest()) {
            log.warn("[{}] circuit OPEN, returning fallback", name);
            return fallback.get();
        }
        try {
            T result = callWithRetry(call);
            recordSuccess();
            return result;
        } catch (HttpClientErrorException e) {
            // 4xx — сервис ответил, значит он жив; ответ отдаём клиенту без изменений
            recordSuccess();
            throw e;
        } catch (RestClientException e) {
            log.warn("[{}] read failed after retries ({}), returning fallback", name, e.toString());
            recordFailure();
            return fallback.get();
        }
    }

    /**
     * Выполняет операцию чтения из некритичного источника: любая ошибка (в том числе 4xx)
     * заменяется fallback-значением.
     */
    public <T> T executeLenient(Supplier<T> call, Supplier<T> fallback) {
        if (!allowRequest()) {
            log.warn("[{}] circuit OPEN, returning fallback", name);
            return fallback.get();
        }
        try {
            T result = callWithRetry(call);
            recordSuccess();
            return result;
        } catch (RestClientException e) {
            log.warn("[{}] read failed after retries ({}), returning fallback", name, e.toString());
            recordFailure();
            return fallback.get();
        }
    }

    /**
     * После перезапуска downstream-сервиса пул соединений клиента может содержать «протухшее»
     * keep-alive соединение. Несколько повторных попыток с небольшой паузой позволяют прозрачно
     * переустановить соединение. Число попыток не влияет на статистику отказов — она учитывается
     * один раз на вызов.
     */
    private <T> T callWithRetry(Supplier<T> call) {
        ResourceAccessException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                last = e;
                log.warn("[{}] connection error on attempt {}/{}: {}", name, attempt, MAX_ATTEMPTS, e.toString());
                sleep();
            }
        }
        throw last;
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= resetTimeoutMillis) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    private synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    private synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
            log.warn("[{}] circuit OPEN after {} consecutive failures", name, consecutiveFailures);
        }
    }

    public synchronized State getState() {
        return state;
    }
}
