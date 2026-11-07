package org.lab2.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Services services = new Services();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    public Services getServices() {
        return services;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public static class Services {

        private String flight;
        private String ticket;
        private String bonus;

        public String getFlight() {
            return flight;
        }

        public void setFlight(String flight) {
            this.flight = flight;
        }

        public String getTicket() {
            return ticket;
        }

        public void setTicket(String ticket) {
            this.ticket = ticket;
        }

        public String getBonus() {
            return bonus;
        }

        public void setBonus(String bonus) {
            this.bonus = bonus;
        }
    }

    public static class CircuitBreaker {

        /** Количество подряд идущих отказов, после которого цепь размыкается. */
        private int threshold = 3;

        /** Таймаут (мс) через который разомкнутая цепь проверяет реальный сервис. */
        private long resetTimeout = 10000;

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public long getResetTimeout() {
            return resetTimeout;
        }

        public void setResetTimeout(long resetTimeout) {
            this.resetTimeout = resetTimeout;
        }
    }
}
