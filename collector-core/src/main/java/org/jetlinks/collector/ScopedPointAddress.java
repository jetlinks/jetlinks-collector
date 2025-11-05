package org.jetlinks.collector;

import org.hibernate.validator.constraints.Length;
import org.jetlinks.collector.address.PointAddress;

public abstract class ScopedPointAddress extends PointAddress {

    @Length(max = 64)
    public abstract CharSequence getScope();

}
