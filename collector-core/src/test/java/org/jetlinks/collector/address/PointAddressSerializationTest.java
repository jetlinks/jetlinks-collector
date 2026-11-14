package org.jetlinks.collector.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetlinks.collector.PointMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointAddressSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesStringAddressInMetadataWithoutLosingNodeId() throws Exception {
        PointMetadata metadata = new PointMetadata();
        String nodeId = "ns=2;s=机组.温度";
        metadata.setAddress(PointAddress.create(nodeId));

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(metadata));

        assertEquals(nodeId, json.get("address").asText());
        assertEquals("\"\"", mapper.writeValueAsString(PointAddress.create("")));
    }

    @Test
    void preservesStructuredProtocolAddresses() throws Exception {
        PointMetadata metadata = new PointMetadata();
        metadata.setAddress(new StructuredAddress());

        JsonNode address = mapper.readTree(mapper.writeValueAsBytes(metadata)).get("address");

        assertEquals(3, address.get("code").asInt());
        assertEquals(1, address.get("offset").asInt());
    }

    public static class StructuredAddress extends PointAddress {
        public int getCode() { return 3; }
        public int getOffset() { return 1; }
        @Override public boolean equals(Object other) { return other instanceof StructuredAddress; }
        @Override public int hashCode() { return 3; }
        @Override public String toString() { return "40002"; }
    }
}
