package com.casino.casinoerp.dto;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

public class StrictFnbQuantityDeserializer extends JsonDeserializer<Integer> {
    @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            throw JsonMappingException.from(parser, "F&B quantity must be a positive JSON integer.");
        return parser.getIntValue();
    }
}
