/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import tools.jackson.databind.JsonNode;

import static com.graphhopper.jackson.geojson.GeoJson.COORDINATES;

public class MultiLineStringParser extends BaseParser implements GeometryParser<MultiLineString> {

    public MultiLineStringParser(GeometryFactory geometryFactory) {
        super(geometryFactory);
    }

    public MultiLineString multiLineStringFromJson(JsonNode root) {
        return geometryFactory.createMultiLineString(
                lineStringsFromJson(root.get(COORDINATES)));
    }

    private LineString[] lineStringsFromJson(JsonNode array) {
        LineString[] strings = new LineString[array.size()];
        for (int i = 0; i != array.size(); ++i) {
            strings[i] = geometryFactory.createLineString(PointParser.coordinatesFromJson(array.get(i)));
        }
        return strings;
    }

    @Override
    public MultiLineString geometryFromJson(JsonNode node) {
        return multiLineStringFromJson(node);
    }
}
