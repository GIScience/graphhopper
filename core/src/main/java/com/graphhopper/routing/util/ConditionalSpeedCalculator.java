package com.graphhopper.routing.util;

import ch.poole.conditionalrestrictionparser.ConditionalRestrictionParser;
import ch.poole.conditionalrestrictionparser.Restriction;
import com.graphhopper.routing.EdgeKeys;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.storage.ConditionalEdges;
import com.graphhopper.util.DateTimeHelper;
import com.graphhopper.storage.ConditionalEdgesMap;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;

/**
 * Calculate time-dependent conditional speed
 *
 * @author Andrzej Oles
 */
public class ConditionalSpeedCalculator implements SpeedCalculator{
    protected final DecimalEncodedValue avSpeedEnc;

    // time-dependent stuff
    private final BooleanEncodedValue conditionalEnc;
    private final ConditionalEdgesMap conditionalEdges;
    private final DateTimeHelper dateTimeHelper;

    public ConditionalSpeedCalculator(GraphHopperStorage graph, FlagEncoder encoder) {
        avSpeedEnc = encoder.getAverageSpeedEnc();

        // time-dependent stuff
        EncodingManager encodingManager = graph.getEncodingManager();
        String encoderName = EncodingManager.getKey(encoder, ConditionalEdges.SPEED);

        if (!encodingManager.hasEncodedValue(encoderName)) {
            throw new IllegalStateException("No conditional speed associated with the flag encoder");
        }

        conditionalEnc = encodingManager.getBooleanEncodedValue(encoderName);
        conditionalEdges = graph.getConditionalSpeed(encoder);

        this.dateTimeHelper = new DateTimeHelper(graph);
    }

    public double getSpeed(EdgeIteratorState edge, boolean reverse, long time) {
        double speed = reverse ? edge.getReverse(avSpeedEnc) : edge.get(avSpeedEnc);

        // retrieve time-dependent maxspeed here
        if (time != -1 && edge.get(conditionalEnc)) {
            ZonedDateTime zonedDateTime = dateTimeHelper.getZonedDateTime(edge, time);
            int edgeId = EdgeKeys.getOriginalEdge(edge);
            String value = conditionalEdges.getValue(edgeId);
            double maxSpeed = getSpeed(value, zonedDateTime);
            if (maxSpeed >= 0)
                return maxSpeed * 0.9;
        }

        return speed;
    }

    private double getSpeed(String conditional, ZonedDateTime zonedDateTime)  {
        try {
            ConditionalRestrictionParser crparser = new ConditionalRestrictionParser(new ByteArrayInputStream(conditional.getBytes()));
            ArrayList<Restriction> restrictions = crparser.restrictions();

            // iterate over restrictions starting from the last one in order to match to the most specific one
            for (int i = restrictions.size() - 1 ; i >= 0; i--) {
                Restriction restriction = restrictions.get(i);
                // stop as soon as time matches the combined conditions
                if (TimeDependentConditionalEvaluator.match(restriction.getConditions(), zonedDateTime)) {
                    return AbstractFlagEncoder.parseSpeed(restriction.getValue());
                }
            }
        } catch (ch.poole.conditionalrestrictionparser.ParseException e) {
            //nop
        }
        return -1;
    }

}
