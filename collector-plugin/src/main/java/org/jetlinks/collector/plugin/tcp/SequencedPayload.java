package org.jetlinks.collector.plugin.tcp;

public interface SequencedPayload<ID> {

    ID getSequenceNumber();

}
