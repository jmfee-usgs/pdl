package gov.usgs.earthquake.indexer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import gov.usgs.earthquake.indexer.IndexerChange.IndexerChangeType;
import gov.usgs.util.XmlUtils;


/**
 * Generate summary of an IndexerEvent.
 */
public class JsonEventSummary {

    /** IndexerEvent being summarized. */
    public final IndexerEvent event;

    /**
     * Construct a new JsonEventSummary.
     *
     * @param event event to summarize.
     */
    public JsonEventSummary(final IndexerEvent event) {
        this.event = event;
    }

    /**
     * Summarize one IndexerChange.
     *
     * @param change change to summarize.
     * @return object with change and associated event information.
     */
    public JsonObject summarizeChange(final IndexerChange change) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        IndexerChangeType type = change.getType();
        builder.add("type", type.toString());

        if (type == IndexerChangeType.EVENT_DELETED || type == IndexerChangeType.EVENT_MERGED) {
            // an event was removed
            addOrAddNull(builder, "removedEvent", summarizeEvent(change.getOriginalEvent()));
        } else {
            addOrAddNull(builder, "event", summarizeEvent(change.getNewEvent()));
        }

        return builder.build();
    }

    /**
     * Summarize an Event.
     *
     * @param event event to summarize.
     * @return event as geojson feature.
     */
    public JsonObject summarizeEvent(Event event) {
        if (event == null) {
            return null;
        }

        EventSummaryBuilder summary = new EventSummaryBuilder(event);
        JsonObjectBuilder builder = Json.createObjectBuilder();

        builder.add("type", "Feature");
        builder.add("id", event.getEventId());

        JsonObjectBuilder properties = Json.createObjectBuilder();
        addOrAddNull(properties, "mag", event.getMagnitude());
        addOrAddNull(properties, "place", summary.getTitle());
        addOrAddNull(properties, "time", XmlUtils.formatDate(event.getTime()));
        addOrAddNull(properties, "updated", XmlUtils.formatDate(event.getUpdateTime()));
        addOrAddNull(properties, "felt", summary.getNumResponses());
        addOrAddNull(properties, "cdi", summary.getMaxCDI());
        addOrAddNull(properties, "mmi", summary.getMaxMMI());
        addOrAddNull(properties, "alert", summary.getAlertLevel());
        addOrAddNull(properties, "status", summary.getReviewStatus());
        properties.add("tsunami", summary.isTsunamiLink());
        properties.add("sig", summary.getSignificance());
        properties.add("net", event.getSource());
        properties.add("code", event.getSourceCode());
        properties.add("ids", toJsonArray(summary.getEventIds()));
        properties.add("sources", toJsonArray(summary.getSources()));
        properties.add("types", toJsonArray(summary.getTypes()));
        addOrAddNull(properties, "nst", summary.getNumStationsUsed());
        addOrAddNull(properties, "dmin", summary.getMinimumDistance());
        addOrAddNull(properties, "gap", summary.getAzimuthalGap());
        addOrAddNull(properties, "magType", summary.getMagnitudeType());
        properties.add("type", summary.getEventType());
        builder.add("properties", properties);

        JsonObjectBuilder geometry = Json.createObjectBuilder();
        geometry.add("type", "Point");
        JsonArrayBuilder coordinates = Json.createArrayBuilder();
        coordinates.add(event.getLongitude());
        coordinates.add(event.getLatitude());
        if (event.getDepth() == null) {
            coordinates.addNull();
        } else {
            coordinates.add(event.getDepth());
        }
        geometry.add("coordinates", coordinates);
        builder.add("geometry", geometry);

        return builder.build();
    }

    /**
     * Summarize an IndexerEvent, and all its changes.
     *
     * @param event what the indexer changed.
     * @return object with product and list of changes.
     */
    public JsonObject summarizeIndexerEvent(final IndexerEvent event) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("product", summarizeProductSummary(event.getSummary()));

        JsonArrayBuilder changes = Json.createArrayBuilder();
        event.getIndexerChanges().forEach((c) -> changes.add(summarizeChange(c)));
        builder.add("changes", changes);

        return builder.build();
    }

    /**
     * Information about the product triggering the changes.
     * 
     * @param summary ProductSummary to summarize.
     * @return summary.
     */
    public JsonObject summarizeProductSummary(ProductSummary summary) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("source", summary.getId().getSource());
        builder.add("type", summary.getId().getType());
        builder.add("code", summary.getId().getCode());
        builder.add("updateTime", XmlUtils.formatDate(summary.getId().getUpdateTime()));
        builder.add("status", summary.getStatus());
        return builder.build();
    }

    public JsonObject toJsonObject() {
        return summarizeIndexerEvent(this.event);
    }

    /**
     * Utility method to convert Set<String> to JsonArray.
     *
     * @param set set to convert.
     * @return array
     */
    protected JsonArray toJsonArray(final Set<String> set) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        set.forEach(item -> arr.add(item));
        return arr.build();
    }

    /**
     * Utility method to either addNull or add.
     *
     * @param builder builder where key is added.
     * @param key key to add.
     * @param value value/null to add.
     */
    protected void addOrAddNull(final JsonObjectBuilder builder, final String key, final BigDecimal value) {
        if (value == null) {
            builder.addNull(key);
        } else {
            builder.add(key, value);
        }
    }

    /**
     * Utility method to either addNull or add.
     *
     * @param builder builder where key is added.
     * @param key key to add.
     * @param value value/null to add.
     */
    protected void addOrAddNull(final JsonObjectBuilder builder, final String key, final JsonValue value) {
        if (value == null) {
            builder.addNull(key);
        } else {
            builder.add(key, value);
        }
    }

    /**
     * Utility method to either addNull or add.
     *
     * @param builder builder where key is added.
     * @param key key to add.
     * @param value value/null to add.
     */
    protected void addOrAddNull(final JsonObjectBuilder builder, final String key, final String value) {
        if (value == null) {
            builder.addNull(key);
        } else {
            builder.add(key, value);
        }
    }

    /**
     * Utility method to either addNull or add.
     *
     * @param builder builder where key is added.
     * @param key key to add.
     * @param value value/null to add.
     */
    protected void addOrAddNull(final JsonObjectBuilder builder, final String key, final BigInteger value) {
        if (value == null) {
            builder.addNull(key);
        } else {
            builder.add(key, value);
        }
    }

}
