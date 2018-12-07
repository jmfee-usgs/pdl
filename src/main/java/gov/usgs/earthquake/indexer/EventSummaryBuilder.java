package gov.usgs.earthquake.indexer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/**
 * EventSummaryBuilder generates summary information for an Event.
 */
public class EventSummaryBuilder {

    /** The Event object being summarized. */
    public final Event event;
    /** Preferred origin product. */
    public final ProductSummary preferredOrigin;
    /** Preferred magnitude product. */
    public final ProductSummary preferredMagnitude;

    public EventSummaryBuilder(final Event event) {
        this.event = event;
        this.preferredOrigin = event.getPreferredOriginProduct();
        this.preferredMagnitude = event.getPreferredMagnitudeProduct();
    }

    /**
     * PAGER Alert Level.
     *
     * @return alertlevel property from losspager product, or null.
     */
    public String getAlertLevel() {
        String alertlevel = null;

        ProductSummary losspager = event.getPreferredProduct("losspager");
        if (losspager != null) {
            alertlevel = losspager.getProperties().get("alertlevel");
        }

        return alertlevel;
    }

    /**
     * Origin Azimuthal Gap.
     *
     * @return azimuthal-gap property from origin product, or null.
     */
    public BigDecimal getAzimuthalGap() {
        BigDecimal azimuthalGap = null;
        if (preferredOrigin != null) {
            try {
                azimuthalGap = new BigDecimal(preferredOrigin.getProperties().get("azimuthal-gap"));
            } catch (Exception e) {}
        }
        return azimuthalGap;
    }

    /**
     * Event IDs from multiple contributors.
     *
     * @return event ids associated to event.
     */
    public Set<String> getEventIds() {
        return event.getSubEvents().keySet();
    }

    /**
     * Origin Event Type.
     *
     * @return event-type property from preferred origin, or "earthquake".
     */
    public String getEventType() {
        String eventType = null;
        if (preferredOrigin != null) {
            eventType = preferredOrigin.getProperties().get("event-type");
        }
        if (eventType == null) {
            eventType = "earthquake";
        }
        return eventType;
    }

    /**
     * Magnitude Type.
     *
     * @return magnitude-type product from magnitude product.
     */
    public String getMagnitudeType() {
        String magnitudeType = null;
        if (preferredMagnitude != null) {
            magnitudeType = preferredMagnitude.getProperties().get("magnitude-type");
        }
        return magnitudeType;
    }

    /**
     * Maximum Reported Intensity.
     *
     * Community Determined Intensity.
     *
     * @return maxmmi property from dyfi product.
     */
    public BigDecimal getMaxCDI() {
        BigDecimal maxcdi = null;
        ProductSummary dyfi = event.getPreferredProduct("dyfi");
        if (dyfi != null) {
            // dyfi reports cdi as "maxmmi" property
            try {
                maxcdi = new BigDecimal(dyfi.getProperties().get("maxmmi"));
            } catch (Exception e) {}
        }
        return maxcdi;
    }

    /**
     * Maximum Estimated Intensity.
     *
     * @return maxmmi property from either shakemap or losspager product.
     */
    public BigDecimal getMaxMMI() {
        BigDecimal maxmmi = null;
        ProductSummary shakemap = event.getPreferredProduct("shakemap");
        if (shakemap != null) {
            try {
                maxmmi = new BigDecimal(shakemap.getProperties().get("maxmmi"));
            } catch (Exception e) {}
        }
        if (maxmmi == null) {
            ProductSummary losspager = event.getPreferredProduct("losspager");
            if (losspager != null) {
                try {
                    maxmmi = new BigDecimal(losspager.getProperties().get("maxmmi"));
                } catch (Exception e) {}
            }
        }
        return maxmmi;
    }

    /**
     * Distance to closest station in kilometers.
     *
     * @return minimum-distance property from preferred origin.
     */
    public BigDecimal getMinimumDistance() {
        BigDecimal minimumDistance = null;
        if (preferredOrigin != null) {
            try {
                minimumDistance = new BigDecimal(preferredOrigin.getProperties().get("minimum-distance"));
            } catch (Exception e) {}
        }
        return minimumDistance;
    }

    /**
     * Number of felt reports received by DYFI.
     *
     * @return num-responses or numResp property from dyfi product.
     */
    public BigInteger getNumResponses() {
        BigInteger numResponses = null;
        ProductSummary dyfi = event.getPreferredProduct("dyfi");
        if (dyfi != null) {
            // dyfi reports cdi as "maxmmi" property
            try {
                numResponses = new BigInteger(dyfi.getProperties().get("num-responses"));
            } catch (Exception e) {}

            if (numResponses == null) {
                try {
                    numResponses = new BigInteger(dyfi.getProperties().get("numResp"));
                } catch (Exception e) {}
            }
        }
        return numResponses;
    }

    /**
     * Number of stations used for origin.
     *
     * @return num-stations-used property from preferred origin.
     */
    public BigInteger getNumStationsUsed() {
        BigInteger numStationsUsed = null;
        if (preferredOrigin != null) {
            try {
                numStationsUsed = new BigInteger(preferredOrigin.getProperties().get("num-stations-used"));
            } catch (Exception e) {}
        }
        return numStationsUsed;
    }

    /**
     * Review status for origin.
     *
     * @return "deleted", when event is deleted,
     *         or review-status property from preferred origin.
     */
    public String getReviewStatus() {
        String reviewStatus = null;
        if (event.isDeleted()) {
            reviewStatus = "deleted";
        } else if (preferredOrigin != null) {
            reviewStatus = preferredOrigin.getProperties().get("review-status");
        }
        if (reviewStatus == null) {
            reviewStatus = "automatic";
        }
        return reviewStatus;
    }

    /**
     * Estimated significance of event.
     *
     * Events are considered significant when this is &gt;= 650.
     *
     * @return significance property from significance product,
     *         or, calculated significance based on magnitude, losspager,
     *         and dyfi products.
     */
    public BigInteger getSignificance() {
        ProductSummary significanceProduct = event.getPreferredProduct("significance");
        if (significanceProduct != null) {
            try {
                return new BigInteger(significanceProduct.getProperties().get("significance"));
            } catch (Exception e) {}
        }

        BigDecimal magnitude = event.getMagnitude();
        String alertLevel = getAlertLevel();
        BigInteger numResponses = getNumResponses();
        BigDecimal maxcdi = getMaxCDI();

        Long magnitudeSignificance = 0L;
        Long pagerSignificance = 0L;
        Long dyfiSignificance = 0L;

        if (magnitude != null) {
            double mag = magnitude.doubleValue();
            magnitudeSignificance = Math.round(mag * 100.0 * Math.abs(mag) / 6.5);
        }
        if (alertLevel != null) {
            if ("red".equalsIgnoreCase(alertLevel)) {
                pagerSignificance = 2000L;
            } else if ("orange".equalsIgnoreCase(alertLevel)) {
                pagerSignificance = 1000L;
            } else if ("yellow".equalsIgnoreCase(alertLevel)) {
                pagerSignificance = 650L;
            }
        }
        if (numResponses != null) {
            dyfiSignificance = Math.round(Math.min(1000.0, numResponses.doubleValue()) * maxcdi.doubleValue() / 10);
        }

        Long significance = Math.max(magnitudeSignificance, pagerSignificance) + dyfiSignificance;
        return BigInteger.valueOf(significance);
    }

    /**
     * Sources that have contributed products.
     *
     * @return set of sources.
     */
    public Set<String> getSources() {
        Set<String> sources = new HashSet<String>();
        for (ProductSummary p : event.getProductList()) {
            sources.add(p.getSource());
        }
        return sources;
    }

    /**
     * Standard Error for Origin.
     *
     * @return standard-error property for preferred originl.
     */
    public BigDecimal getStandardError() {
        BigDecimal standardError = null;
        if (preferredOrigin != null) {
            try {
                standardError = new BigDecimal(preferredOrigin.getProperties().get("standard-error"));
            } catch (Exception e) {}
        }
        return standardError;
    }

    /**
     * Title for event.
     *
     * @return title property from preferred origin,
     *         or location property from geoserve product.
     */
    public String getTitle() {
        String title = null;
        if (preferredOrigin != null) {
            title = preferredOrigin.getProperties().get("title");
        }
        if (title == null) {
            ProductSummary geoserve = event.getPreferredProduct("geoserve");
            if (geoserve != null) {
                title = geoserve.getProperties().get("location");
            }
        }
        return title;
    }

    /**
     * Types of products associated to event.
     *
     * @return set of types.
     */
    public Set<String> getTypes() {
        Set<String> types = new HashSet<String>();
        for (ProductSummary p : event.getProductList()) {
            types.add(p.getType());
        }
        return types;
    }

    /**
     * Whether a link to tsunami information has been contributed.
     *
     * @return true, if impact-link product with tsunami link addon code is found.
     */
    public Boolean isTsunamiLink() {
        Boolean isTsunamiLink = false;
        for (ProductSummary p : event.getProducts("impact-link")) {
            String addonCode = p.getProperties().get("addon-code");
            if (addonCode != null && addonCode.toUpperCase().startsWith("TSUNAMILINK")) {
                isTsunamiLink = true;
                break;
            }
        }
        return isTsunamiLink;
    }

}