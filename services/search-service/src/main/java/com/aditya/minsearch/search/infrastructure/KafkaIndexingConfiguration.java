package com.aditya.minsearch.search.infrastructure;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "minsearch.messaging.enabled", havingValue = "true")
public class KafkaIndexingConfiguration {
  private io.micrometer.core.instrument.MeterRegistry metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
  @org.springframework.beans.factory.annotation.Autowired
  void metrics(io.micrometer.core.instrument.MeterRegistry metrics) { this.metrics=metrics; }
  @Bean
  DefaultErrorHandler indexingErrorHandler(
      KafkaTemplate<String, String> kafka, IndexingFailureRecorder failures) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafka,
            (record, failure) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    var handler = new DefaultErrorHandler(
        (record, failure) -> {
          recoverer.accept(record, failure);
          failures.record(record, failure);
          metrics.counter("minsearch.index.deadlettered").increment();
        },
        new FixedBackOff(1000, 2));
    handler.setRetryListeners((record, failure, attempt) -> {
      if (attempt > 1) metrics.counter("minsearch.index.retries").increment();
    });
    return handler;
  }
}
