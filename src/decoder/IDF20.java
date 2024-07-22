/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IDF20 Interface
 *
 * Used to provide altitude information
 *
 * The only reason a DF20 will be processed, is if there was a DF11, DF17, or
 * DF18 with a valid CRC parity first. Without a valid CRC parity these packets
 * are ignored.
 */
public interface IDF20 extends IStatus, IAlert {

    public String getCallsign();

    public int getBDS();

    public long getData56();

    public int getAltitude();

    public long getUpdateTime();
}
