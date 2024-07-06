/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

public interface ICallsign {

    /**
     * Given 56 bits, return a Callsign string
     *
     * @param data56 a long representing the raw data bits
     * @return a string representing the callsign text
     */
    public String callsignDecode(long data56);
}
