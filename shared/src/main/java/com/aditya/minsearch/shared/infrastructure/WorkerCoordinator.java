package com.aditya.minsearch.shared.infrastructure;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.*;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/** Stops admissions before shutdown, and drains leases while persistence beans remain available. */
@Component
public final class WorkerCoordinator implements SmartLifecycle, ApplicationListener<ContextClosedEvent> {
  private final Object monitor = new Object();
  private final long timeoutMillis;
  private boolean accepting = true;
  private boolean running;
  private int active;
  public WorkerCoordinator(@Value("${minsearch.workers.shutdown-timeout:25s}") Duration timeout) {
    if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Shutdown timeout must be positive");
    timeoutMillis=timeout.toMillis();
  }
  public Lease acquire() {
    synchronized (monitor) {
      if (!accepting) throw new RejectedExecutionException("Service is stopping; durable work must be retried");
      active++;
      return new Lease();
    }
  }
  public boolean stopping() { synchronized (monitor) { return !accepting; } }
  public int activeCount() { synchronized (monitor) { return active; } }
  @Override public void start() { synchronized (monitor) { accepting=true; running=true; } }
  @Override public boolean isRunning() { synchronized (monitor) { return running; } }
  @Override public int getPhase() { return Integer.MAX_VALUE - 101; }
  @Override public void onApplicationEvent(ContextClosedEvent event) { synchronized (monitor) { accepting=false; } }
  @Override public void stop() {
    long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    synchronized (monitor) {
      accepting=false;
      while (active>0) {
        long remaining=deadline-System.nanoTime();
        if (remaining<=0) break;
        try { java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(monitor, remaining); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
      }
      running=false;
    }
  }
  public final class Lease implements AutoCloseable {
    private boolean closed;
    @Override public void close() { synchronized (monitor) {
      if (!closed) { closed=true; active--; monitor.notifyAll(); }
    }}
  }
}
