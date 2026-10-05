package com.cadence.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cadence.messaging")
public class MessagingProperties {
    private final Outbox outbox = new Outbox();
    private final Consumer consumer = new Consumer();

    public Outbox getOutbox() {
        return outbox;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public static class Outbox {
        /** Whether this instance relays outbox rows to Kafka. */
        private boolean relayEnabled = true;
        /** Delay between relay polls. */
        private Duration relayInterval = Duration.ofMillis(500);
        /** Max rows relayed per poll. */
        private int batchSize = 100;
        /** How long to wait for Kafka to acknowledge one send. */
        private Duration sendTimeout = Duration.ofSeconds(10);
        /** Sent rows older than this are deleted. */
        private Duration retention = Duration.ofDays(7);
        /** How often sent rows past the retention are deleted. */
        private Duration cleanupInterval = Duration.ofHours(1);

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean relayEnabled) {
            this.relayEnabled = relayEnabled;
        }

        public Duration getRelayInterval() {
            return relayInterval;
        }

        public void setRelayInterval(Duration relayInterval) {
            this.relayInterval = relayInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getSendTimeout() {
            return sendTimeout;
        }

        public void setSendTimeout(Duration sendTimeout) {
            this.sendTimeout = sendTimeout;
        }

        public Duration getRetention() {
            return retention;
        }

        public void setRetention(Duration retention) {
            this.retention = retention;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }
    }

    public static class Consumer {
        /** Retries after the first failed attempt before a message goes to the DLT. */
        private int maxRetries = 4;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private double backoffMultiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(10);

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }
}
