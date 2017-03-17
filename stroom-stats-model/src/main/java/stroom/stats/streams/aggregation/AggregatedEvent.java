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

package stroom.stats.streams.aggregation;

import com.google.common.base.Preconditions;
import stroom.stats.streams.StatKey;

/**
 * Simple wrapper for a {@link StatKey} and {@link StatAggregate} that make up an aggregated statistic event
 */
public class AggregatedEvent {

    private final StatKey statKey;
    private final StatAggregate statAggregate;

    public AggregatedEvent(final StatKey statKey, final StatAggregate statAggregate) {
        Preconditions.checkNotNull(statKey);
        Preconditions.checkNotNull(statAggregate);

        this.statKey = statKey;
        this.statAggregate = statAggregate;
    }

    public StatKey getStatKey() {
        return statKey;
    }

    public StatAggregate getStatAggregate() {
        return statAggregate;
    }

    @Override
    public String toString() {
        return "AggregatedEvent{" +
                "statKey=" + statKey +
                ", statAggregate=" + statAggregate +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final AggregatedEvent that = (AggregatedEvent) o;

        if (!statKey.equals(that.statKey)) return false;
        return statAggregate.equals(that.statAggregate);
    }

    @Override
    public int hashCode() {
        int result = statKey.hashCode();
        result = 31 * result + statAggregate.hashCode();
        return result;
    }
}
