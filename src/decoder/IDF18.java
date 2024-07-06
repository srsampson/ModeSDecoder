/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

/*
 * This is the IDF18 Interface
 *
 * This is the TIS-B object
 */
public interface IDF18 extends IAlert {
    
    public int getCategory();
    
    public String getCallsign();

    public int getAltitude();

    public int getControlField();
    
    public long getUpdateTime();
}
