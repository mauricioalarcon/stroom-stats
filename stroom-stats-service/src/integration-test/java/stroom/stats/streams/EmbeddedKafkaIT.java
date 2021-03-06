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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import stroom.stats.test.KafkaEmbededUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddedKafkaIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedKafkaIT.class);
    private static final String STREAMS_APP_ID = "TestStreamsApp";
    public static final String TOPIC_MESSAGES = "messages";
    public static final String TOPIC_MESSAGES_MAPPED = "messages-mapped";
    public static final String TOPIC_BRANCH_1 = "branch-1";
    public static final String TOPIC_BRANCH_2 = "branch-2";

    @Rule
    public KafkaEmbedded kafkaEmbedded = new KafkaEmbedded(1, true);

    /**
     * Put some items on the queue and make sure they can be consumed
     */
    @Test
    public void noddyProducerConsumerTest() throws ExecutionException, InterruptedException {

        String[] topics = {TOPIC_MESSAGES};
        KafkaEmbededUtils.createTopics(kafkaEmbedded, topics);

        Map<String, Object> senderProps = KafkaTestUtils.producerProps(kafkaEmbedded);
        KafkaProducer<Integer, String> producer = new KafkaProducer<>(senderProps);
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 0, "message0")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 1, "message1")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 2, "message2")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 3, "message3")).get();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("noddyProducerConsumerTest-consumer",
                "false",
                kafkaEmbedded);
        //earliest ensures we can start the consumer at any point without it missing messages
        consumerProps.put("auto.offset.reset", "earliest");

        final CountDownLatch latch = new CountDownLatch(4);
        final CountDownLatch consumerShutdownLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            KafkaConsumer<Integer, String> kafkaConsumer = new KafkaConsumer<>(consumerProps);
            kafkaConsumer.subscribe(Collections.singletonList(TOPIC_MESSAGES));
            try {
                while (true) {
                    ConsumerRecords<Integer, String> records = kafkaConsumer.poll(100);
                    for (ConsumerRecord<Integer, String> record : records) {
                        LOGGER.info("consuming from topic = {}, partition = {}, offset = {}, key = {}, value = {}",
                                record.topic(), record.partition(), record.offset(), record.key(), record.value());
                        latch.countDown();
                    }
                    if (latch.getCount() == 0) {
                        break;
                    }
                }
            } finally {
                kafkaConsumer.close();
                consumerShutdownLatch.countDown();
            }
        });

        assertThat(latch.await(90, TimeUnit.SECONDS)).isTrue();
        consumerShutdownLatch.await();
        producer.close();

        KafkaEmbededUtils.deleteTopics(kafkaEmbedded, topics);
    }


    /**
     * Put some items on the queue and make sure they can be processed by kafka streams
     */
    @Test
    public void noddyStreamsTest() throws ExecutionException, InterruptedException, IOException {

        String[] topics = {TOPIC_MESSAGES, TOPIC_MESSAGES_MAPPED};
        KafkaEmbededUtils.createTopics(kafkaEmbedded, topics);

        Map<String, Object> senderProps = KafkaTestUtils.producerProps(kafkaEmbedded);
        KafkaProducer<Integer, String> producer = new KafkaProducer<>(senderProps);
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 0, "message0")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 1, "message1")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 2, "message2")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 3, "message3")).get();

        Map<String, Object> streamProps = KafkaTestUtils.consumerProps("dummyGroup", "false", kafkaEmbedded);
        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "noddyStreamsTest");
        streamProps.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamProps.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        //We are trying to make a streams config with consumer config props, so need to remove some that
        //streams does not like.
        streamProps.remove(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);

        StreamsConfig streamsConfig = new StreamsConfig(streamProps);

        KStreamBuilder builder = new KStreamBuilder();
        KStream<String, String> kafkaInput = builder.stream(TOPIC_MESSAGES);
        kafkaInput
                .mapValues(value -> value + "-mapped")
                .to(Serdes.String(), Serdes.String(), TOPIC_MESSAGES_MAPPED);

        KafkaStreams streams = new KafkaStreams(builder, streamsConfig);
        streams.start();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("noddyStreamsTest-consumer", "false", kafkaEmbedded);
        consumerProps.put("auto.offset.reset", "earliest");

        final CountDownLatch latch = new CountDownLatch(4);
        final CountDownLatch consumerShutdownLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {

            KafkaConsumer<Integer, String> kafkaConsumer = new KafkaConsumer<>(consumerProps);
            kafkaConsumer.subscribe(Collections.singletonList(TOPIC_MESSAGES_MAPPED));
            try {
                while (true) {
                    ConsumerRecords<Integer, String> records = kafkaConsumer.poll(100);
                    for (ConsumerRecord<Integer, String> record : records) {
                        LOGGER.info("consuming from topic = {}, partition = {}, offset = {}, key = {}, value = {}",
                                record.topic(), record.partition(), record.offset(), record.key(), record.value());
                        latch.countDown();
                    }
                    if (latch.getCount() == 0) {
                        break;
                    }
                }
            } finally {
                kafkaConsumer.close();
                consumerShutdownLatch.countDown();
            }
        });

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        consumerShutdownLatch.await();
        producer.close();
        streams.close();

        KafkaEmbededUtils.deleteTopics(kafkaEmbedded, topics);
    }

    @Test
    public void transformerStreamsTest() throws ExecutionException, InterruptedException, IOException {

        String[] topics = {TOPIC_MESSAGES, TOPIC_MESSAGES_MAPPED};
        KafkaEmbededUtils.createTopics(kafkaEmbedded, topics);

        Serde<Integer> intSerde = Serdes.Integer();
        Serde<Long> longSerde = Serdes.Long();
        Serde<String> stringSerde = Serdes.String();

        Map<String, Object> senderProps = KafkaTestUtils.producerProps(kafkaEmbedded);
        KafkaProducer<Integer, Long> producer = new KafkaProducer<>(senderProps, intSerde.serializer(), longSerde.serializer());
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 0, 10L)).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 1, 10L)).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 2, 10L)).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 3, 10L)).get();

        Map<String, Object> streamProps = KafkaTestUtils.consumerProps("dummyGroup", "false", kafkaEmbedded);
        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "transformerStreamsTest");
        streamProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        //We are trying to make a streams config with consumer config props, so need to remove some that
        //streams does not like.
        streamProps.remove(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);

        StreamsConfig streamsConfig = new StreamsConfig(streamProps);

        KStreamBuilder builder = new KStreamBuilder();
        KStream<Integer, Long> kafkaInput = builder.stream(intSerde, longSerde, TOPIC_MESSAGES);
        kafkaInput
                .mapValues(value -> value + "-mapped")
                .to(intSerde, stringSerde, TOPIC_MESSAGES_MAPPED);

        KafkaStreams streams = new KafkaStreams(builder, streamsConfig);
        streams.start();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("transformerStreamsTest-consumer", "false", kafkaEmbedded);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        final CountDownLatch latch = new CountDownLatch(4);
        final CountDownLatch consumerShutdownLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {

            KafkaConsumer<Integer, String> kafkaConsumer = new KafkaConsumer<>(consumerProps, intSerde.deserializer(), stringSerde.deserializer());
            kafkaConsumer.subscribe(Collections.singletonList(TOPIC_MESSAGES_MAPPED));
            try {
                while (true) {
                    ConsumerRecords<Integer, String> records = kafkaConsumer.poll(100);
                    for (ConsumerRecord<Integer, String> record : records) {
                        LOGGER.info("consuming from topic = {}, partition = {}, offset = {}, key = {}, value = {}",
                                record.topic(), record.partition(), record.offset(), record.key(), record.value());
                        latch.countDown();
                    }
                    if (latch.getCount() == 0) {
                        break;
                    }
                }
            } finally {
                kafkaConsumer.close();
                consumerShutdownLatch.countDown();
            }
        });

        assertThat(latch.await(90, TimeUnit.SECONDS)).isTrue();

        consumerShutdownLatch.await();
        producer.close();
        streams.close();

        KafkaEmbededUtils.deleteTopics(kafkaEmbedded, topics);
    }

    /**
     * Put some items on the queue and make sure they can be processed by kafka streams
     */
    @Test
    public void branchingStreamsTest() throws ExecutionException, InterruptedException {

        final String[] topics = {TOPIC_MESSAGES, TOPIC_MESSAGES_MAPPED, TOPIC_BRANCH_1, TOPIC_BRANCH_2};
        KafkaEmbededUtils.createTopics(kafkaEmbedded, topics);

        Map<String, Object> senderProps = KafkaTestUtils.producerProps(kafkaEmbedded);
        KafkaProducer<Integer, String> producer = new KafkaProducer<>(senderProps);
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 0, "message0")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 0, 1, "message1")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 2, "message2")).get();
        producer.send(new ProducerRecord<>(TOPIC_MESSAGES, 1, 3, "message3")).get();

        Map<String, Object> streamProps = KafkaTestUtils.consumerProps("dummyGroup", "false", kafkaEmbedded);
        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "branchingStreamsTest");
        streamProps.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
        streamProps.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        //We are trying to make a streams config with consumer config props, so need to remove some that
        //streams does not like.
        streamProps.remove(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);

        StreamsConfig streamsConfig = new StreamsConfig(streamProps);

        KStreamBuilder builder = new KStreamBuilder();
        KStream<Integer, String> kafkaInput = builder.stream(TOPIC_MESSAGES);

        //branch odds and evens to different topics
        KStream<Integer, String>[] branchedStreams = kafkaInput
                .mapValues(value -> value + "-mapped")
                .through(Serdes.Integer(), Serdes.String(), TOPIC_MESSAGES_MAPPED)
                .branch(
                        (key, value) -> key % 2 == 0,
                        (key, value) -> key % 2 != 0
                );

        branchedStreams[0].to(TOPIC_BRANCH_1);
        branchedStreams[1].to(TOPIC_BRANCH_2);

        KafkaStreams streams = new KafkaStreams(builder, streamsConfig);
        streams.start();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("branchingStreamsTest-consumer", "false", kafkaEmbedded);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        final CountDownLatch latch = new CountDownLatch(12);
        final CountDownLatch consumerShutdownLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {

            KafkaConsumer<Integer, String> kafkaConsumer = new KafkaConsumer<>(consumerProps);
            kafkaConsumer.subscribe(Arrays.asList(topics));
            try {
                while (true) {
                    ConsumerRecords<Integer, String> records = kafkaConsumer.poll(100);
                    for (ConsumerRecord<Integer, String> record : records) {
                        LOGGER.info("consuming from topic = {}, partition = {}, offset = {}, key = {}, value = {}",
                                record.topic(), record.partition(), record.offset(), record.key(), record.value());
                        latch.countDown();
                    }
                    if (latch.getCount() == 0) {
                        break;
                    }
                }
            } finally {
                kafkaConsumer.close();
                consumerShutdownLatch.countDown();
            }
        });

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        consumerShutdownLatch.await();
        producer.close();
        streams.close();

        KafkaEmbededUtils.deleteTopics(kafkaEmbedded, topics);
    }

    /**
     * Start a consumer that subscribes to all embeddedKafka topics to help with debugging.
     * Dumps out the key/msg as byte arrays given that the object types may vary
     */
    private void startAllTopicsConsumer(Map<String, Object> consumerProps) throws InterruptedException {

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            KafkaConsumer<byte[], byte[]> kafkaConsumer = new KafkaConsumer<>(consumerProps,
                    Serdes.ByteArray().deserializer(),
                    Serdes.ByteArray().deserializer());
            try {
                kafkaEmbedded.consumeFromAllEmbeddedTopics(kafkaConsumer);
            } catch (Exception e) {
                throw new RuntimeException(String.format("Error subscribing to all embedded topics"), e);
            }

            try {
                while (true) {
                    ConsumerRecords<byte[], byte[]> records = kafkaConsumer.poll(100);
//                    ConsumerRecords<StatEventKey, StatAggregate> records = kafkaConsumer.poll(100);
//                    for (ConsumerRecord<StatEventKey, StatAggregate> record : records) {
                    for (ConsumerRecord<byte[], byte[]> record : records) {
                        LOGGER.info("consuming from topic = {}, partition = {}, offset = {}, key = {}, value = {}",
                                record.topic(), record.partition(), record.offset(), record.key(), record.value());
                    }
                }
            } finally {
                kafkaConsumer.close();
            }
        });
    }

}