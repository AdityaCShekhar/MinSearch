package com.aditya.minsearch.shared.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class WorkerCoordinatorTest {
  @Test void stopsAdmissionsAndWaitsForClaimedWorkBeforeFinishing() throws Exception {
    var workers=new WorkerCoordinator(Duration.ofSeconds(3)); workers.start();
    var lease=workers.acquire();
    var executor=Executors.newSingleThreadExecutor();
    try {
      var stopped=executor.submit((Runnable) workers::stop);
      long deadline=System.nanoTime()+Duration.ofSeconds(1).toNanos();
      while (!workers.stopping() && System.nanoTime()<deadline) Thread.onSpinWait();
      assertThatThrownBy(workers::acquire).isInstanceOf(RejectedExecutionException.class);
      assertThat(stopped.isDone()).isFalse();
      lease.close(); lease.close(); stopped.get(2, TimeUnit.SECONDS);
      assertThat(workers.activeCount()).isZero(); assertThat(workers.isRunning()).isFalse();
    } finally { lease.close(); executor.shutdownNow(); }
  }
  @Test void shutdownDeadlineDoesNotWaitForeverForBrokenWorker() {
    var workers=new WorkerCoordinator(Duration.ofMillis(10)); workers.start();
    try (var lease=workers.acquire()) {
      assertThatCode(workers::stop).doesNotThrowAnyException();
      assertThat(workers.stopping()).isTrue();
    }
    assertThat(workers.activeCount()).isZero();
  }
}
