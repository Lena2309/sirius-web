package org.eclipse.sirius.web.ai.configuration;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockingRateLimiter {
    private boolean isDone = false;
    private final AtomicInteger permits;
    private final int maxPermits;
    private final Duration permitRegenerationTime;
    private final Object regenLock = new Object();

    public BlockingRateLimiter(int permits, Duration permitRegenerationTime) {
        this.permits = new AtomicInteger(permits);
        this.maxPermits = permits;
        this.permitRegenerationTime = permitRegenerationTime;

        new Thread(this::regenerate).start();
    }

    public int getPermits() {
        return permits.get();
    }

    public void acquire(Logger logger) {
        synchronized (permits) {
            while (permits.get() <= 0) {
                try {
                    logger.warn("No permits available, waiting...");
                    permits.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            permits.decrementAndGet(); // Consume a permit
        }
    }

    private void regenerate() {
        while (!isDone) {
            synchronized (regenLock) {
                try {
                    regenLock.wait(permitRegenerationTime.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            synchronized (permits) {
                if (permits.get() < maxPermits) {
                    permits.set(maxPermits);
                    System.out.println("Permits reset to " + maxPermits);
                    while (permits.get() > 0) {
                        permits.notify();
                    }
                }
            }
        }
    }

    public void done() {
        this.isDone = true;
        synchronized (regenLock) {
            regenLock.notify();
        }
    }
}
