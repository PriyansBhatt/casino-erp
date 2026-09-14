package com.casino.casinoerp.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Same non-coercing JSON rule as chip quantities, bounded to the existing integer column. */
public class StrictCashCountQuantityDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        long value = new StrictBuyInQuantityDeserializer().deserialize(parser, context);
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw com.fasterxml.jackson.databind.JsonMappingException.from(parser,
                    "Cash note quantities must be integers between 0 and 2147483647.");
        }
        return (int) value;
    }
}
