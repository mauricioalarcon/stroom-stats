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

package stroom.stats.hbase.uid;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.stats.cache.CacheFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

public class TestUniqueIdCacheImpl {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private CacheFactory mockCacheFactory;

    private UniqueIdCache uniqueIdCache;

    @Before
    public void setup() {

        MockUniqueIdGenerator mockUniqueId = new MockUniqueIdGenerator();

        NameToUidLoaderWriter nameToUidLoaderWriter = new NameToUidLoaderWriter(mockUniqueId);
        UidToNameLoaderWriter uidToNameLoaderWriter = new UidToNameLoaderWriter(mockUniqueId);

        //build some caches to back UniqueIdCacheImpl
        CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache(UniqueIdCacheImpl.NAME_TO_UID_CACHE_NAME,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, UID.class,
                                ResourcePoolsBuilder.heap(1000))
                                .withLoaderWriter(nameToUidLoaderWriter)
                                .build())
                .withCache(UniqueIdCacheImpl.UID_TO_NAME_CACHE_NAME,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(UID.class, String.class,
                                ResourcePoolsBuilder.heap(1000))
                                .withLoaderWriter(uidToNameLoaderWriter)
                                .build())
                .build(true);

        Cache<String, UID> nameToUidCache = cacheManager.getCache(UniqueIdCacheImpl.NAME_TO_UID_CACHE_NAME, String.class, UID.class);
        Cache<UID, String> uidToNameCache = cacheManager.getCache(UniqueIdCacheImpl.UID_TO_NAME_CACHE_NAME, UID.class, String.class);

        //Get the mock cacheFactory to return our new caches when called
        when(mockCacheFactory.getOrCreateCache(
                eq(UniqueIdCacheImpl.NAME_TO_UID_CACHE_NAME),
                eq(String.class),
                eq(UID.class),
                any())
        ).thenReturn(nameToUidCache);

        when(mockCacheFactory.getOrCreateCache(
                eq(UniqueIdCacheImpl.UID_TO_NAME_CACHE_NAME),
                eq(UID.class),
                eq(String.class),
                any())
        ).thenReturn(uidToNameCache);

        //use null loaderwriters as they would only be passed to the cacheFactory which we are mocking
        uniqueIdCache = new UniqueIdCacheImpl(mockUniqueId, mockCacheFactory, nameToUidLoaderWriter, uidToNameLoaderWriter);
    }

    @Test
    public void getOrCreateId_getUniqueId_getName() throws Exception {


        String statNameStr = this.getClass().getName() + "-testGetOrCreateId-" + Instant.now().toString();
        //get the id for a name that will not exist, thus creating the mapping
        UID id = uniqueIdCache.getOrCreateId(statNameStr);

        assertThat(id).isNotNull();
        assertThat(id.getUidBytes()).hasSize(UID.UID_ARRAY_LENGTH);

        String name = uniqueIdCache.getName(id);

        //ensure the reverse map is also present
        assertThat(name).isEqualTo(statNameStr);

        //now get the id for the same string which was created above
        UID id2 = uniqueIdCache.getOrCreateId(statNameStr);

        assertThat(id2).isEqualTo(id);

        //now get the id for the same string using getId
        Optional<UID> id3 = uniqueIdCache.getUniqueId(statNameStr);

        assertThat(id3.isPresent()).isTrue();
        assertThat(id3.get()).isEqualTo(id);
    }

    @Test
    public void getUniqueId_notExists() throws Exception {

        //try and get an id for a name that will not exist
        String statNameStr = this.getClass().getName() + "-testGetId-" + Instant.now().toString();
        Optional<UID> id = uniqueIdCache.getUniqueId(statNameStr);

        assertThat(id.isPresent()).isFalse();
    }


    //partial mock to act like the forward and reverse mapping tables
    //this mock will be fronted by caches in UniqueIdCacheImpl
    private static class MockUniqueIdGenerator implements UniqueIdGenerator {

        private static final Logger LOGGER = LoggerFactory.getLogger(MockUniqueIdGenerator.class);

        private final AtomicInteger idSequence = new AtomicInteger(0);
        private final ConcurrentMap<UID, String> uidToNameMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, UID> nameToUidMap = new ConcurrentHashMap<>();

        @Override
        public Optional<UID> getId(String name) {
            return Optional.ofNullable(nameToUidMap.get(name));
        }

        @Override
        public UID getOrCreateId(String name) {
            final UID uniqueId = nameToUidMap.computeIfAbsent(name, key -> {
                // name not mapped so create a new ID
                final byte[] id = generateNewId();

                final UID uid = UID.from(id);

                LOGGER.trace("Creating mock UID: {} for name: {} ", uid.toString(), key);

                uidToNameMap.put(uid, key);

                return uid;
            });

            return uniqueId;
        }

        @Override
        public Optional<String> getName(final UID uid) {
            return Optional.ofNullable(uidToNameMap.get(uid));
        }

        @Override
        public int getWidth() {
            return UID.UID_ARRAY_LENGTH;
        }

        private byte[] generateNewId() {
            return UniqueIdGenerator.convertToUid(idSequence.incrementAndGet(), UID.UID_ARRAY_LENGTH);
        }
    }

}