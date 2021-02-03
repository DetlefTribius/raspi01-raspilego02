/**
 * 
 */
package gui;

import java.math.BigDecimal;

/**
 * @author Detlef Tribius
 *
 */
public class Data implements Comparable<Data>
{

    /**
     * COUNTER_KEY = "counterKey" - Key zum Zugriff auf die Nummer/den Zaehler...
     */
    public final static String COUNTER_KEY = "counterKey";

    /**
     * CYCLE_TIME_KEY = "cycleTimeKey"
     */
    public final static String CYCLE_TIME_KEY = "cycleTimeKey";

    /**
     * TOKEN_KEY = "tokenKey"
     */
    public final static String TOKEN_KEY = "tokenKey";
    
    /**
     * NUMBER_MA_KEY = "numberMAKey"
     */
    public final static String NUMBER_MA_KEY = "numberMAKey";
    
    /**
     * NUMBER_MB_KEY = "numberMBKey"
     */
    public final static String NUMBER_MB_KEY = "numberMBKey";

    /**
     * OUTPUT_MA_KEY = "outputMAKey" - Stellgroesse Motor A
     */
    public final static String OUTPUT_MA_KEY = "outputMAKey";
    
    /**
     * OUTPUT_MB_KEY = "outputMBKey" - Stellgroesse Motor B
     */
    public final static String OUTPUT_MB_KEY = "outputMBKey";
    
    /**
     * Long counter - Zaehler
     */
    private final Long counter;
    
    /**
     * BigDecimal cycleTime - Zyklusdauer
     */
    private final BigDecimal cycleTime;
    
    /**
     * String token
     */
    private final String token;
    
    /**
     * Long numberMA
     */
    private final Long numberMA;
    
    /**
     * Long numberMB
     */
    private final Long numberMB;

    /**
     * SCALE_OUTPUT = 3 - Genauigkeit der Sollwertvorgabe (3 Nachkommastellen)
     * <p>
     * vgl. auch Angabe in Model <code>SCALE_MX_VALUE = 2</code>
     * </p>
     */
    public final static int SCALE_OUTPUT = 3;

    /**
     * BigDecimal outputMA - Stellgroesse Motor A
     */
    private final BigDecimal outputMA;
    
    /**
     * BigDecimal outputMB - Stellgroesse Motor B
     */
    private final BigDecimal outputMB;
    
    /**
     * Data() - Defaultkonstruktor...
     */
    public Data()
    {
        this(0L, 
             BigDecimal.ZERO, 
             0L, 
             0L, 
             0L, 
             BigDecimal.ZERO.setScale(SCALE_OUTPUT),  
             BigDecimal.ZERO.setScale(SCALE_OUTPUT));
    }
    
    /**
     * Data(long counter, BigDecimal cycleTime, long token, long phiSetPoint, int value, long numberMA, long numberMB) - Konstruktor aus allen Attributen...
     * @param counter - Zaehler, keine weitere funktionale Bedeutung
     * @param cycleTime - Zyklusdauer (Regelalgorithmus erfolgt getaktet, T ist Zyklusdauer)
     * @param token - Kennung wird zwischen Arduino und Raspberry ausgetauscht
     * @param numberMA - Lageinformation Motor A
     * @param numberMB - Lageinformation Motor B
     * @param outputMA - Stellgroesse zum Motor A
     * @param outputMB - Stellgroesse zum Motor B
     */
    public Data(long counter, 
                BigDecimal cycleTime, 
                long token,
                long numberMA, 
                long numberMB,
                BigDecimal outputMA,
                BigDecimal outputMB)
    {
        this.counter = Long.valueOf(counter);
        this.cycleTime = (cycleTime != null)? cycleTime : BigDecimal.ZERO;
        this.token = getTokenAsString(token);
        this.numberMA = Long.valueOf(numberMA);
        this.numberMB = Long.valueOf(numberMB);
        this.outputMA = (outputMA != null)? outputMA : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
        this.outputMB = (outputMB != null)? outputMB : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
    }
    
    /**
     * @return the counter
     */
    public final Long getCounter()
    {
        return this.counter;
    }

    /**
     * @return the cycleTime
     */
    public final BigDecimal getCycleTime()
    {
        return this.cycleTime;
    }

    /**
     * @return the token
     */
    public final String getToken()
    {
        return this.token;
    }

    /**
     * @return the numberMA
     */
    public final Long getNumberMA()
    {
        return this.numberMA;
    }

    /**
     * @return the numberMB
     */
    public final Long getNumberMB()
    {
        return this.numberMB;
    }

    /**
     * @return the outputMA
     */
    public final BigDecimal getOutputMA()
    {
        return this.outputMA;
    }

    /**
     * @return the outputMB
     */
    public final BigDecimal getOutputMB()
    {
        return this.outputMB;
    }

    /**
     * getKeys() - liefert den Zugriff auf alle Attribute.
     * <p>
     * Auf alle Attribute muss zugreifbar sein mit <code>getValue(String key)</code>.
     * </p>
     * @return String[]
     */
    public String[] getKeys()
    {
        return new String[] {Data.COUNTER_KEY,
                             Data.CYCLE_TIME_KEY,
                             Data.TOKEN_KEY,
                             Data.NUMBER_MA_KEY,
                             Data.NUMBER_MB_KEY,
                             Data.OUTPUT_MA_KEY,
                             Data.OUTPUT_MB_KEY};
    }    

    /**
     * getValue(String key) - Bereitstellung der Anzeige...
     * @param key
     * @return String-Anzeige
     */
    public final String getValue(String key)
    {
        if (Data.COUNTER_KEY.equals(key))
        {
            return (this.counter != null)? this.counter.toString() : null;
        }
        if (Data.CYCLE_TIME_KEY.equals(key))
        {
            return (this.cycleTime != null)? this.cycleTime.toString() : null;
        }
        if (Data.TOKEN_KEY.equals(key))
        {
            return this.token;
        }
        if (Data.NUMBER_MA_KEY.equals(key))
        {
            return (this.numberMA != null)? this.numberMA.toString() : null;
        }
        if (Data.NUMBER_MB_KEY.equals(key))
        {
            return (this.numberMB != null)? this.numberMB.toString() : null;
        }
        if (Data.OUTPUT_MA_KEY.equals(key))
        {
            return (this.outputMA != null)? this.outputMA.toString() : null;
        }
        if (Data.OUTPUT_MB_KEY.equals(key))
        {
            return (this.outputMB != null)? this.outputMB.toString() : null;
        }
        return null;
    }

    /**
     * getTokenAsString(Long token)
     * @param token Long token
     * <p>
     * Vom long token werden die unteren 4 Byte als hex-String ausgegeben
     * </p>
     * @return String
     */
    public static String getTokenAsString(long token)
    {
        return Long.toHexString((token & 0xffffffff)).toUpperCase(); 
    }

    /**
     * compareTo(Data other)
     */
    @Override
    public int compareTo(Data other)
    {
        return this.counter.compareTo(other.counter);
    }

    /**
     * hashCode() - durch Eclipse nur auf Basis von <code>counter</code>!
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((counter == null) ? 0 : counter.hashCode());
        return result;
    }

    /**
     * equals(Object obj) - durch Eclipse nur auf Basis von <code>counter</code>!
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof Data))
        {
            return false;
        }
        Data other = (Data) obj;
        if (counter == null)
        {
            if (other.counter != null)
            {
                return false;
            }
        } 
        else if (!counter.equals(other.counter))
        {
            return false;
        }
        return true;
    }

    /**
     * toString() - zu Protokollzwecken... (z.B. Logging)
     */
    @Override
    public String toString()
    {
        return new StringBuilder().append("[")
                                  .append(this.counter)
                                  .append(" ")
                                  .append(this.cycleTime)
                                  .append(" ")
                                  .append(this.token)
                                  .append(" ")
                                  .append(this.numberMA)
                                  .append(" ")
                                  .append(this.numberMB)
                                  .append(" ")
                                  .append(this.outputMA)
                                  .append(" ")
                                  .append(this.outputMB)
                                  .append("]")
                                  .toString();
    }
}
