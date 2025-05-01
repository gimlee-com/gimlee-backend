package com.gimlee.api.cities.data

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

class DMSToDecimalDeserializer: JsonDeserializer<Double>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Double {
        val stringValue = parser.valueAsString?: return 0.0
        val splitValue = stringValue.split("°", "'", "''").subList(0, 3).map { it.toDouble() }
        return splitValue[0] + (splitValue[1] / 60) + (splitValue[2] / 3600)

    }
}