/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IDF04 Interface
 *
 * Used to provide altitude information
 *
 * The only reason a DF04 will be processed, is if there was a DF11, DF17, or
 * DF18 with a valid CRC parity first. Without a valid CRC parity these packets
 * are ignored.
 */
public interface IDF04 extends IStatus, IAlert {

    /**
     * Getter to return altitude in feet
     * 
     * Altitude may be in 100 or 25 foot resolution.
     * Returns -9999 (NULL) if error detected.
     * 
     * @return an int representing the altitude in feet
     */
    public int getAltitude();
    
    public long getUpdateTime();
}
