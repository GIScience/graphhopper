/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPoint;
import tools.jackson.databind.JsonNode;

import static com.graphhopper.jackson.geojson.GeoJson.COORDINATES;

public class MultiPointParser extends BaseParser implements GeometryParser<MultiPoint> {

    public MultiPointParser(GeometryFactory geometryFactory) {
        super(geometryFactory);
    }

    public MultiPoint multiPointFromJson(JsonNode root) {
        return geometryFactory.createMultiPoint(
                PointParser.coordinatesFromJson(root.get(COORDINATES)));
    }

    @Override
    public MultiPoint geometryFromJson(JsonNode node) {
        return multiPointFromJson(node);
    }
}
