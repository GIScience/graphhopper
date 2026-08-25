/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static com.graphhopper.jackson.geojson.GeoJson.*;

public class GenericGeometryParser extends BaseParser implements GeometryParser<Geometry> {

    private final Map<String, GeometryParser<? extends Geometry>> parsers;

    public GenericGeometryParser(GeometryFactory geometryFactory) {
        super(geometryFactory);
        parsers = new HashMap<>();
        parsers.put(POINT, new PointParser(geometryFactory));
        parsers.put(MULTI_POINT, new MultiPointParser(geometryFactory));
        parsers.put(LINE_STRING, new LineStringParser(geometryFactory));
        parsers.put(MULTI_LINE_STRING, new MultiLineStringParser(geometryFactory));
        parsers.put(POLYGON, new PolygonParser(geometryFactory));
        parsers.put(MULTI_POLYGON, new MultiPolygonParser(geometryFactory));
        parsers.put(GEOMETRY_COLLECTION, new GeometryCollectionParser(geometryFactory, this));
    }

    @Override
    public Geometry geometryFromJson(JsonNode node) {
        String typeName = node.get(TYPE).asText();
        GeometryParser<? extends Geometry> parser = parsers.get(typeName);
        if (parser == null) {
            throw new IllegalArgumentException("Invalid geometry type: " + typeName);
        }
        return parser.geometryFromJson(node);
    }
}
