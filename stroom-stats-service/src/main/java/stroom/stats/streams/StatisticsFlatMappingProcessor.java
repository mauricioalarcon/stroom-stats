/*
 * Copyright 2017 Crown Copyright
 *
 * This file is part of Stroom-Stats.
 *
 * Stroom-Stats is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Stroom-Stats is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Stroom-Stats.  If not, see <http://www.gnu.org/licenses/>.
 */

package stroom.stats.streams;

import com.codahale.metrics.health.HealthCheck;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.stats.StatisticsProcessor;
import stroom.stats.api.StatisticType;
import stroom.stats.properties.StroomPropertyService;
import stroom.stats.streams.mapping.AbstractStatisticFlatMapper;
import stroom.stats.util.HasRunState;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class StatisticsFlatMappingProcessor implements StatisticsProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsFlatMappingProcessor.class);

    public static final String PROP_KEY_FLAT_MAP_PROCESSOR_APP_ID_PREFIX = StatisticsIngestService.PROP_KEY_PREFIX_STATS_STREAMS +
            "flatMapProcessorAppIdPrefix";

    private final StroomPropertyService stroomPropertyService;
    private final StatisticsFlatMappingStreamFactory statisticsFlatMappingStreamFactory;
    private final StatisticType statisticType;
    private volatile KafkaStreams kafkaStreams;
    private volatile int streamThreads = 0;
    private final String appId;
    private final String inputTopic;
    private final String badEventTopic;
    private final String permsTopicsPrefix;
    private final AbstractStatisticFlatMapper mapper;
    private volatile HasRunState.RunState runState = HasRunState.RunState.STOPPED;

    //used for thread synchronization
    private final Object startStopMonitor = new Object();

    public StatisticsFlatMappingProcessor(final StroomPropertyService stroomPropertyService,
                                          final StatisticsFlatMappingStreamFactory statisticsFlatMappingStreamFactory,
                                          final StatisticType statisticType,
                                          final AbstractStatisticFlatMapper mapper) {

        this.stroomPropertyService = stroomPropertyService;
        this.statisticsFlatMappingStreamFactory = statisticsFlatMappingStreamFactory;
        this.statisticType = statisticType;
        this.mapper = mapper;

        appId = getName(PROP_KEY_FLAT_MAP_PROCESSOR_APP_ID_PREFIX, statisticType);
        LOGGER.info("Building flat mapping processor {}", appId);

        inputTopic = getName(StatisticsIngestService.PROP_KEY_STATISTIC_EVENTS_TOPIC_PREFIX, statisticType);
        badEventTopic = getName(StatisticsIngestService.PROP_KEY_BAD_STATISTIC_EVENTS_TOPIC_PREFIX, statisticType);
        permsTopicsPrefix = getName(StatisticsIngestService.PROP_KEY_STATISTIC_ROLLUP_PERMS_TOPIC_PREFIX, statisticType);

    }

    private KafkaStreams configureStream(final StatisticType statisticType,
                                         final AbstractStatisticFlatMapper mapper) {

        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);

        //TODO need to specify number of threads in the yml as it could be box specific
        streamThreads = getStreamThreads();
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, streamThreads);

        StreamsConfig streamsConfig = buildStreamsConfig(appId, props);

        KafkaStreams flatMapProcessor = statisticsFlatMappingStreamFactory.buildStream(
                streamsConfig,
                inputTopic,
                badEventTopic,
                permsTopicsPrefix,
                mapper);

        flatMapProcessor.setUncaughtExceptionHandler(buildUncaughtExceptionHandler(appId, statisticType, mapper));

        return flatMapProcessor;
    }

    private StreamsConfig buildStreamsConfig(String appId, final Map<String, Object> additionalProps) {
        Map<String, Object> props = new HashMap<>();

        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBootstrapServers());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, getStreamsCommitIntervalMs());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, getAutoOffsetReset());

        //TODO not clear if this is needed for not. Normal Kafka doesn't need it but streams may do
        //leaving it in seems to cause zookeeper connection warnings in the tests.  Tests seem to work ok
        //without it
//        props.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG, zookeeperConfig.getQuorum());

        //Add any additional props, overwriting any from above
        props.putAll(additionalProps);

        props.forEach((key, value) ->
                LOGGER.info("Setting Kafka Streams property {} for appId {} to [{}]", key, appId, value.toString())
        );

        return new StreamsConfig(props);
    }

    private Thread.UncaughtExceptionHandler buildUncaughtExceptionHandler(final String appId,
                                                                          final StatisticType statisticType,
                                                                          final AbstractStatisticFlatMapper abstractStatisticFlatMapper) {
        return (t, e) ->
                LOGGER.error("Uncaught exception in stream processor with appId {} type {} and mapper {} in thread {}",
                        appId,
                        statisticType,
                        abstractStatisticFlatMapper.getClass().getSimpleName(),
                        t.getName(),
                        e);
    }

    private String getName(final String propKey, final StatisticType statisticType) {
        String prefix = stroomPropertyService.getPropertyOrThrow(propKey);
        return TopicNameFactory.getStatisticTypedName(prefix, statisticType);
    }

    private int getStreamThreads() {
        return stroomPropertyService.getIntProperty(StatisticsIngestService.PROP_KEY_KAFKA_STREAM_THREADS, 1);
    }

    private long getStreamsCommitIntervalMs() {
        return stroomPropertyService.getLongProperty(StatisticsIngestService.PROP_KEY_KAFKA_COMMIT_INTERVAL_MS, 30_000);
    }

    private String getKafkaBootstrapServers() {
        return stroomPropertyService.getPropertyOrThrow(StatisticsIngestService.PROP_KEY_KAFKA_BOOTSTRAP_SERVERS);
    }

    private String getAutoOffsetReset() {
        return stroomPropertyService.getProperty(StatisticsIngestService.PROP_KEY_KAFKA_AUTO_OFFSET_RESET, "latest");
    }

    @Override
    public void stop() {
        synchronized (startStopMonitor) {
            runState = RunState.STOPPING;
            Instant startTime = Instant.now();
            if (kafkaStreams != null) {
                kafkaStreams.close();
                //kstream will be recreated on start allowing for different configuration
                kafkaStreams = null;
            }
            runState = RunState.STOPPED;
            LOGGER.info("Stopped processor {} for input topic {} in {}s",
                    appId,
                    inputTopic,
                    Duration.between(startTime, Instant.now()).getSeconds());
        }
    }

    @Override
    public void start() {
        synchronized (startStopMonitor) {
            runState = RunState.STARTING;
            kafkaStreams = configureStream(statisticType, mapper);
            kafkaStreams.start();
            runState = RunState.RUNNING;
            LOGGER.info("Started processor {} for input topic {} with {} stream threads", appId, inputTopic, getStreamThreads());
        }
    }

    @Override
    public RunState getRunState() {
        return runState;
    }

    @Override
    public String getGroupId() {
        return appId;
    }

    @Override
    public String getName() {
        return "AggregationProcessor-" + appId;
    }

    @Override
    public HealthCheck.Result getHealth() {
        switch (runState) {
            case RUNNING:
                return HealthCheck.Result.healthy(runState.toString());
            default:
                return HealthCheck.Result.unhealthy(runState.toString());
        }
    }

    public Map<String, String> produceHealthCheckSummary() {
        Map<String, String> statusMap = new TreeMap<>();
        statusMap.put("applicationId", appId);
        statusMap.put("badEventTopic", badEventTopic);
        statusMap.put("inputTopic", inputTopic);
        statusMap.put("permsTopicsPrefix", permsTopicsPrefix);
        statusMap.put("runState", runState.name());
        statusMap.put("streamThreads", Integer.toString(streamThreads));
        return statusMap;
    }
}
