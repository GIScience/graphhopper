/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.serialization;

import com.graphhopper.jackson.geojson.parsers.GeometryParser;
import org.locationtech.jts.geom.Geometry;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class GeometryDeserializer<T extends Geometry> extends ValueDeserializer<T> {

    private final GeometryParser<T> geometryParser;

    public GeometryDeserializer(GeometryParser<T> geometryParser) {
        this.geometryParser = geometryParser;
    }

    @Override
    public T deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
        JsonNode root = jsonParser.readValueAsTree();
        return geometryParser.geometryFromJson(root);
    }
}
