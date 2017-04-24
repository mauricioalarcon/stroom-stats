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

package stroom.stats.configuration;

import org.ehcache.spi.loaderwriter.BulkCacheLoadingException;
import org.ehcache.spi.loaderwriter.BulkCacheWritingException;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatisticConfigurationCacheByNameLoaderWriter implements CacheLoaderWriter<String,StatisticConfiguration>{

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticConfigurationCacheByNameLoaderWriter.class);


    private final StatisticConfigurationEntityDAO statisticConfigurationEntityDAO;

    @Inject
    public StatisticConfigurationCacheByNameLoaderWriter(final StatisticConfigurationEntityDAO statisticConfigurationEntityDAO) {
        this.statisticConfigurationEntityDAO = statisticConfigurationEntityDAO;
    }

    @Override
    public StatisticConfiguration load(final String name) throws Exception {

        LOGGER.debug("load called for name {}", name);

        return statisticConfigurationEntityDAO.loadByName(name)
                .orElseThrow(() -> new Exception(String.format("Statistic configuration with name %s cannot be found in the database", name)));
    }

    @Override
    public Map<String, StatisticConfiguration> loadAll(final Iterable<? extends String> keys) throws BulkCacheLoadingException, Exception {
        LOGGER.debug("loadAll called for keys {}", keys);

        //unique key constraint should ensure we only have one stat config per uuid, hence (o1,o2) -> o1
        return statisticConfigurationEntityDAO.loadAll().stream()
                .map(statConfigEntity -> (StatisticConfiguration) statConfigEntity)
                .collect(Collectors.toMap(StatisticConfiguration::getName, Function.identity(), (o1, o2) -> o1));
    }

    @Override
    public void write(final String key, final StatisticConfiguration value) throws Exception {
        throw new UnsupportedOperationException("CRUD operations are not currently supported on this cache");
    }

    @Override
    public void writeAll(final Iterable<? extends Map.Entry<? extends String, ? extends StatisticConfiguration>> entries) throws BulkCacheWritingException, Exception {
        throw new UnsupportedOperationException("CRUD operations are not currently supported on this cache");
    }

    @Override
    public void delete(final String key) throws Exception {
        throw new UnsupportedOperationException("CRUD operations are not currently supported on this cache");
    }

    @Override
    public void deleteAll(final Iterable<? extends String> keys) throws BulkCacheWritingException, Exception {
        throw new UnsupportedOperationException("CRUD operations are not currently supported on this cache");
    }
}
