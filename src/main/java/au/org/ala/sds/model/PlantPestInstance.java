/**
 *
 */
package au.org.ala.sds.model;

import java.util.Date;

import org.apache.commons.lang.StringUtils;

import au.org.ala.sds.util.DateHelper;

/**
 *
 * @author Peter Flemming (peter.flemming@csiro.au)
 */
public class PlantPestInstance extends SensitivityInstance {

    private final Date fromDate;
    private final Date toDate;

    /**
     * @param category
     * @param authority
     * @param fromDate
     * @param toDate
     * @param zone
     * @param generalisation
     */
    public PlantPestInstance(
            SensitivityCategory category,
            String authority,
            SensitivityZone zone,
            String fromDate,
            String toDate) {
        super(category, authority, zone);
        this.fromDate = StringUtils.isNotEmpty(fromDate) ? DateHelper.parseDate(fromDate) : null;
        this.toDate = StringUtils.isNotEmpty(toDate) ? DateHelper.parseDate(toDate) : null;
    }

    public Date getFromDate() {
        return fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

}