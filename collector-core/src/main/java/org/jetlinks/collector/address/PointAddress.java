package org.jetlinks.collector.address;

import jakarta.annotation.Nonnull;

/**
 * 点位地址
 *
 * @see org.jetlinks.collector.ScopedPointAddress
 */
public abstract class PointAddress {

    public static PointAddress create(String address) {
        return new StringPointAddress(address);
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    @Nonnull
    public abstract String toString();
}
