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

package stroom.stats.streams.mapping;

import com.google.common.base.Preconditions;
import org.apache.kafka.streams.KeyValue;
import stroom.stats.api.MultiPartIdentifier;
import stroom.stats.hbase.uid.UniqueIdCache;
import stroom.stats.properties.StroomPropertyService;
import stroom.stats.schema.v4.Statistics;
import stroom.stats.streams.StatEventKey;
import stroom.stats.streams.StatisticWrapper;
import stroom.stats.streams.aggregation.CountAggregate;
import stroom.stats.streams.aggregation.StatAggregate;
import stroom.stats.util.logging.LambdaLogger;

import javax.inject.Inject;
import java.util.List;

public class CountStatToAggregateFlatMapper extends AbstractStatisticFlatMapper {

    private static final LambdaLogger LOGGER = LambdaLogger.getLogger(CountStatToAggregateFlatMapper.class);

    private final UniqueIdCache uniqueIdCache;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    public CountStatToAggregateFlatMapper(UniqueIdCache uniqueIdCache,
                                          StroomPropertyService stroomPropertyService) {
        super(uniqueIdCache, stroomPropertyService);
        LOGGER.debug("Initialising {}", this.getClass().getCanonicalName());

        this.uniqueIdCache = uniqueIdCache;
        this.stroomPropertyService = stroomPropertyService;
    }

    /**
     * Convert the Statistic object into a StatEventKey and a StatAggregate pair. The StatEventKey is a byte array representation of
     * the parts that make up the statistic key, i.e. name, tagValues. The StatAggregate is just a container for the stat value
     * ready for downstream aggregation
     */
    @Override
    public Iterable<KeyValue<StatEventKey, StatAggregate>> flatMap(String statUuid, StatisticWrapper statisticWrapper) {
        Preconditions.checkNotNull(statUuid);
        Preconditions.checkNotNull(statisticWrapper);

        int maxEventIds = stroomPropertyService.getIntProperty(StatAggregate.PROP_KEY_MAX_AGGREGATED_EVENT_IDS, Integer.MAX_VALUE);
        Statistics.Statistic statistic = Preconditions.checkNotNull(statisticWrapper.getStatistic());

        List<MultiPartIdentifier> eventIds = convertEventIds(statistic, maxEventIds);

        //convert stat value
        StatAggregate statAggregate = new CountAggregate(eventIds, maxEventIds, statistic.getCount());
        List<KeyValue<StatEventKey, StatAggregate>> keyValues = buildKeyValues(statUuid, statisticWrapper, statAggregate);

        LOGGER.trace(() -> String.format("Flat mapping event into %s events", keyValues.size()));
        return keyValues;
    }

}
