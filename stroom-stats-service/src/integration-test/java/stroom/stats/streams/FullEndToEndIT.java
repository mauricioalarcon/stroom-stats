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
import stroom.stats.configuration.StatisticConfiguration;
import stroom.stats.configuration.marshaller.StroomStatsStoreEntityMarshaller;
import stroom.stats.hbase.HBaseStatisticConstants;
import stroom.stats.properties.StroomPropertyService;
import stroom.stats.schema.v4.Statistics;
import stroom.stats.schema.v4.TagType;
import stroom.stats.shared.EventStoreTimeIntervalEnum;
import stroom.stats.test.StatMessage;
import stroom.stats.test.StroomStatsStoreEntityHelper;
import stroom.stats.test.StatisticsHelper;
import stroom.stats.util.logging.LambdaLogger;
import stroom.stats.schema.v4.StatisticsMarshaller;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public class FullEndToEndIT extends AbstractAppIT {
    private static final LambdaLogger LOGGER = LambdaLogger.getLogger(FullEndToEndIT.class);

    private Injector injector = getApp().getInjector();
    private StroomPropertyService stroomPropertyService = injector.getInstance(StroomPropertyService.class);

    private final static String TAG_ENV = "environment";
    private final static String TAG_SYSTEM = "system";

    @Test
    public void testAllTypesAndIntervals() throws InterruptedException {
        //start at the beginning of today
        ZonedDateTime startTime = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LOGGER.info("Start time is {}", startTime);

        configure(stroomPropertyService);
        List<StatisticConfiguration> statisticConfigurations = createDummyStatisticConfigurations();

        StroomStatsStoreEntityHelper.persistDummyStatisticConfigurations(
                statisticConfigurations,
                injector.getInstance(SessionFactory.class),
                injector.getInstance(StroomStatsStoreEntityMarshaller.class));


        List<StatMessage> statistics = createDummyStatistics(
                statisticConfigurations,
                stroomPropertyService.getPropertyOrThrow(StatisticsIngestService.PROP_KEY_STATISTIC_EVENTS_TOPIC_PREFIX),
                startTime);


        sendDummyStatistics(
                buildKafkaProducer(stroomPropertyService),
                statistics,
                injector.getInstance(StatisticsMarshaller.class));

        Thread.sleep(5_000);


        //TODO assert the data using the query api
    }

    private static void configure(StroomPropertyService stroomPropertyService){
        //make sure the purge retention periods are very large so no stats get purged
        Arrays.stream(EventStoreTimeIntervalEnum.values()).forEach(interval ->
                stroomPropertyService.setProperty(
                        HBaseStatisticConstants.DATA_STORE_PURGE_INTERVALS_TO_RETAIN_PROPERTY_NAME_PREFIX +
                        interval.name().toLowerCase(), 10_000));

        //set small batch size and flush interval so we don't have to wait ages for data to come through
        stroomPropertyService.setProperty(StatisticsAggregationProcessor.PROP_KEY_AGGREGATOR_MIN_BATCH_SIZE, 10);
        stroomPropertyService.setProperty(StatisticsAggregationProcessor.PROP_KEY_AGGREGATOR_MAX_FLUSH_INTERVAL_MS, 500);
    }

    private static List<StatisticConfiguration> createDummyStatisticConfigurations(){
        List<StatisticConfiguration> stats = new ArrayList<>();
        stats.add(StroomStatsStoreEntityHelper.createDummyStatisticConfiguration(
                "FullEndToEndIT-test-", StatisticType.COUNT, EventStoreTimeIntervalEnum.SECOND, TAG_ENV, TAG_SYSTEM));
        stats.add(StroomStatsStoreEntityHelper.createDummyStatisticConfiguration(
                "FullEndToEndIT-test-", StatisticType.VALUE, EventStoreTimeIntervalEnum.SECOND, TAG_ENV, TAG_SYSTEM));
        return stats;
    }

    private static List<StatMessage> createDummyStatistics(
            final List<StatisticConfiguration> statisticConfigurations,
            final String topicPrefix,
            final ZonedDateTime startTime) {

        final String VAL_ENV_OPS = "OPS";
        final String VAL_ENV_DEV = "DEV";
        final String VAL_SYSTEM_ABC = "SystemABC";
        final String VAL_SYSTEM_XZY = "SystemXYZ";
        final List<Tuple2<String, String>> VALUE_PAIRS = Arrays.asList(
                new Tuple2<>(VAL_ENV_OPS, VAL_SYSTEM_ABC),
                new Tuple2<>(VAL_ENV_OPS, VAL_SYSTEM_XZY),
                new Tuple2<>(VAL_ENV_DEV, VAL_SYSTEM_ABC),
                new Tuple2<>(VAL_ENV_DEV, VAL_SYSTEM_XZY));
        final int STATS_IN_BATCH = 10;
        final int TIME_DELTA_MS = 250;
        final long MAX_ITERATIONS = 8;

        final AtomicLong counter = new AtomicLong(0);
        List<Statistics.Statistic> statList = new ArrayList<>();
        List<StatMessage> statMessages = new ArrayList<>();

        statisticConfigurations.forEach(statConf -> {
            String statUuid = statConf.getUuid();
            StatisticType statisticType = statConf.getStatisticType();
            EventStoreTimeIntervalEnum interval = statConf.getPrecision();

            LOGGER.info("Processing {} - {}", statisticType, interval);

            String topic = TopicNameFactory.getStatisticTypedName(topicPrefix, statisticType);


            while (counter.get() < MAX_ITERATIONS) {
                ZonedDateTime time = startTime.plus(TIME_DELTA_MS * counter.get(), ChronoUnit.MILLIS);

                Tuple2<String, String> valuePair = VALUE_PAIRS.get((int) (counter.get() % VALUE_PAIRS.size()));

                TagType tagTypeEnv = StatisticsHelper.buildTagType(TAG_ENV, valuePair._1());
                TagType tagTypeSystem = StatisticsHelper.buildTagType(TAG_SYSTEM, valuePair._2());
                Statistics.Statistic statistic;
                if (statisticType.equals(StatisticType.COUNT)) {
                    statistic = StatisticsHelper.buildCountStatistic(time, 10L, tagTypeEnv, tagTypeSystem);
                } else {
                    statistic = StatisticsHelper.buildValueStatistic(time, 0.5, tagTypeEnv, tagTypeSystem);
                }

                statList.add(statistic);

                if (counter.get() != 0 && (counter.get() % STATS_IN_BATCH == 0 || counter.get() == (MAX_ITERATIONS - 1))) {
                    Statistics statistics = StatisticsHelper.buildStatistics(statList.toArray(new Statistics.Statistic[statList.size()]));
                    statList.clear();
                    statMessages.add(new StatMessage(topic, statConf.getUuid(), statistics));
                }
                counter.incrementAndGet();
            }

            //reset for the next round
            counter.set(0);
        });

        return statMessages;
    }

    private static void sendDummyStatistics(
            final KafkaProducer<String, String> kafkaProducer,
            final List<StatMessage> statMessages,
            final StatisticsMarshaller statisticsMarshaller){

        statMessages.forEach(statMessage -> {
            ProducerRecord<String, String> producerRecord = buildProducerRecord(
                    statMessage.getTopic(),
                    statMessage.getKey(),
                    statMessage.getStatistics(),
                    statisticsMarshaller);

            statMessage.getStatistics().getStatistic().forEach(statistic ->
                    LOGGER.trace("Sending stat with uuid {} count {} and value {}",
                            statMessage.getKey(),
                            statistic.getCount(),
                            statistic.getValue())
            );

            LOGGER.trace(() -> String.format("Sending %s stat events to topic %s",
                    statMessage.getStatistics().getStatistic().size(), statMessage.getTopic()));
            try {
                kafkaProducer.send(producerRecord).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw  new RuntimeException("Interrupted", e);
            } catch (ExecutionException e) {
                throw  new RuntimeException("Error sending record to Kafka", e);
            }

        });

        kafkaProducer.flush();
        kafkaProducer.close();
    }

    private static KafkaProducer<String, String> buildKafkaProducer(StroomPropertyService stroomPropertyService){
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                stroomPropertyService.getPropertyOrThrow(StatisticsIngestService.PROP_KEY_KAFKA_BOOTSTRAP_SERVERS));
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        producerProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 100);
        producerProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 5_000_000);

        Serde<String> stringSerde = Serdes.String();

        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(producerProps, stringSerde.serializer(), stringSerde.serializer());
        return kafkaProducer;
    }

    private static ProducerRecord<String, String> buildProducerRecord(final String topic,
                                                                      final String key,
                                                                      final Statistics statistics,
                                                                      final StatisticsMarshaller statisticsMarshaller) {
        return new ProducerRecord<>(topic, key, statisticsMarshaller.marshallToXml(statistics));
    }
}
