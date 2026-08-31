/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import tools.jackson.databind.JsonNode;

import static com.graphhopper.jackson.geojson.GeoJson.COORDINATES;

// ORS-GH MOD - new class
public class MultiPolygonParser extends BaseParser implements GeometryParser<MultiPolygon> {

    private final PolygonParser helperParser;

    public MultiPolygonParser(GeometryFactory geometryFactory) {
        super(geometryFactory);
        helperParser = new PolygonParser(geometryFactory);
    }

    public MultiPolygon multiPolygonFromJson(JsonNode root) {
        JsonNode arrayOfPolygons = root.get(COORDINATES);
        return geometryFactory.createMultiPolygon(polygonsFromJson(arrayOfPolygons));
    }

    private Polygon[] polygonsFromJson(JsonNode arrayOfPolygons) {
        Polygon[] polygons = new Polygon[arrayOfPolygons.size()];
        for (int i = 0; i != arrayOfPolygons.size(); ++i) {
            polygons[i] = helperParser.polygonFromJsonArrayOfRings(arrayOfPolygons.get(i));
        }
        return polygons;
    }

    @Override
    public MultiPolygon geometryFromJson(JsonNode node) {
        return multiPolygonFromJson(node);
    }
}
