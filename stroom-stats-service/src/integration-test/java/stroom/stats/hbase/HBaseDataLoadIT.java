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

package stroom.stats.hbase;

import com.google.inject.Injector;
import javaslang.Tuple2;
import org.hibernate.SessionFactory;
import org.junit.Test;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.stats.AbstractAppIT;
import stroom.stats.api.StatisticTag;
import stroom.stats.api.StatisticType;
import stroom.stats.api.StatisticsService;
import stroom.stats.common.CountStatisticDataPoint;
import stroom.stats.common.StatisticDataPoint;
import stroom.stats.common.StatisticDataSet;
import stroom.stats.common.ValueStatisticDataPoint;
import stroom.stats.common.rollup.RollUpBitMask;
import stroom.stats.configuration.StatisticConfiguration;
import stroom.stats.configuration.StatisticConfigurationEntity;
import stroom.stats.configuration.StatisticRollUpType;
import stroom.stats.configuration.marshaller.StatisticConfigurationEntityMarshaller;
import stroom.stats.hbase.uid.UID;
import stroom.stats.hbase.uid.UniqueIdCache;
import stroom.stats.shared.EventStoreTimeIntervalEnum;
import stroom.stats.streams.StatKey;
import stroom.stats.streams.TagValue;
import stroom.stats.streams.aggregation.CountAggregate;
import stroom.stats.streams.aggregation.StatAggregate;
import stroom.stats.streams.aggregation.ValueAggregate;
import stroom.stats.test.StatisticConfigurationEntityBuilder;
import stroom.stats.test.StatisticConfigurationEntityHelper;
import stroom.stats.util.DateUtil;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Load data into HBase via the {@link StatisticsService} and then query it via the {@link StatisticsService}
 */
public class HBaseDataLoadIT extends AbstractAppIT {

    private Injector injector = getApp().getInjector();
    private UniqueIdCache uniqueIdCache = injector.getInstance(UniqueIdCache.class);
    private StatisticsService statisticsService = injector.getInstance(StatisticsService.class);
    private SessionFactory sessionFactory = injector.getInstance(SessionFactory.class);
    private StatisticConfigurationEntityMarshaller statisticConfigurationEntityMarshaller = injector.getInstance(StatisticConfigurationEntityMarshaller.class);

    private EventStoreTimeIntervalEnum interval = EventStoreTimeIntervalEnum.DAY;
    private ChronoUnit workingChronoUnit = ChronoUnit.DAYS;
    private RollUpBitMask rollUpBitMask = RollUpBitMask.ZERO_MASK;
    private final Instant time1 = ZonedDateTime.now().toInstant();
    private final Instant time2 = time1.plus(5, workingChronoUnit);

    //effectively time1, time1+5d, time1+10d, time1+15d, time1+20d
    private List<Instant> times = IntStream.rangeClosed(0, 4)
            .boxed()
            .map(i -> time1.plus(5 * i, workingChronoUnit))
            .collect(Collectors.toList());

    private List<Instant> timesTruncated = times.stream()
            .map(time -> time.truncatedTo(workingChronoUnit))
            .collect(Collectors.toList());

    private String tag1Str = "tag1";
    private String tag1Val1Str = tag1Str + "val1";
    private String tag2Str = "tag2";
    private String tag2Val1Str = tag2Str + "val1";
    private String tag2Val2Str = tag2Str + "val2";

    private UID tag1 = uniqueIdCache.getOrCreateId(tag1Str);
    private UID tag1val1 = uniqueIdCache.getOrCreateId(tag1Val1Str);
    private UID tag2 = uniqueIdCache.getOrCreateId(tag2Str);
    private UID tag2val1 = uniqueIdCache.getOrCreateId(tag2Val1Str);
    private UID tag2val2 = uniqueIdCache.getOrCreateId(tag2Val2Str);

    private ExpressionItem dateTerm = new ExpressionTerm(
            StatisticConfiguration.FIELD_NAME_DATE_TIME,
            ExpressionTerm.Condition.BETWEEN,
            String.format("%s,%s",
                    DateUtil.createNormalDateTimeString(Instant.now().minus(10, workingChronoUnit).toEpochMilli()),
                    DateUtil.createNormalDateTimeString(Instant.now().plus(10, workingChronoUnit).toEpochMilli())));

    private ExpressionItem precisionTerm = new ExpressionTerm(
            StatisticConfiguration.FIELD_NAME_PRECISION,
            ExpressionTerm.Condition.EQUALS,
            interval.toString().toLowerCase());

    private ExpressionItem tag1Term = new ExpressionTerm(
            tag1Str,
            ExpressionTerm.Condition.EQUALS,
            tag1Val1Str);

    private ExpressionItem tag2Term = new ExpressionTerm(
            tag2Str,
            ExpressionTerm.Condition.EQUALS,
            tag2Val1Str);

    private ExpressionItem tag2TermNotFound = new ExpressionTerm(
            tag2Str,
            ExpressionTerm.Condition.EQUALS,
            "ValueThatDoesn'tExist");

    /**
     * Load two COUNT stats then query using a variety of query terms to filter the data
     */
    @Test
    public void testCount() {

        StatisticType statisticType = StatisticType.COUNT;
        Map<StatKey, StatAggregate> aggregatedEvents = new HashMap<>();

        //Put time in the statName to allow us to re-run the test without an empty HBase
        String statNameStr = this.getClass().getName() + "-test-" + Instant.now().toString();

        StatisticConfigurationEntity statisticConfigurationEntity = new StatisticConfigurationEntityBuilder(
                statNameStr,
                statisticType,
                interval.columnInterval(),
                StatisticRollUpType.ALL)
                .addFields(tag1Str, tag2Str)
                .build();

        StatisticConfigurationEntityHelper.addStatConfig(
                sessionFactory,
                statisticConfigurationEntityMarshaller,
                statisticConfigurationEntity);

        UID statName = uniqueIdCache.getOrCreateId(statNameStr);
        assertThat(statName).isNotNull();

        StatKey statKey1 = new StatKey(statName,
                rollUpBitMask,
                interval,
                time1.toEpochMilli(),
                new TagValue(tag1, tag1val1),
                new TagValue(tag2, tag2val1));

        long statValue1 = 100L;

        StatAggregate statAggregate1 = new CountAggregate(statValue1);

        aggregatedEvents.put(statKey1, statAggregate1);

        StatKey statKey2 = new StatKey(statName,
                rollUpBitMask,
                interval,
                time2.toEpochMilli(),
                new TagValue(tag1, tag1val1),
                new TagValue(tag2, tag2val2));

        long statValue2 = 200L;

        StatAggregate statAggregate2 = new CountAggregate(statValue2);

        aggregatedEvents.put(statKey2, statAggregate2);

        statisticsService.putAggregatedEvents(statisticType, interval, aggregatedEvents);

        Query queryAllData = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm));

        Query querySpecificRow = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, tag1Term, tag2Term));

        Query queryNoDataFound = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, tag1Term, tag2TermNotFound));

        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, queryAllData, statisticConfigurationEntity, 2);

        //should have 3 distinct tag values as t1 is same for btoh and t2 is different for each
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str, tag2Val2Str);
        assertThat(computeSumOfCountCounts(dataPoints)).isEqualTo((statValue1) + (statValue2));

        dataPoints = runQuery(statisticsService, querySpecificRow, statisticConfigurationEntity, 1);

        //should only get two distinct tag values as we have just one row back
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str);
        assertThat(computeSumOfCountCounts(dataPoints)).isEqualTo(statValue1);

        //should get nothing back as the requested tagvalue is not in the store
        runQuery(statisticsService, queryNoDataFound, statisticConfigurationEntity, 0);

        //No put the same events again so the values should be aggregated by HBase
        statisticsService.putAggregatedEvents(statisticType, interval, aggregatedEvents);

        dataPoints = runQuery(statisticsService, queryAllData, statisticConfigurationEntity, 2);

        //should have 3 distinct tag values as t1 is same for btoh and t2 is different for each
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str, tag2Val2Str);
        assertThat(computeSumOfCountCounts(dataPoints)).isEqualTo((statValue1 * 2) + (statValue2 * 2));

        dataPoints = runQuery(statisticsService, querySpecificRow, statisticConfigurationEntity, 1);

        //should only get two distinct tag values as we have just one row back
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str);
        assertThat(computeSumOfCountCounts(dataPoints)).isEqualTo(statValue1 * 2);

        //should get nothing back as the requested tagvalue is not in the store
        runQuery(statisticsService, queryNoDataFound, statisticConfigurationEntity, 0);
    }

    /**
     * Load two VALUE stats then query using a variety of query terms to filter the data
     */
    @Test
    public void testValue() {

        StatisticType statisticType = StatisticType.VALUE;

        Map<StatKey, StatAggregate> aggregatedEvents = new HashMap<>();

        //Put time in the statName to allow us to re-run the test without an empty HBase
        String statNameStr = this.getClass().getName() + "-test-" + Instant.now().toString();

        StatisticConfigurationEntity statisticConfigurationEntity = new StatisticConfigurationEntityBuilder(
                statNameStr,
                statisticType,
                interval.columnInterval(),
                StatisticRollUpType.ALL)
                .addFields(tag1Str, tag2Str)
                .build();

        StatisticConfigurationEntityHelper.addStatConfig(
                sessionFactory,
                statisticConfigurationEntityMarshaller,
                statisticConfigurationEntity);

        UID statName = uniqueIdCache.getOrCreateId(statNameStr);
        assertThat(statName).isNotNull();

        StatKey statKey1 = new StatKey(statName,
                rollUpBitMask,
                interval,
                time1.toEpochMilli(),
                new TagValue(tag1, tag1val1),
                new TagValue(tag2, tag2val1));

        double statValue1 = 1.23;

        StatAggregate statAggregate1 = new ValueAggregate(statValue1);

        aggregatedEvents.put(statKey1, statAggregate1);

        StatKey statKey2 = new StatKey(statName,
                rollUpBitMask,
                interval,
                time2.toEpochMilli(),
                new TagValue(tag1, tag1val1),
                new TagValue(tag2, tag2val2));

        double statValue2 = 2.34;

        StatAggregate statAggregate2 = new ValueAggregate(statValue2);

        aggregatedEvents.put(statKey2, statAggregate2);

        statisticsService.putAggregatedEvents(statisticType, interval, aggregatedEvents);


        Query queryAllData = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm));

        Query querySpecificRow = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, tag1Term, tag2Term));

        Query queryNoDataFound = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, tag1Term, tag2TermNotFound));

        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, queryAllData, statisticConfigurationEntity, 2);

        //should have 3 distinct tag values as t1 is same for btoh and t2 is different for each
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str, tag2Val2Str);
        assertThat(computeSumOfCountCounts(dataPoints)).isEqualTo((statValue1) + (statValue2));

        dataPoints = runQuery(statisticsService, querySpecificRow, statisticConfigurationEntity, 1);

        //should only get two distinct tag values as we have just one row back
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str);
        assertThat(computeSumOfCountCounts(dataPoints)).isEqualTo(statValue1);

        //should get nothing back as the requested tagvalue is not in the store
        runQuery(statisticsService, queryNoDataFound, statisticConfigurationEntity, 0);

        //now put the same events again so HBase should aggregated the values in the cells
        statisticsService.putAggregatedEvents(statisticType, interval, aggregatedEvents);

        dataPoints = runQuery(statisticsService, queryAllData, statisticConfigurationEntity, 2);

        //should have 3 distinct tag values as t1 is same for btoh and t2 is different for each
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str, tag2Val2Str);
        assertThat(computeSumOfValueValues(dataPoints)).isEqualTo((statValue1) + (statValue2));
        assertThat(computeSumOfValueCounts(dataPoints)).isEqualTo(2 + 2);

        dataPoints = runQuery(statisticsService, querySpecificRow, statisticConfigurationEntity, 1);

        //should only get two distinct tag values as we have just one row back
        assertThat(dataPoints.stream().flatMap(dataPoint -> dataPoint.getTags().stream()).map(StatisticTag::getValue).distinct().collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(tag1Val1Str, tag2Val1Str);
        assertThat(computeSumOfValueValues(dataPoints)).isEqualTo(statValue1);
        assertThat(computeSumOfValueCounts(dataPoints)).isEqualTo(2);

        //should get nothing back as the requested tagvalue is not in the store
        runQuery(statisticsService, queryNoDataFound, statisticConfigurationEntity, 0);
    }

    @Test
    public void testDateHandling_missingDateTerm() {

        StatisticConfigurationEntity statisticConfigurationEntity = loadStatData(StatisticType.COUNT);

        Query query = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND));

        //all records should come back
        runQuery(statisticsService, query, statisticConfigurationEntity,times.size());
    }

    @Test
    public void testDateHandling_equals() {

        StatisticConfigurationEntity statisticConfigurationEntity = loadStatData(StatisticType.COUNT);

        //use timesTruncated as that is how they are stored in hbase
        ExpressionItem dateTerm = new ExpressionTerm(
                StatisticConfiguration.FIELD_NAME_DATE_TIME,
                ExpressionTerm.Condition.EQUALS,
                DateUtil.createNormalDateTimeString(timesTruncated.get(2).toEpochMilli()));

        Query query = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, precisionTerm));

        //only find the the one we equal
        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, query, statisticConfigurationEntity, 1);
        assertThat(dataPoints.get(0).getTimeMs()).isEqualTo(timesTruncated.get(2).toEpochMilli());
    }

    @Test
    public void testDateHandling_lessThan() {

        StatisticConfigurationEntity statisticConfigurationEntity = loadStatData(StatisticType.COUNT);

        //use timesTruncated as that is how they are stored in hbase
        ExpressionItem dateTerm = new ExpressionTerm(
                StatisticConfiguration.FIELD_NAME_DATE_TIME,
                ExpressionTerm.Condition.LESS_THAN,
                DateUtil.createNormalDateTimeString(timesTruncated.get(2).toEpochMilli()));

        Query query = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, precisionTerm));

        //should only get the two times below the middle one we have aimed for
        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, query, statisticConfigurationEntity, 2);
        assertThat(dataPoints.stream()
                        .map(point -> Instant.ofEpochMilli(point.getTimeMs()))
                        .collect(Collectors.toList()))
                .containsExactlyInAnyOrder(timesTruncated.get(0), timesTruncated.get(1));
    }

    @Test
    public void testDateHandling_lessThanEqualTo() {

        StatisticConfigurationEntity statisticConfigurationEntity = loadStatData(StatisticType.COUNT);

        //use timesTruncated as that is how they are stored in hbase
        ExpressionItem dateTerm = new ExpressionTerm(
                StatisticConfiguration.FIELD_NAME_DATE_TIME,
                ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO,
                DateUtil.createNormalDateTimeString(timesTruncated.get(2).toEpochMilli()));

        Query query = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, precisionTerm));

        //should only get the two times below the middle one we have aimed for
        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, query, statisticConfigurationEntity, 3);
        assertThat(dataPoints.stream()
                .map(point -> Instant.ofEpochMilli(point.getTimeMs()))
                .collect(Collectors.toList()))
                .containsExactlyInAnyOrder(timesTruncated.get(0), timesTruncated.get(1), timesTruncated.get(2));
    }

    @Test
    public void testDateHandling_greaterThan() {

        StatisticConfigurationEntity statisticConfigurationEntity = loadStatData(StatisticType.COUNT);

        //truncate the original stat time down to the interval so we can find it with an EQUALS
        ExpressionItem dateTerm = new ExpressionTerm(
                StatisticConfiguration.FIELD_NAME_DATE_TIME,
                ExpressionTerm.Condition.GREATER_THAN,
                DateUtil.createNormalDateTimeString(timesTruncated.get(2).toEpochMilli()));

        Query query = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, precisionTerm));

        //should only get the two times below the middle one we have aimed for
        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, query, statisticConfigurationEntity, 2);
        assertThat(dataPoints.stream()
                .map(point -> Instant.ofEpochMilli(point.getTimeMs()))
                .collect(Collectors.toList()))
                .containsExactlyInAnyOrder(timesTruncated.get(3), timesTruncated.get(4));
    }

    @Test
    public void testDateHandling_greaterThanEqualTo() {

        StatisticConfigurationEntity statisticConfigurationEntity = loadStatData(StatisticType.COUNT);

        //use timesTruncated as that is how they are stored in hbase
        ExpressionItem dateTerm = new ExpressionTerm(
                StatisticConfiguration.FIELD_NAME_DATE_TIME,
                ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO,
                DateUtil.createNormalDateTimeString(timesTruncated.get(2).toEpochMilli()));

        Query query = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, precisionTerm));

        //should only get the two times below the middle one we have aimed for
        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, query, statisticConfigurationEntity, 3);
        assertThat(dataPoints.stream()
                .map(point -> Instant.ofEpochMilli(point.getTimeMs()))
                .collect(Collectors.toList()))
                .containsExactlyInAnyOrder(timesTruncated.get(2), timesTruncated.get(3), timesTruncated.get(4));
    }

    @Test
    public void testDateHandling_between() {

        StatisticConfigurationEntity statisticConfigurationEntity = loadStatData(StatisticType.COUNT);

        //use timesTruncated as that is how they are stored in hbase
        ExpressionItem dateTerm = new ExpressionTerm(
                StatisticConfiguration.FIELD_NAME_DATE_TIME,
                ExpressionTerm.Condition.BETWEEN,
                String.format("%s,%s",
                        DateUtil.createNormalDateTimeString(timesTruncated.get(1).toEpochMilli()),
                        DateUtil.createNormalDateTimeString(timesTruncated.get(3).toEpochMilli())));

        Query query = new Query(
                new DocRef(StatisticConfigurationEntity.ENTITY_TYPE, statisticConfigurationEntity.getUuid()),
                new ExpressionOperator(true, ExpressionOperator.Op.AND, dateTerm, precisionTerm));

        //should only get the two times below the middle one we have aimed for
        List<StatisticDataPoint> dataPoints = runQuery(statisticsService, query, statisticConfigurationEntity, 3);
        assertThat(dataPoints.stream()
                .map(point -> Instant.ofEpochMilli(point.getTimeMs()))
                .collect(Collectors.toList()))
                .containsExactlyInAnyOrder(timesTruncated.get(1), timesTruncated.get(2), timesTruncated.get(3));
    }

    private StatisticConfigurationEntity createStatConfEntity(final String statNameStr,
                                                              final StatisticType statisticType) {
        StatisticConfigurationEntity statisticConfigurationEntity = new StatisticConfigurationEntityBuilder(
                statNameStr,
                statisticType,
                interval.columnInterval(),
                StatisticRollUpType.ALL)
                .addFields(tag1Str, tag2Str)
                .build();

        StatisticConfigurationEntityHelper.addStatConfig(
                sessionFactory,
                statisticConfigurationEntityMarshaller,
                statisticConfigurationEntity);


        return statisticConfigurationEntity;

    }

    private StatisticConfigurationEntity loadStatData(final StatisticType statisticType) {
        //Put time in the statName to allow us to re-run the test without an empty HBase
        String statNameBase = this.getClass().getName() + "-test-" + Instant.now().toString();
        List<StatisticConfigurationEntity> entities = new ArrayList<>();

        //create stats for multiple stat names to make sure we are not picking up other records
        Arrays.asList("A", "B", "C").forEach(postFix -> {

            String statNameStr = statNameBase + "A";
            StatisticConfigurationEntity statisticConfigurationEntity = createStatConfEntity(statNameStr, statisticType);
            entities.add(statisticConfigurationEntity);

            UID statName = uniqueIdCache.getOrCreateId(statNameStr);
            assertThat(statName).isNotNull();

            Map<StatKey, StatAggregate> aggregatedEvents = times.stream()
                    .map(time -> {
                        StatKey statKey = new StatKey(statName,
                                rollUpBitMask,
                                interval,
                                time.toEpochMilli(),
                                new TagValue(tag1, tag1val1),
                                new TagValue(tag2, tag2val1));

                        StatAggregate statAggregate = new CountAggregate(100L);
                        return new Tuple2<>(statKey, statAggregate);
                    })
                    .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

            assertThat(aggregatedEvents).hasSize(times.size());

            statisticsService.putAggregatedEvents(statisticType, interval, aggregatedEvents);
        });

        //return the middle entity
        return entities.get(1);
    }

    private List<StatisticDataPoint> runQuery(
            StatisticsService statisticsService,
            Query query,
            StatisticConfigurationEntity statisticConfigurationEntity,
            int expectedRecCount) {
        StatisticDataSet statisticDataSet = statisticsService.searchStatisticsData(wrapQuery(query), statisticConfigurationEntity);

        assertThat(statisticDataSet).isNotNull();
        assertThat(statisticDataSet).size().isEqualTo(expectedRecCount);
        return statisticDataSet.getStatisticDataPoints();
    }


    private SearchRequest wrapQuery(Query query) {
        return new SearchRequest(null, query, Collections.emptyList(), ZoneOffset.UTC.getId(), false);
    }

    private static long computeSumOfCountCounts(List<StatisticDataPoint> dataPoints) {

        return dataPoints.stream()
                .map(point -> (CountStatisticDataPoint)point)
                .map(CountStatisticDataPoint::getCount)
                .mapToLong(Long::longValue)
                .sum();
    }

    private static long computeSumOfValueCounts(List<StatisticDataPoint> dataPoints) {

        return dataPoints.stream()
                .map(point -> (ValueStatisticDataPoint)point)
                .map(ValueStatisticDataPoint::getCount)
                .mapToLong(Long::longValue)
                .sum();
    }

    private static double computeSumOfValueValues(List<StatisticDataPoint> dataPoints) {

        return dataPoints.stream()
                .map(point -> (ValueStatisticDataPoint)point)
                .map(ValueStatisticDataPoint::getValue)
                .mapToDouble(Double::doubleValue)
                .sum();
    }


}
