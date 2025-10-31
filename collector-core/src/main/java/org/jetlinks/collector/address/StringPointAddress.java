package org.jetlinks.collector.address;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
class StringPointAddress extends PointAddress {
    final String address;

    @Override
    @Nonnull
    public String toString() {
        return address;
    }
}
