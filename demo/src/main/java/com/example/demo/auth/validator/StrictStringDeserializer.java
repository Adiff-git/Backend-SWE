package com.example.demo.auth.validator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import com.example.demo.user.exception.BusinessValidationException;
/** Credential JSON must contain strings; never coerce numeric or boolean values. */
public final class StrictStringDeserializer extends ValueDeserializer<String> {
    @Override public String deserialize(JsonParser parser,DeserializationContext context) {
        if(parser.currentToken()!=JsonToken.VALUE_STRING)throw new BusinessValidationException("Invalid input");
        return parser.getString();
    }
}
