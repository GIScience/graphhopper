/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import tools.jackson.databind.JsonNode;

import static com.graphhopper.jackson.geojson.GeoJson.GEOMETRIES;

// ORS-GH MOD - new class
public class GeometryCollectionParser extends BaseParser implements GeometryParser<GeometryCollection> {

    private final GenericGeometryParser genericGeometriesParser;

    public GeometryCollectionParser(GeometryFactory geometryFactory, GenericGeometryParser genericGeometriesParser) {
        super(geometryFactory);
        this.genericGeometriesParser = genericGeometriesParser;
    }

    private Geometry[] geometriesFromJson(JsonNode arrayOfGeoms) {
        Geometry[] items = new Geometry[arrayOfGeoms.size()];
        for (int i = 0; i != arrayOfGeoms.size(); ++i) {
            items[i] = genericGeometriesParser.geometryFromJson(arrayOfGeoms.get(i));
        }
        return items;
    }

    @Override
    public GeometryCollection geometryFromJson(JsonNode node) {
        return geometryFactory.createGeometryCollection(
                geometriesFromJson(node.get(GEOMETRIES)));
    }
}
