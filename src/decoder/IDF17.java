/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IDF17 Interface
 *
 * This is the ADS-B (Extended Squitter) object
 */
public interface IDF17 extends IAlert {
    
    public int getCategory();
    
    public String getCallsign();

    public int getAltitude();

    public int getCapability();
    
    public long getUpdateTime();
}
