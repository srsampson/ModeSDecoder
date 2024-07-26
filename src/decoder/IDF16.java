/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IDF16 Interface
 *
 * Used with TCAS to exchange altitude, TA,  and RA information
 *
 * TA = Traffic Advisory (no action needed)
 * RA - Resolution Advisory (action needed)
 */
public interface IDF16 {

    public boolean getIsOnGround();

    public int getRI4();

    public long getMV();
    
    public int getBDS();

    /**
     * Getter to return altitude in feet
     *
     * Altitude may be in 100 or 25 foot resolution. Returns -9999 if error
     * detected.
     *
     * @return an int representing the altitude in feet
     */
    public int getAltitude();
}
