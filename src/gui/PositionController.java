/**
 * 
 */
package gui;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Die Klasse PositionController realisiert den eigentlichen Regelalgorithmus
 * der Gleichlaufregelung. Der Algorithmus wird durch Beauftragung der 
 * doControl()-Methode realisiert. Die notwendigen Parameter werden zuvor
 * gesetzt.
 * 
 * @author Detlef Tribius
 */
public class PositionController
{

    /**
     * logger...
     */
    private final static Logger logger = LoggerFactory.getLogger(PositionController.class);    
    
    /**
     * Darstellung der Taktzeit...
     */
    public static int SCALE_CYCLE_TIME = 3;
    
    /**
     * SCALE_OUTPUT = 3, Genauigkeit fuer die Ausgabewerte (Nachkommastellen)
     */
    public static int SCALE_OUTPUT = 3;
    
    
    public static int SCALE_INTERN = 6;
    
    /**
     * Zeitpunkt der aktuellen Beauftragung...
     */
    private Instant now = null;
    
    /**
     * Zeitpunkt der letzten Beauftragung...
     */
    private Instant past = null;
    
    /**
     * cycleTimeDecimal - Zykluszeit (Abtast-/Regler-Zeit)
     */
    private BigDecimal cycleTimeDecimal = BigDecimal.ZERO;    
    
    /**
     * enhancement - Reglerverstaerkung...
     * <p>
     * Die Reglerverstaerkung kann ueber die Oberflaeche geaendert werden...
     * </p>
     */
    private BigDecimal enhancement;

    /**
     * wheelSteps - Anzahl der Impulse des Gebers pro Umdrehung
     */
    private final int wheelSteps;
    
    /**
     * PositionController(int wheelSteps)
     * @param wheelSteps: Anzahl der Impulse des Gebers pro Umdrehung
     */
    PositionController(int wheelSteps)
    {
        // Es ist wahrscheinlich sinnvoll zu fordern, dass 
        // wheelSteps >= 1 gelten muss! 
        // (Da evtl. Division durch wheelSteps notwendig wird!)
        this.wheelSteps = (wheelSteps > 1)? wheelSteps : 1;
        this.enhancement = BigDecimal.ZERO;
    }
    
    /**
     * setEnhancement(BigDecimal enhancement) - Reglerverstaerkung setzen
     * @param enhancement
     */
    public void setEnhancement(BigDecimal enhancement)
    {
        this.enhancement = (enhancement != null)? enhancement : BigDecimal.ZERO;
    }
    
    /**
     * getEnhancement() - Reglerverstaerkung
     * @return
     */
    public BigDecimal getEnhancement()
    {
        return this.enhancement;
    }
    
    /**
     * doControl() - Regelalgorithmus...
     * @return Output(output)
     */
    public Output doControl(long numberSetPoint,                            // Sollwert
                            long numberMA, long numberMB,                   // jeweiliger Istwert MA, MB 
                            BigDecimal maxValueMA, BigDecimal maxValueMB)   // jeweilige Grenzwerte 
    {
        // diffNumber: Lage-Differenz zwischen Soll - Ist...
        
        // diffNumberMA: Regel-Differenz fuer Motor A, wird zu outputMA...
        final long diffNumberMA = numberSetPoint - numberMA;
        
        // diffNumberMB: Regel-Differenz fuer Motor B, wird zu outputMB...
        final long diffNumberMB = numberSetPoint - numberMB;
        
        // Verstarkungsfaktor, hier gehen die Impulse pro Umdrehung mit ein.
        final BigDecimal p_factor = this.enhancement.divide(BigDecimal.valueOf(this.wheelSteps), SCALE_INTERN, BigDecimal.ROUND_CEILING);
        
        final BigDecimal valueMA = BigDecimal.valueOf(diffNumberMA).multiply(p_factor).setScale(SCALE_OUTPUT, BigDecimal.ROUND_HALF_UP);
        // VZ: -1, 0 oder +1
        final int signumMA = valueMA.signum(); 
        final boolean isLimitMA = valueMA.abs().compareTo(maxValueMA.abs()) > 0;
        final BigDecimal outputMA = isLimitMA? ((signumMA < 0)? maxValueMA.negate() : maxValueMA.setScale(SCALE_OUTPUT)) : valueMA;
        
        final BigDecimal valueMB = BigDecimal.valueOf(diffNumberMB).multiply(p_factor).setScale(SCALE_OUTPUT, BigDecimal.ROUND_HALF_UP);
        // VZ: -1, 0 oder +1
        final int signumMB = valueMB.signum(); 
        final boolean isLimitMB = valueMB.abs().compareTo(maxValueMB.abs()) > 0;
        final BigDecimal outputMB = isLimitMB? ((signumMB < 0)? maxValueMB.negate() : maxValueMB.setScale(SCALE_OUTPUT)) : valueMB;

        // *** Anmerkung ***
        // firstBigDecimal.compareTo(secondBigDecimal) < 0     // "<"
        // firstBigDecimal.compareTo(secondBigDecimal) > 0     // ">"    
        // firstBigDecimal.compareTo(secondBigDecimal) == 0    // "=="  
        // firstBigDecimal.compareTo(secondBigDecimal) >= 0    // ">="
        
        return new Output(BigDecimal.valueOf(diffNumberMA), 
                          BigDecimal.valueOf(diffNumberMB), 
                          outputMA, 
                          outputMB);
    }
    
    
    /**
     * Output - Zusammenfassung des Reglerausgangs
     * 
     * @author Detlef Tribius
     *
     */
    final class Output
    {
        /**
         * BigDecimal diffValueMA
         */
        private final BigDecimal diffValueMA;
        
        /**
         * BigDecimal diffValueMB
         */
        private final BigDecimal diffValueMB;
        
        /**
         * BigDecimal outputMA
         */
        private final BigDecimal outputMA;
        
        /**
         * BigDecimal outputMB
         */
        private final BigDecimal outputMB; 
        
        /**
         * Output() - Konstruktor aus den Attributen...
         * @param diffValue
         * @param outputMA
         * @param outputMB
         */
        public Output(BigDecimal diffValueMA,
                      BigDecimal diffValueMB,
                      BigDecimal outputMA,      
                      BigDecimal outputMB)
        {
            this.diffValueMA = (diffValueMA != null)? diffValueMA : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
            this.diffValueMB = (diffValueMB != null)? diffValueMB : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
            
            this.outputMA = (outputMA != null)? outputMA : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
            this.outputMB = (outputMB != null)? outputMB : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
        }

        /**
         * @return the diffValueMA
         */
        public final BigDecimal getDiffValueMA()
        {
            return this.diffValueMA;
        }

        /**
         * @return the diffValueMB
         */
        public final BigDecimal getDiffValueMB()
        {
            return this.diffValueMB;
        }

        /**
         * @return the outputMA
         */
        public final BigDecimal getOutputMA()
        {
            return outputMA;
        }

        /**
         * @return the outputMB
         */
        public final BigDecimal getOutputMB()
        {
            return outputMB;
        }
        
        /**
         * String toString() - zu Protokollzwecken...
         */
        @Override
        public String toString()
        {
            return new StringBuilder().append("[")
                                      .append(this.diffValueMA.toString())
                                      .append(" ")
                                      .append(this.diffValueMB.toString())
                                      .append(" ")
                                      .append(this.outputMA.toString())
                                      .append(" ")
                                      .append(this.outputMB.toString())
                                      .append("]")
                                      .toString();
        }
    }
}
