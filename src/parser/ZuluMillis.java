/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package parser;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Class to provide Zulu Time (UTC) in milliseconds.
 */
public final class ZuluMillis {

    private static final Calendar CAL = new GregorianCalendar();

    /**
     * Method to return the current time in Zulu (UTC) milliseconds
     *
     * @return a long Representing the Zulu time (UTC) in milliseconds
     */
    public long getUTCTime() {
        CAL.setTimeInMillis(System.currentTimeMillis());

        return CAL.getTimeInMillis()
                - CAL.get(Calendar.ZONE_OFFSET)
                - CAL.get(Calendar.DST_OFFSET);
    }
}
