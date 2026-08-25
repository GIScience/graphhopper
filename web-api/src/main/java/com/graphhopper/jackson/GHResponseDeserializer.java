/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.jackson;

import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

// ORS-GH MOD - ported to Jackson 3
public class GHResponseDeserializer extends ValueDeserializer<GHResponse> {
    @Override
    public GHResponse deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        GHResponse ghResponse = new GHResponse();
        JsonNode treeNode = p.readValueAsTree();
        for (JsonNode path : treeNode.get("paths")) {
            ResponsePath responsePath = ctxt.readTreeAsValue(path, ResponsePath.class);
            ghResponse.add(responsePath);
        }
        return ghResponse;
    }
}
