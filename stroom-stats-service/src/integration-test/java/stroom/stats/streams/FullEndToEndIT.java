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

import com.google.inject.Injector;
import javaslang.Tuple2;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.hibernate.SessionFactory;
import org.junit.Test;
import stroom.stats.AbstractAppIT;
import stroom.stats.api.StatisticType;
import stroom.stats.configuration.StatisticConfigurationEntity;
import stroom.stats.configuration.StatisticRollUpType;
import stroom.stats.configuration.marshaller.StatisticConfigurationEntityMarshaller;
import stroom.stats.hbase.HBaseStatisticConstants;
import stroom.stats.properties.StroomPropertyService;
import stroom.stats.schema.Statistics;
import stroom.stats.schema.TagType;
import stroom.stats.shared.EventStoreTimeIntervalEnum;
import stroom.stats.test.StatisticConfigurationEntityBuilder;
import stroom.stats.test.StatisticConfigurationEntityHelper;
import stroom.stats.test.StatisticsHelper;
import stroom.stats.util.logging.LambdaLogger;
import stroom.stats.xml.StatisticsMarshaller;
import stroom.util.thread.ThreadUtil;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FullEndToEndIT extends AbstractAppIT {

    private static final LambdaLogger LOGGER = LambdaLogger.getLogger(FullEndToEndIT.class);

    private Injector injector = getApp().getInjector();
    private SessionFactory sessionFactory = injector.getInstance(SessionFactory.class);
    private StatisticConfigurationEntityMarshaller statisticConfigurationEntityMarshaller = injector.getInstance(StatisticConfigurationEntityMarshaller.class);
    private StroomPropertyService stroomPropertyService = injector.getInstance(StroomPropertyService.class);

    private final static String TAG_ENV = "environment";
    private final static String VAL_ENV_OPS = "OPS";
    private final static String VAL_ENV_DEV = "DEV";
    private final static String TAG_SYSTEM = "system";
    private final static String VAL_SYSTEM_ABC = "SystemABC";
    private final static String VAL_SYSTEM_XZY = "SystemXYZ";

    private final static List<Tuple2<String, String>> VALUE_PAIRS = Arrays.asList(
            new Tuple2<>(VAL_ENV_OPS, VAL_SYSTEM_ABC),
            new Tuple2<>(VAL_ENV_OPS, VAL_SYSTEM_XZY),
            new Tuple2<>(VAL_ENV_DEV, VAL_SYSTEM_ABC),
            new Tuple2<>(VAL_ENV_DEV, VAL_SYSTEM_XZY));
    private final static int STATS_IN_BATCH = 10;
    private final static int TIME_DELTA_MS = 250;
    private final static long MAX_ITERATIONS = 8;


    @Test
    public void testAllTypesAndIntervals() {
        //start at the beginning of today
        ZonedDateTime startTime = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LOGGER.info("Start time is {}", startTime);

        configure(stroomPropertyService);
        Map<Tuple2<StatisticType, EventStoreTimeIntervalEnum>, String> statNameMap =
                buildStatNameMap(stroomPropertyService, sessionFactory, statisticConfigurationEntityMarshaller);

        Map<String, List<Statistics>> statistics = buildStatistics(
                statNameMap,
                stroomPropertyService.getPropertyOrThrow(KafkaStreamService.PROP_KEY_STATISTIC_EVENTS_TOPIC_PREFIX),
                startTime);


        sendStatistics(
                buildKafkaProducer(stroomPropertyService),
                statistics,
                injector.getInstance(StatisticsMarshaller.class));

        ThreadUtil.sleep(60_000);

    }

    private static Map<String, List<Statistics>> buildStatistics(Map<Tuple2<StatisticType, EventStoreTimeIntervalEnum>, String> statNameMap, String topicPrefix, ZonedDateTime startTime) {
        Map<String, List<Statistics>> topicToStatistics = new HashMap<>();
        final AtomicLong counter = new AtomicLong(0);
        List<Statistics.Statistic> statList = new ArrayList<>();

        statNameMap.entrySet().forEach(entry -> {
            String statName = entry.getValue();
            StatisticType statisticType = entry.getKey()._1();
            EventStoreTimeIntervalEnum interval = entry.getKey()._2();

            LOGGER.info("Processing {} - {}", statisticType, interval);

            String topic = TopicNameFactory.getStatisticTypedName(topicPrefix, statisticType);


            while (counter.get() < MAX_ITERATIONS) {
                ZonedDateTime time = startTime.plus(TIME_DELTA_MS * counter.get(), ChronoUnit.MILLIS);

                Tuple2<String, String> valuePair = VALUE_PAIRS.get((int) (counter.get() % VALUE_PAIRS.size()));

                TagType tagTypeEnv = StatisticsHelper.buildTagType(TAG_ENV, valuePair._1());
                TagType tagTypeSystem = StatisticsHelper.buildTagType(TAG_SYSTEM, valuePair._2());
                Statistics.Statistic statistic;
                if (statisticType.equals(StatisticType.COUNT)) {
                    statistic = StatisticsHelper.buildCountStatistic(statName, time, 10L, tagTypeEnv, tagTypeSystem);
                } else {
                    statistic = StatisticsHelper.buildValueStatistic(statName, time, 0.5, tagTypeEnv, tagTypeSystem);
                }

                statList.add(statistic);

                if (counter.get() != 0 && (counter.get() % STATS_IN_BATCH == 0 || counter.get() == (MAX_ITERATIONS - 1))) {
                    Statistics statistics = StatisticsHelper.buildStatistics(statList.toArray(new Statistics.Statistic[statList.size()]));
                    statList.clear();
                    if(topicToStatistics.containsKey(topic)) {
                        topicToStatistics.get(topic).add(statistics);
                    }
                    else{
                        topicToStatistics.put(topic, Arrays.asList(statistics));
                    }
                }
                counter.incrementAndGet();
            }

            //reset for the next round
            counter.set(0);
        });

        return topicToStatistics;
    }


    private static void configure(StroomPropertyService stroomPropertyService){
        //set small batch size and flush interval so we don't have to wait ages for data to come through
        stroomPropertyService.setProperty(StatisticsAggregationProcessor.PROP_KEY_AGGREGATOR_MIN_BATCH_SIZE, 10);
        stroomPropertyService.setProperty(StatisticsAggregationProcessor.PROP_KEY_AGGREGATOR_MAX_FLUSH_INTERVAL_MS, 500);
    }

    private static Map<Tuple2<StatisticType, EventStoreTimeIntervalEnum>, String> buildStatNameMap(StroomPropertyService stroomPropertyService,
                                         SessionFactory sessionFactory,
                                         StatisticConfigurationEntityMarshaller statisticConfigurationEntityMarshaller){
        Map<Tuple2<StatisticType, EventStoreTimeIntervalEnum>, String> statNameMap = new HashMap<>();

        //build a map of all the stat names and add them as StatConfig entities
        Arrays.stream(EventStoreTimeIntervalEnum.values()).forEach(interval -> {

            //make sure the purge retention periods are very large so no stats get purged
            stroomPropertyService.setProperty(
                    HBaseStatisticConstants.DATA_STORE_PURGE_INTERVALS_TO_RETAIN_PROPERTY_NAME_PREFIX +
                            interval.name().toLowerCase(), 10_000);

            Arrays.stream(StatisticType.values()).forEach(statisticType -> {
                //TODO temporary filter, remove
                if (interval.equals(EventStoreTimeIntervalEnum.SECOND)) {
//                if (interval.equals(EventStoreTimeIntervalEnum.SECOND) && statisticType.equals(StatisticType.COUNT)) {
                    String statNameStr = "FullEndToEndIT-test-" + Instant.now().toString() + "-" + statisticType + "-" + interval;
                    LOGGER.info("Creating stat name : {}", statNameStr);

                    statNameMap.put(new Tuple2(statisticType, interval), statNameStr);
                    StatisticConfigurationEntity statisticConfigurationEntity = new StatisticConfigurationEntityBuilder(
                            statNameStr,
                            statisticType,
                            interval.columnInterval(),
                            StatisticRollUpType.ALL)
                            .addFields(TAG_ENV, TAG_SYSTEM)
                            .build();

                    StatisticConfigurationEntityHelper.addStatConfig(
                            sessionFactory,
                            statisticConfigurationEntityMarshaller,
                            statisticConfigurationEntity);
                }
            });
        });

        return statNameMap;
    }

    private static KafkaProducer<String, String> buildKafkaProducer(StroomPropertyService stroomPropertyService){
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                stroomPropertyService.getPropertyOrThrow(KafkaStreamService.PROP_KEY_KAFKA_BOOTSTRAP_SERVERS));
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        producerProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 100);
        producerProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 5_000_000);

        Serde<String> stringSerde = Serdes.String();

        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(producerProps, stringSerde.serializer(), stringSerde.serializer());
        return kafkaProducer;
    }

    private static void sendStatistics(
            KafkaProducer<String, String> kafkaProducer,
            Map<String, List<Statistics>> statisticTypeToStatistics,
            StatisticsMarshaller statisticsMarshaller){
        statisticTypeToStatistics.entrySet().stream().forEach(
                entry ->entry.getValue().forEach(
                        statistics -> {
                            ProducerRecord<String, String> producerRecord = buildProducerRecord(entry.getKey(), statistics, statisticsMarshaller);

                            statistics.getStatistic().forEach(statistic ->
                                    LOGGER.trace("Sending stat with name {}, count {} and value {}", statistic.getName(), statistic.getCount(), statistic.getValue())
                            );

                            LOGGER.trace(() -> String.format("Sending %s stat events to topic $s", statistics.getStatistic().size(), entry.getKey()));
                            kafkaProducer.send(producerRecord);
                        }));

        kafkaProducer.flush();
        kafkaProducer.close();
    }

    private static ProducerRecord<String, String> buildProducerRecord(String topic, Statistics statistics, StatisticsMarshaller statisticsMarshaller) {
        String statName = statistics.getStatistic().get(0).getName();
        return new ProducerRecord<>(topic, statName, statisticsMarshaller.marshallXml(statistics));
    }

}