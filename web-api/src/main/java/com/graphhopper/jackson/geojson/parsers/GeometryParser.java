/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.Geometry;
import tools.jackson.databind.JsonNode;

// ORS-GH MOD - new interface
public interface GeometryParser<T extends Geometry> {

    T geometryFromJson(JsonNode node);

}
