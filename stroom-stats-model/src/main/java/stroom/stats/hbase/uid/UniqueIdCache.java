

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

import javaslang.control.Try;

public interface UniqueIdCache {
    /**
     * Looks up the passed string in the UID table and returns the UID byte
     * array. If the String is not found it creates an entry and returns the new
     * UID.
     *
     * This should ONLY be used in situations where you are trying to store
     * something that needs a UID. Do not use if you are just trying to find an
     * existing UID for a given name because if the name doesn't exist it will
     * create a new UID entry
     *
     * @param name
     *            The String value to look up, e.g. event name
     * @return The UID as a byte[]
     */
    UID getOrCreateId(final String name);

    /**
     * Looks up the passed string in the UID table and returns the UID byte
     * array.
     *
     * Should be used in situations where you want to know the UID for a given
     * name but understand that the name-UID mapping may or may not exist, hence
     * the Optional return type.
     *
     * @param name
     *            The name string to look up in the cache
     * @return The mapped UID or an empty optional
     */
    Try<UID> getUniqueId(final String name);

    default UID getCreateOrDefaultId(final String name, final UniqueIdFetchMode fetchMode) {
        switch (fetchMode) {
        case GET_OR_CREATE:
            return getOrCreateId(name);
        case GET:
            return getUniqueIdOrDefault(name);
        default:
            throw new RuntimeException(String.format("UniqueIdFetchMode value we weren't expecting %s", fetchMode));
        }
    }

    default UID getUniqueIdOrElse(final String name, final UID notFoundUid) {
        return getUniqueId(name).getOrElse(notFoundUid);
    }

    default UID getUniqueIdOrDefault(final String name) {
        return getUniqueId(name).getOrElse(UID.NOT_FOUND_UID);
    }

    /**
     * For a given UID byte array will return the mapped name string, or throw a
     * {@link RuntimeException} if not found. UIDs are only generated by the
     * UniqueIdCache so if you have one it should have a name. IF it doesn't
     * then something has gone wrong.
     */
    String getName(final UID id);

    // public Optional<String> getName(final long id);
    //
    // public default <X extends RuntimeException> String getNameOrThrow(final
    // long id,
    // final Supplier<? extends X> exceptionSupplier) {
    // return getName(id).orElseThrow(exceptionSupplier);
    // }

    int getWidth();

    int getCacheSize();
}
