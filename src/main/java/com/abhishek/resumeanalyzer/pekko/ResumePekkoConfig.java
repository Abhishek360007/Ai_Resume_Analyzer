package com.abhishek.resumeanalyzer.pekko;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configures the Apache Pekko {@link ActorSystem} and {@link Materializer} used by the
 * resume processing pipeline. Active only when app.pekko.enabled=true (default).
 *
 * <p>The actor system is created with a dedicated worker dispatcher sized to the configured
 * worker count, and logs through the SLF4J bridge so all Pekko logs follow the application
 * logging configuration.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.pekko.enabled", havingValue = "true", matchIfMissing = true)
public class ResumePekkoConfig {

    @Value("${app.pekko.actor-system-name:resume-analyzer}")
    private String actorSystemName;

    @Value("${app.pekko.worker-count:5}")
    private int workerCount;

    @Bean(destroyMethod = "terminate")
    public ActorSystem<Void> resumeActorSystem() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("pekko.actor.default-dispatcher.fork-join-executor.parallelism-min", workerCount);
        settings.put("pekko.actor.default-dispatcher.fork-join-executor.parallelism-max", Math.max(workerCount, 8));
        settings.put("pekko.actor.worker-dispatcher.type", "Dispatcher");
        settings.put("pekko.actor.worker-dispatcher.executor", "thread-pool-executor");
        settings.put("pekko.actor.worker-dispatcher.thread-pool-executor.fixed-pool-size", workerCount);
        settings.put("pekko.actor.worker-dispatcher.throughput", 1);
        settings.put("pekko.loggers", List.of("org.apache.pekko.event.slf4j.Slf4jLogger"));
        settings.put("pekko.loglevel", "INFO");
        settings.put("pekko.stdout-loglevel", "INFO");

        Config config = ConfigFactory.parseMap(settings).withFallback(ConfigFactory.load());
        log.info("Creating Pekko ActorSystem '{}' with {} worker actors", actorSystemName, workerCount);
        return ActorSystem.create(Behaviors.empty(), actorSystemName, config);
    }

    @Bean
    public Materializer resumeMaterializer(ActorSystem<Void> resumeActorSystem) {
        return SystemMaterializer.get(resumeActorSystem).materializer();
    }
}
