/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IDF11 Interface
 *
 * Used as the All Call broadcast
 */
public interface IDF11 {
    
    public boolean getIsOnGround();

    public int getCapability();

    public int getRadarIID();

    public boolean getSI();;       
    
    public long getUpdateTime();
}
