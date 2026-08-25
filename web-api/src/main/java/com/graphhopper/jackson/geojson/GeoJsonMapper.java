package com.graphhopper.jackson.geojson;

import tools.jackson.databind.json.JsonMapper;

/**
 * Shared factory for the small ObjectMapper used to read plain GeoJSON files
 * (border/landmark-splitting FeatureCollections) into {@link com.graphhopper.util.JsonFeatureCollection}.
 * This is separate from {@link com.graphhopper.jackson.Jackson}, which builds the full mapper used for
 * the HTTP API request/response format.
 */
// ORS-GH MOD - new class
public class GeoJsonMapper {

    private GeoJsonMapper() {
    }

    public static JsonMapper newObjectMapper() {
        return JsonMapper.builder().addModule(new JtsModule()).build();
    }
}
