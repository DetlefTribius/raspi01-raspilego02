package gui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinEdge;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import gui.Model.GuiStatus;
import raspi.hardware.i2c.ArduinoI2C;
import raspi.hardware.i2c.MotorDriverHAT;

// Vgl. https://www.baeldung.com/java-observer-pattern
// auch https://wiki.swechsler.de/doku.php?id=java:allgemein:mvc-beispiel
// http://www.nullpointer.at/2011/02/06/howto-gui-mit-swing-teil-4-interaktion-mit-der-gui/
// http://www.javaquizplayer.com/blogposts/java-propertychangelistener-as-observer-19.html
// TableModel...
// Vgl.: https://examples.javacodegeeks.com/core-java/java-swing-mvc-example/
/**
 * <p>
 * Das Model haelt die Zustandsgroessen..
 * </p>
 * <p>
 * Fuer jede Zustandsgroesse des Modells wird ein Attribut eingefuehrt.  
 * </p>
 * <p>
 * <ul>
 *  <li>Zaehler counter</li>
 *  <li>Taktdauer cycleTime</li>
 * </ul>
 * </p>
 * 
 */
public class Model 
{
    /**
     * logger
     */
    private final static Logger logger = LoggerFactory.getLogger(Model.class);
    
    /**
     * Kennung isRaspi kennzeichnet, der Lauf erfolgt auf dem RasberryPi.
     * Die Kennung wird zur Laufzeit aus den Systemvariablen fuer das
     * Betriebssystem und die Architektur ermittelt. Mit dieser Kennung kann
     * die Beauftragung von Raspi-internen Programmen gesteuert werden.
     */
    private final boolean isRaspi;
    /**
     * OS_NAME_RASPI = "linux" - Kennung fuer Linux.
     * <p>
     * ...wird verwendet, um einen Raspi zu erkennen...
     * </p>
     */
    public final static String OS_NAME_RASPI = "linux";
    /**
     * OS_ARCH_RASPI = "arm" - Kennung fuer die ARM-Architektur.
     * <p>
     * ...wird verwendet, um einen Raspi zu erkennen...
     * </p>
     */
    public final static String OS_ARCH_RASPI = "arm";

    /**
     * MD_HAT_ADDRESS - Bus-Adresse des MotorDriverHAT, festgelegt durch
     * Verdrahtung auf dem Baustein, Standard-Vorbelegung lautet: 0x40.
     * <p>
     * Vergleiche "MotorDriver HAT User Manual": ...The address range from 0x40 to 0x5F. 
     *  </p>
     */
    public final static int MD_HAT_ADDRESS = 0x40; 
    
    /**
     * MD_HAT_FREQUENCY = 100
     */
    public final static int MD_HAT_FREQUENCY = 100;

    /**
     * motorDriverHAT - Referenz auf den MotorDriverHAT...
     */
    private final MotorDriverHAT motorDriverHAT;
    
    /**
     * counter - Taktzaehler (keine weitere funktionale Bedeutung)
     * <p>
     * Der <b><code>counter</code></b> dient zur Zuordnung der Zustandsgroessen
     * aus  Data(). Mit jeder Taktung (Beauftragung durch den Arduino) wird
     * der Zaehler weitergezaehlt. Er hat erst einmal keine weitere Bedeutung
     * in der Kommunikation mit dem Arduino.
     * </p>
     */
    private long counter = 0L;
    
    /**
     * cycleTime - Zykluszeit (Taktzeit der Beauftragung durch den Arduino), 
     * wird durch Differenzbildung (vgl. this.past) ermittelt...
     */
    private BigDecimal cycleTime = BigDecimal.ZERO;
    
    /**
     * SCALE_CYCLE_TIME = 3 - Genauigikeit bei der Darstellung der Zeit 
     * (z.B. Taktzeit, derzeit 3 Nachkommastellen)... 
     */
    public static int SCALE_CYCLE_TIME = 3;
    
    /**
     * Instant past - der letzter Zeitstempel...
     * <p>
     * Der Takt wird durch den ArduinoI2C Uno vorgegeben. 
     * In past wird der letzte Zeitstempel abgelegt 
     * zur Bestimmung der Taktdauer T zwischen now und this.past. 
     * </p>
     */
    private Instant past = null;
    
    /**
     * Referenz auf den GPIO-controller...
     * <p>
     * Der GPIO-Controller bedient die GPIO-Schnittstelle des Raspi.
     * </p>
     */
    private final GpioController gpioController;

    /**
     * ARDUINO_ADDRESS - Bus-Adresse des ArduinoI2C, 
     * festgelegt durch Software auf dem ArduinoI2C... 
     */
    public final static int ARDUINO_ADDRESS = 0x08; 
       
    /**
     * arduinoI2C - Referenz auf Hilfsklasse zur Kommunikation 
     * mit dem Arduino. 
     * <p>
     * Raspberry ist der I2C-Master, Arduino der
     * I2C-Slave, angestossen wird die Kommunikation aber durch
     * einen Takt durch den Arduino (Zykluszeit auf dem Arduino
     * einstellbar).
     * </p>
     */
    private final ArduinoI2C arduinoI2C;
    
    /**
     * i2cStatus - Status der Kommunikation mit dem Arduino
     * <p>
     * <ul>
     * <li><b>NOP</b> - Keine Kommunikation, z.B. nach Programmstart, vor Start-Button</li>
     * <li><b>INITIAL</b> - Erste Beauftragung, Raspi ist zur Kommunikation bereit, nach Start-Button, als token wird 0 gesendet</li>
     * <li><b>SUCCESS</b> - Erfolgreiche Erstbeauftragung, der token wird jeweils im Arduino erhoeht...</li>
     * <li><b>ERROR</b> - Fehler </li>
     * </ul>
     * </p>
     */
    private ArduinoI2C.Status i2cStatus = ArduinoI2C.Status.NOP;
    
    /**
     * token - Kennung zur Identifizierung von Nachrichten zwischen
     * Raspi und Arduino
     * <p>
     * Der <b><code>token</code></b> dient zur Zuordnung einzelner Nachrichten
     * zwischen Arduino und Raspberry Pi.
     * </p>
     * <p>
     * <ul>
     * <li>Raspi wird immer vom Arduino getaktet.</li>
     * <li>Der Raspi protokolliert dann der erfolgreichen Erhalt der letzten Message vom
     * Arduino durch Mitgabe des tokens aus dieser Message und der Statusinformation SUCCESS</li>
     * <li>Der Arduino schickt dann in der Antwort den naechsten token mit den neuen Nachricht an den
     * Raspi...</li> 
     * </ul>
     * </p>
     */
    private long token = 0L;
    
    /**
     * DESTINATION_KEY = "destinationKey - Key zum Zugriff auf den Sollwert der Zielgroesse (Lage)
     * <p>
     * Die Zielgroesse fuer die Lage wird an der Oberflaeche als Anzahl Umdrehungen angegeben.
     * Die Eingabewerte werden in das Model uebertragen und finden sich unter dem Key DESTINATION_KEY
     * in der Map dataMap.
     * </p>
     */
    public final static String DESTINATION_KEY = "destinationKey";
    
    /**
     * FORMATTED_TEXT_FIELD_PATTERN = "#0.00" - Formatstring fuer das JFormattedTextField...
     * <p>
     * Beispielsweise: FORMATTED_TEXT_FIELD_PATTERN = "#0.000"
     * </p>
     */
    public final static String FORMATTED_TEXT_FIELD_PATTERN = "#0.0";

    /**
     * NUMBER_SET_POINT_KEY = "numberSetPointKey" - Key zum Lagesollwert 
     * gemessen in Impulsen 
     */
    public final static String NUMBER_SET_POINT_KEY = "numberSetPointKey";
    
    /**
     * numberSetPoint - Sollwert fuer die Lage (Sollwert) in Impulse angegeben
     * <p>
     * <code>numberSetPoint</code> ergibt sich aus
     * numberSetPoint = CIRCUMFERENCE (d.i. Anzahl der Impulse pro Umdrehung) * destination
     * </p>
     */
    private long numberSetPoint = 0L;
    
    /**
     * totalMA[] - totale Impuls-Zaehler-Staende Motor A
     * <p>
     * totalMA[1] - aktueller Wert[k], totalMA[0] - historischer Wert[k-1] 
     * </p>
     */
    private long totalMA[] = { 0L, 0L };
    
    /**
     * controlMA[] - Reglerausgang zum Motor A
     * <p>
     * controlMA[0] - historischer Wert[k-1], wichtig zur Ermittlung des VZ
     * </p>
     */
    private BigDecimal controlMA[] = { BigDecimal.ZERO, BigDecimal.ZERO };
    
    /**
     * long numberMA - Lageinformation Motor A...
     */
    private long numberMA = 0L;

    /**
     * totalMB[] - totale Impuls-Zaehler-Staende Motor B
     * <p>
     * totalMB[1] - aktueller Wert[k], totalMB[0] - historischer Wert[k-1] 
     * </p>
     */
    private long totalMB[] = { 0L, 0L };
    
    /**
     * controlMB[] - Reglerausgang zum Motor B
     * <p>
     * controlMB[0] - historischer Wert[k-1], wichtig zur Ermittlung des VZ
     * </p>
     */
    private BigDecimal controlMB[] = { BigDecimal.ZERO, BigDecimal.ZERO };
    
    /**
     * long numberMB - Lageinformation Motor B...
     */
    private long numberMB = 0L;
    
    /**
     * maxValueMA - Maximalwert des Sollwertes fuer
     * Motor A, Vorgabe durch die GUI
     * <p>
     * Bereich maxValueMA (Limit): -1.0 ... 0.0 ... +1.0
     * </p>
     */
    private BigDecimal maxValueMA = BigDecimal.ZERO;
    
    /**
     * maxValueMB - Maximalwert des Sollwertes fuer
     * Motor B, Vorgabe durch die GUI
     * <p>
     * Bereich maxValueMA (Limit): -1.0 ... 0.0 ... +1.0
     * </p>
     */
    private BigDecimal maxValueMB = BigDecimal.ZERO;
    
    /**
     * valueMA - Sollwert (Pwm-Vorgabe) Motor A, wird durch die GUI vorgegeben
     * <p>
     * Bereich valueMA (Sollwert): -1.0 ... 0.0 ... +1.0
     * </p>
     */
    private BigDecimal valueMA = BigDecimal.ZERO;
    
    /**
     * valueMB - Sollwert (Pwm-Vorgabe) Motor B, wird durch die GUI vorgegeben
     * <p>
     * Bereich valueMB (Sollwert): -1.0 ... 0.0 ... +1.0
     * </p>
     */
    private BigDecimal valueMB = BigDecimal.ZERO;
    
    /**
     * boolean isControlled - boolsche Kennung: Regelung ja/nein...
     */
    private boolean isControlled = false;
    
    /**
     * outputMA - Stellgroesse Motor A
     * <p>
     * Bereich outputMA: -1.0 ... 0.0 ... +1.0 
     * </p>
     */
    private BigDecimal outputMA;
    
    /**
     *  outputMB - Stellgroesse Motor B
     * <p>
     * Bereich outputMB: -1.0 ... 0.0 ... +1.0 
     * </p>
     */
    private BigDecimal outputMB;

    /**
     * SCALE_OUTPUT = 3 - Genauigkeit (Anzahl der Nachkommastellen) der Ausgabe an den HAT
     */
    public final static int SCALE_OUTPUT = 3;
    
    /**
     * CIRCUMFERENCE - Anzahl der Impulse des Gebers pro Umdrehung
     * 
     * Aus der Anzahl der Impulse I pro Zeiteinheit T ergibt sich die
     * Umdrehungszahl U pro Minute zu:
     * 
     *   U = I * 1/CIRCUMFERENCE * 60/T
     *   U = (I/T) * (60/CIRCUMFERENCE) 
     */
    public final static int CIRCUMFERENCE = 6;
    
    /**
     * SCALE_INTERN = 6 - Genauigkeit interner Daten.
     */
    public final static int SCALE_INTERN = 6;
    
    /**
     * RPM_CONST - Parameter, ergibt sich aus (Impulsanzahl pro Umdrehung)/(60L) zur weiteren verwendung...
     */
    public final static BigDecimal RPM_CONST = BigDecimal.valueOf(CIRCUMFERENCE).divide(BigDecimal.valueOf(60L), SCALE_INTERN, BigDecimal.ROUND_DOWN);

    /**
     * positionController - Referenz auf den Regler...
     */
    private final PositionController positionController = new PositionController(CIRCUMFERENCE);
    
    /**
     * Pull-Up/Pull-Down-Einstellung...
     * <p>
     * Hier Voreinstellung auf PinPullResistance.OFF, da Pull-Down-Widerstaende 
     * durch die Hardware bereitgestellt werden...
     * </p>
     * <p>
     * Hier Einstellung Kein Pull-Down/Pull-Up durch den Raspi...
     * </p>
     */
    private final static PinPullResistance PIN_PULL_RESISTANCE = PinPullResistance.OFF;
    
    /**
     * GPIO_CYCLE_PIN - der Pin wird durch den ArduinoI2C UNO getaktet...
     */
    private final static Pin GPIO_CYCLE_PIN = RaspiPin.GPIO_04;    // GPIO23 (GPIO_GEN4), Board-Nr=16
    
    /**
     * GPIO_CYCLE_PIN_NAME - Name des GPIO_CYCLE_PIN
     */
    private final static String GPIO_CYCLE_PIN_NAME = GPIO_CYCLE_PIN.getName();
    
    /**
     * GPIO_PINS - ...die folgenden (Ausgabe-) Pins werden angesprochen...
     * <p>
     * Es handel sich um Output-Pins!
     * </p>
     */
    private final static Pin[] GPIO_PINS = 
    {
        // Beispielsweise: RaspiPin.GPIO_00    // GPIO 17, Board-Nr=11
    };
    
    /**
     * PIN_NAMES - String-Array mit den Namen der Raspi Ausgabe-Pin's.
     * Das Array wird aus dem Array GPIO_PINS[] befuellt.
     */
    public final static String[] PIN_NAMES = new String[GPIO_PINS.length];
    
    static 
    {
        // Befuellen des Arrays PIN_NAMES[] aus GPIO_PINS[]...
        for(int index = 0; index < GPIO_PINS.length; index++)
        {
            PIN_NAMES[index] = GPIO_PINS[index].getName();
        }
    }
  
    /**
     * gpioPinDigitalInputCyclePin haelt das GpioPinDigitalInput-Objekte.
     * <p>
     * Es handelt sich um ein Input-Objekt. Daher wird die Referenz direkt 
     * abgelegt. Bei mehreren Input-Objekten wuerde es sich anbieten, eine
     * Map anzulegen...
     * </p>
     * <p>
     * Ueber die gpioPinDigitalInputCyclePin-Referenz erfolgt die zyklische
     * Beauftragung der Regelung. Der Zyklus wird dabei vom Arduino vorgegeben. 
     * </p>
     */
    private final GpioPinDigitalInput gpioPinDigitalInputCyclePin;

    /**
     * gpioPinOutputMap nimmt die GpioPinDigitalOutput-Objekte auf, 
     * Key ist dabei jeweils der Pin_Name, z.B. "GPIO 21"...
     * <p>
     * Verwendung: Unter dem Key 'Name des GPIO' wird die Referenz 
     * auf den Pin abgelegt. 
     * </p>
     * <p>
     * Diese Map wird im weiteren nicht verwendet, steht nur als Muster
     * bereit...
     * </p>
     */
    private final java.util.TreeMap<String, GpioPinDigitalOutput> gpioPinOutputMap = new java.util.TreeMap<>();
    
    /**
     * NAME_START_BUTTON = "startButton"
     */
    public static final String NAME_START_BUTTON = "startButton";

    /**
     * NAME_STOP_BUTTON = "stopButton"
     */
    public static final String NAME_STOP_BUTTON = "stopButton";
    
    /**
     * NAME_RESET_BUTTON = "resetButton"
     */
    public static final String NAME_RESET_BUTTON = "resetButton";
    
    /**
     * NAME_END_BUTTON = "endButton"
     */
    public final static String NAME_END_BUTTON = "endButton";
    
    /**
     * dataMap - nimmt die Eingaben auf...
     * <p>
     * Ablage key => Eingabe-Object
     * </p>
     */
    private final java.util.TreeMap<String, Object>  dataMap = new java.util.TreeMap<>();

    /**
     * DATA_KEY = "dataKey" - Key unter dem die Data in der dataMap abgelegt werden...
     * <p>
     * Data umfasst die Zustandsgroessen, die in der View angezeigt werden.
     * </p>
     */
    public final static String DATA_KEY = "dataKey";
    
    /**
     * MAX_VALUE_MA_KEY = "maxValueMAKey"
     * <p>
     * MAX_VALUE_MA_KEY referenziert die Zustandsgroesse maxValueMA
     * </p>
     */
    public final static String MAX_VALUE_MA_KEY = "maxValueMAKey";
    
    /**
     * MAX_VALUE_MB_KEY = "maxValueMBKey"
     * <p>
     * MAX_VALUE_MB_KEY referenziert die Zustandsgroesse maxValueMB
     * </p>
     */
    public final static String MAX_VALUE_MB_KEY = "maxValueMBKey";
    
    /**
     * VALUE_MA_KEY = "valueMAKey"
     */
    public final static String VALUE_MA_KEY = "valueMAKey";
    
    /**
     * VALUE_MB_KEY = "valueMBKey"
     */
    public final static String VALUE_MB_KEY = "valueMBKey";
    
    /**
     * OUTPUT_MA_KEY = "outputMAKey"
     */
    public final static String OUTPUT_MA_KEY = "outputMAKey";
    
    /**
     * OUTPUT_MB_KEY = "outputMBKey"
     */
    public final static String OUTPUT_MB_KEY = "outputMBKey";
    
    /**
     * CONTROL_KEY = "controlKey" - Boolscher Schalter 'Mit Regelung'
     */
    public final static String CONTROL_KEY = "controlKey";

    /**
     * ENHANCEMENT_KEY = "enhancementKey" - Combobox mit den Reglerverstaerkungen...
     */
    public final static String ENHANCEMENT_KEY = "enhancementKey";
    
    /**
     * GUI_STATUS_KEY = "guiStatusKey" - Im GuiStatus wird abgelegt im welchem
     * "Bedienungszustand" die Gui ist.
     */
    public final static String GUI_STATUS_KEY = "guiStatusKey";
    
    /**
     * DATA_KEYS[] - Array mit den Keys zur Ablage in der dataMap...
     */
    private final static String[] DATA_KEYS = 
    {
        DATA_KEY,
        DESTINATION_KEY,
        NUMBER_SET_POINT_KEY,
        MAX_VALUE_MA_KEY,
        MAX_VALUE_MB_KEY,
        VALUE_MA_KEY,
        VALUE_MB_KEY,
        OUTPUT_MA_KEY,
        OUTPUT_MB_KEY,
        CONTROL_KEY,
        ENHANCEMENT_KEY,
        GUI_STATUS_KEY
    };
    
    /**
     * SCALE_MX_VALUE = 2 - Genauigkeit der Sollwertvorgabe (2 Nachkommastellen)
     */
    public final static int SCALE_MX_VALUE = 2;
    
    /**
     * MX_VALUES - Vorgaben fuer die ComboBoxen VALUE_MA_KEY, VALUE_MB_KEY
     */
    public final static BigDecimal[] MX_VALUES = new BigDecimal[]
    {
        BigDecimal.valueOf(1.0).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.9).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.8).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.7).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.6).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.5).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.4).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.3).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.2).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.1).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.0).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.1).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.2).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.3).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.4).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.5).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.6).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.7).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.8).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(-0.9).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(-1.0).setScale(SCALE_MX_VALUE, BigDecimal.ROUND_HALF_UP)                    
    };
    
    /**
     * Index zur Auswahl der Selektion...
     */
    public final static int SELECTED_MX_VALUES_INDEX = 10;
   
    /**
     * SCALE_MX_MAX_VALUE = 2 - Genauigkeit der Limit-Vorgabe Mx_Max_Value (2 Nachkommastellen)
     */
    public final static int SCALE_MX_MAX_VALUE = 2;
    
    /**
     * MX_MAX_VALUES - Vorgaben fuer die ComboBoxen MAX_VALUE_MA_KEY, MAX_VALUE_MB_KEY
     */
    public final static BigDecimal[] MX_MAX_VALUES = new BigDecimal[]
    {
        BigDecimal.valueOf(1.0).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.9).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.8).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.7).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.6).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.5).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.4).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.3).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.2).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.1).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP),                    
        BigDecimal.valueOf(0.0).setScale(SCALE_MX_MAX_VALUE, BigDecimal.ROUND_HALF_UP)                   
    };
    
    /**
     * Index zur Vor-Auswahl der Selektion...
     */
    public final static int SELECTED_MX_MAX_VALUES_INDEX = 10;

    
    /**
     * Genauigkeit (Anzahl der Nachkommastellen) in der Verstaerkungsangabe
     */
    public final static int  SCALE_ENHANCEMENT = 4;
    
    /**
     * ENHANCEMENTS - Array mit den Verstaerkungswerten des Reglers (P-Anteil) 
     * zur Auswahl in der Combobox...
     */
    public final static BigDecimal[] ENHANCEMENTS = new BigDecimal[]
    {
        BigDecimal.valueOf(0.000).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.002).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.005).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.010).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.020).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.050).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.100).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.200).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.300).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(0.500).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(1.000).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(2.000).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(5.000).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP),
        BigDecimal.valueOf(10.00).setScale(SCALE_ENHANCEMENT, BigDecimal.ROUND_HALF_UP)
    };
    
    /**
     * Index zur Auswahl der Selektion...
     */
    public final static int SELECTED_ENHANCEMENTS_INDEX = 0;
    
    /**
     * support - Referenz auf den PropertyChangeSupport...
     */
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    
    /**
     * Default-Konstruktor 
     */
    public Model()
    {
        // Zuallererst: Wo erfolgt der Lauf, auf einem Raspi?
        final String os_name = System.getProperty("os.name").toLowerCase();
        final String os_arch = System.getProperty("os.arch").toLowerCase();
        logger.debug("Betriebssytem: " + os_name + " " + os_arch);
        // Kennung isRaspi setzen...
        this.isRaspi = OS_NAME_RASPI.equals(os_name) && OS_ARCH_RASPI.equals(os_arch);
        
        // ...den gpioController anlegen...
        this.gpioController = isRaspi? GpioFactory.getInstance() : null;
        
        // *** Befuellen der dataMap... ***
        // Die dataMap muss mit allen Key-Eintraegen befuellt werden, sonst 
        // ist setProperty(String key, Object newValue) unwirksam!
        for (String key: Model.DATA_KEYS)
        {
            this.dataMap.put(key, null);
        }
        
        {
            ArduinoI2C arduinoLoc = null;
            MotorDriverHAT motorDriverHATLoc = null;
            try
            {
                // i2cBus wird nicht in Instanzvariable abgelegt, da ueber I2CFactory erreichbar!
                final I2CBus i2cBus = isRaspi? I2CFactory.getInstance(I2CBus.BUS_1) : null;
                // Verbindung zum Arduino instanziieren...
                arduinoLoc = isRaspi? (new ArduinoI2C((i2cBus != null)? i2cBus.getDevice(ARDUINO_ADDRESS) : null)) 
                                     : null;
                
                // MotorDriverHAT instanziieren (auf der Adresse und mit der Frequenz)...
                motorDriverHATLoc = isRaspi? (new MotorDriverHAT(((i2cBus != null)? i2cBus.getDevice(MD_HAT_ADDRESS) : null),
                                                                 MD_HAT_FREQUENCY))
                                            : null;
            }
            catch (UnsupportedBusNumberException | IOException exception)
            {
                logger.error(exception.toString(), exception);
                System.err.println(exception.toString());
                System.exit(0);
            }
            this.arduinoI2C = arduinoLoc;
            // Status der Kommunikation auf NOP und token auf 0L...
            this.i2cStatus = ArduinoI2C.Status.NOP;
            this.token = 0L;
            
            this.motorDriverHAT = motorDriverHATLoc;
        }
        
        {
            //////////////////////////////////////////////////////////////////////////
            // Input-Pins einstellen (plus Eventhandling)...
            if (isRaspi)
            {
                // *** Zugriff auf die Input-Pin nur wenn Lauf auf dem Raspi... ***
                GpioPinDigitalInput gpioInputPin = this.gpioController.provisionDigitalInputPin(Model.GPIO_CYCLE_PIN, 
                                                                                                Model.GPIO_CYCLE_PIN_NAME, 
                                                                                                Model.PIN_PULL_RESISTANCE);
                // Event-Handler (Listener) instanziieren...
                gpioInputPin.addListener(new GpioPinListenerDigital() 
                {
                    /**
                     * Event-Verarbeitung angestossen durch den  ArduinoI2C-Uno...
                     * <p>
                     * Der Handler wird in einem festen Takt durch den Arduino beauftragt.
                     * Innerhalb des Handlers ist die Kommunikation mit dem Arduino und die
                     * Berechnung der Regelalgorithmen vorzunehmen.
                     * </p>
                     */
                    @Override
                    public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event)
                    {
                        final GpioPin gpioPin = event.getPin();
                        final String pinName = gpioPin.getName();
                        final PinEdge pinEdge = event.getEdge();
                        // Reaktion erfolgt an der steigenden Flanke...
                        if (PinEdge.RISING == pinEdge)
                        {
                            //////////////////////////////////////////////////////////////////////////
                            // Die Taktung durch den ArduinoI2C hat einen Referenzpunkt 
                            // erreicht.
                            // Variable now dient zur zeitlichen Einordnung des Ereignisses...
                            // Jetzt werden die Kenngroesse der Taktung ermittelt:
                            // - now: der jetzige Zeitpunkt, 
                            // -      die Zeitdauer ergibt sich dann
                            //        durch Differenzbildung zu Model.this.past...
                            // now wird im weiteren Verlauf im Zustand Model.this.past 
                            // abgelegt. 
                            final Instant now = Instant.now();
                            
                            // Model.this.past: Zeitpunkt der letzten Taktung...
                            if (Model.this.past == null)
                            {
                                // Erste Beauftragung: Model.this.past = null...
                                Model.this.past = now;
                            }
                            // Model.this.cycleTime: Taktzeit aus der Differenz now - past.
                            // Ablage der aktuell gemessenen Taktzeit in der Zustandsgroesse cycleTime...
                            Model.this.cycleTime = toBigDecimalSeconds(Duration.between(Model.this.past, now), 
                                                                       Model.SCALE_CYCLE_TIME); 
                            
                            // ...und Ablage des aktuelle Zeitpunktes...
                            Model.this.past = now;
                            //////////////////////////////////////////////////////////////////////////
                            
                            if (Model.this.dataMap.containsKey(Model.DATA_KEY))
                            {
                                // Die Beauftragung durch Inkrementierung des Zaehlers 
                                // Model.this.counter 'dokumentieren'...
                                // Die dataMap haelt die Daten zur Anzeige in der View, hier DATA_KEY => Data(),
                                // und Data() beinhaltet den aktuellen counter (und weiteres...)
                                
                                // Model.this.counter inkrementieren oder zu 1L setzen...
                                Model.this.counter = ((Model.this.counter + 1L) > 0L)? (Model.this.counter + 1L) : 1L;  
                                
                                final Data data = new Data(Model.this.counter, 
                                                           Model.this.cycleTime,
                                                           Model.this.token,
                                                           Model.this.numberMA,
                                                           Model.this.numberMB,
                                                           Model.this.outputMA,
                                                           Model.this.outputMB); 
                                setProperty(Model.DATA_KEY, data);
                            }
                            else
                            {
                                
                            }
                            label:
                            {
                                //////////////////////////////////////////////////////////////////////////////
                                // Es folgt die Beauftragung der Kommunikation mit dem Arduino...
                                // 1.) Wenn statusI2C == NOP, dann keine Beauftragung...
                                //
                                if (ArduinoI2C.Status.NOP == Model.this.i2cStatus)
                                {
                                    break label;
                                }
                                if (ArduinoI2C.Status.INITIAL == Model.this.i2cStatus)
                                {
                                    // INITIAL wurde durch den Start-Button gesetzt.
                                    // 1.) Als token 0L einstellen...
                                    Model.this.token = 0L;
                                    // 2.) Kommunikation beginnen...
                                }
                                try
                                {
                                    //////////////////////////////////////////////////////////////////////////
                                    // tokenToArduino: Lokale Variable, die vier unteren Bytes 
                                    //                 der long-Instanzvariable this.token...
                                    final long tokenToArduino = (Model.this.token & 0xffffffff);
                                    Model.this.arduinoI2C.write(token, Model.this.i2cStatus);
                                    logger.debug("i2c-Bus: " + tokenToArduino + " gesendet...");
                                    
                                    ArduinoI2C.DataRequest request = Model.this.arduinoI2C.read();
                                    logger.debug("i2c-Bus: " + request.toString() + " gelesen...");
                                    final long tokenFromArduino = request.getToken();
                                    final ArduinoI2C.Status statusFromArduino = request.getStatus();
                                    // valueFromArduino beinhaltet die 4 Byte-Variante der Daten vom Arduino...
                                    final int valueFromArduino = request.getValue();
                                    // numberMAFromArduino: Anzahl Impulse Motor A...
                                    final int numberMAFromArduino = request.getNumberMA();
                                    // numberMBFromArduino: Anzahl Impulse Motor B...
                                    final int numberMBFromArduino = request.getNumberMB();
                                    // Der Arduino wird den token inkrementieren und als
                                    // neuen Token zurueckschicken. Wenn die Differenz
                                    // gleich 1L ist, kann man davon ausgehen, dass auf
                                    // dem Arduino alles korrekt laeuft...
                                    if ((tokenFromArduino - tokenToArduino == 1L) 
                                     && (ArduinoI2C.Status.SUCCESS == statusFromArduino))
                                    {
                                        Model.this.i2cStatus = ArduinoI2C.Status.SUCCESS;
                                        Model.this.token = (tokenFromArduino & 0xffffffff);
                                        
                                        // "Umschiften..."
                                        Model.this.totalMA[0] = Model.this.totalMA[1];
                                        Model.this.totalMA[1] = numberMAFromArduino;
                                        Model.this.controlMA[0] = Model.this.controlMA[1];
                                        // diffMA => Zuwachs Motor A:
                                        final long diffMA = Model.this.totalMA[1] - Model.this.totalMA[0];
                                                
                                        Model.this.totalMB[0] = Model.this.totalMB[1];
                                        Model.this.totalMB[1] = numberMBFromArduino;
                                        Model.this.controlMB[0] = Model.this.controlMB[1];
                                        // diffMB => Zuwachs Motor B:
                                        final long diffMB = Model.this.totalMB[1] - Model.this.totalMB[0];
                                        
                                        final int signumMA = Model.this.controlMA[0].signum();
                                        final int signumMB = Model.this.controlMB[0].signum();
                                        
                                        // numberMA/numberMB - absolute Lage der Motoren in Impulse:
                                        Model.this.numberMA += signumMA * diffMA;
                                        Model.this.numberMB += signumMB * diffMB;
                                        
                                        final String msg = new StringBuilder().append("Sollwert=")
                                                                              .append(Model.this.numberSetPoint)
                                                                              .append(", Istwerte: ")
                                                                              .append(Model.this.numberMA)
                                                                              .append(" ")
                                                                              .append(Model.this.numberMB)
                                                                              .append(", Limitierungen: ")
                                                                              .append(Model.this.maxValueMA)
                                                                              .append(" ")
                                                                              .append(Model.this.maxValueMB)
                                                                              .toString();
                                        
                                        logger.debug(msg);
                                        
                                        final PositionController.Output output = Model.this.getPositionController().doControl(Model.this.numberSetPoint,
                                                                                                                              Model.this.numberMA, Model.this.numberMB,
                                                                                                                              Model.this.maxValueMA, Model.this.maxValueMB);
                                        
                                        logger.debug("doControl(): " + output.toString());
                                        
                                        Model.this.outputMA = Model.this.isControlled? output.getOutputMA() : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
                                        Model.this.outputMB = Model.this.isControlled? output.getOutputMB() : BigDecimal.ZERO.setScale(SCALE_OUTPUT);
                                        
                                        // outputMA und outputMB merken...
                                        Model.this.controlMA[1] = Model.this.outputMA;
                                        Model.this.controlMB[1] = Model.this.outputMB;
                                        
                                        //
                                        final float speedMA = ((Model.this.outputMA != null)? Model.this.outputMA.floatValue() : 0.0F);
                                        final float speedMB = ((Model.this.outputMB != null)? Model.this.outputMB.floatValue() : 0.0F);
                                        
                                        Model.this.motorDriverHAT.setPwmMA(speedMA);
                                        Model.this.motorDriverHAT.setPwmMB(speedMB);
                                        
                                    }
                                    else
                                    {
                                        Model.this.i2cStatus = ArduinoI2C.Status.ERROR;
                                        
                                        Model.this.motorDriverHAT.setPwmMA(0.0F);
                                        Model.this.motorDriverHAT.setPwmMB(0.0F);
                                    }
                                } 
                                catch (IOException exception)
                                {
                                    logger.error(exception.toString(), exception);
                                    System.err.println(exception.toString());
                                }
                            }
                            //
                            //////////////////////////////////////////////////////////////////////////
                            
                            {
                                //////////////////////////////////////////////////////////////////////////////////////////////////
                                // Testausgabe: Dauer der Bearbeitung von handleGpioPinDigitalStateChangeEvent() von 0.001 ... 0.006s
                                // final BigDecimal duration = toBigDecimalSeconds(Duration.between(Model.this.past, Instant.now()), 
                                //                                                 Model.SCALE_CYCLE_TIME); 
                                // Evtl. Log-Ausgabe...
                                // logger.debug("Dauer handleGpioPinDigitalStateChangeEvent() in s: " + duration);
                                //////////////////////////////////////////////////////////////////////////////////////////////////
                            }
                        } // end() - (PinEdge.RISING == pinEdge).
                    }
                    
                    /**
                     * toBigDecimalSeconds(Duration duration) - liefert die Anzahl der Sekunden
                     * <p>
                     * Vgl. toBigDecimalSeconds() aus Duration in Java 11.
                     * </p<
                     * @param duration
                     * @return
                     */
                    private BigDecimal toBigDecimalSeconds(Duration duration, int scale)
                    {
                        Objects.requireNonNull(duration, "duration must not be null!");
                        final BigDecimal result = BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9)).setScale(scale,  BigDecimal.ROUND_HALF_UP);
                        return (result.compareTo(BigDecimal.ONE.movePointLeft(scale)) < 0)? BigDecimal.ZERO : result;   
                    }
                });
                this.gpioPinDigitalInputCyclePin = gpioInputPin;
                // Ablage eines "leeren (Default-)" Data-Objektes in der dataMap...
                // Dem Key Model.DATA_KEY wird beispielsweise das Value Long.valueOf(0L) zugeordnet.
                setProperty(Model.DATA_KEY, new Data());
                logger.debug(Model.DATA_KEY + " in dataMap gesetzt.");                
            }
            else
            {
                this.gpioPinDigitalInputCyclePin = null;
                setProperty(Model.DATA_KEY, new Data());                
                logger.debug(Model.DATA_KEY + " in dataMap mit value=null aufgenommen.");
            }
            //////////////////////////////////////////////////////////////////////////
        }
        
        //////////////////////////////////////////////////////////////////////////
        // Output-pins beruecksichtigen...
        // Wenn Output, dann wird jeder Pin entsprechend konfiguriert
        // und ein Boolsche Wert (als Datenhaltung) zugeordnet...
        for (Pin pin: Model.GPIO_PINS)
        {
            final String key = pin.getName();
            this.dataMap.put(key, Boolean.FALSE);
            logger.debug(key + " in dataMap aufgenommen.");
            if (isRaspi)
            {
                // Zugriff auf die Pin nur wenn Lauf auf dem Raspi...
                GpioPinDigitalOutput gpioPin = this.gpioController.provisionDigitalOutputPin(pin, key, PinState.LOW);
                gpioPin.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
                this.gpioPinOutputMap.put(key, gpioPin);
            } 
            else
            {
                // Der Lauf erfolgt nicht auf dem Raspi...
                this.gpioPinOutputMap.put(key, null);
            }
        }
        //////////////////////////////////////////////////////////////////////////
        
        // Einige Daten initial setzen...
        setProperty(CONTROL_KEY, Boolean.FALSE);
        setProperty(GUI_STATUS_KEY, GuiStatus.INIT);
    }
     
    /**
     * 
     * @param listener
     */
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        this.support.addPropertyChangeListener(listener);
    }

    /**
     * 
     * @param listener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        this.support.removePropertyChangeListener(listener);
    }

    /**
     * setProperty(String key, Object newValue) - Die View wird informiert...
     * 
     * @param key
     * @param newValue
     */
    public void setProperty(String key, Object newValue)
    {
        if (this.dataMap.containsKey(key))
        {
            Object oldValue = this.dataMap.get(key); 
            this.dataMap.put(key, newValue);
            
            if (Model.DESTINATION_KEY.equals(key))
            {
                if (newValue instanceof String)
                {
                    logger.debug("destination=" + newValue);
                }
            }
            
            if (Model.NUMBER_SET_POINT_KEY.equals(key))
            {
            
            }    
                
            if (Model.VALUE_MA_KEY.equals(key))
            {
                if (newValue instanceof BigDecimal)
                {
                    this.valueMA = (BigDecimal) newValue;
                    
                    logger.debug("valueMA=" + this.valueMA.toString());
                }    
            }
            
            if (Model.VALUE_MB_KEY.equals(key))
            {
                if (newValue instanceof BigDecimal)
                {
                    this.valueMB = (BigDecimal) newValue;
                    
                    logger.debug("valueMB=" + this.valueMB.toString());
                }    
            }

            if (Model.MAX_VALUE_MA_KEY.equals(key))
            {
                if (newValue instanceof BigDecimal)
                {
                    this.maxValueMA = (BigDecimal) newValue;
                    
                    logger.debug("maxValueMA=" + this.maxValueMA.toString());
                }    
            }
            
            if (Model.MAX_VALUE_MB_KEY.equals(key))
            {
                if (newValue instanceof BigDecimal)
                {
                    this.maxValueMB = (BigDecimal) newValue;
                    
                    logger.debug("maxValueMB=" + this.maxValueMB.toString());
                }    
            }
            
            if (Model.CONTROL_KEY.equals(key))
            {
                if (newValue instanceof Boolean)
                {
                    this.isControlled = Boolean.TRUE.equals(newValue);
                    
                    logger.debug("isControlled=" + this.isControlled);
                }
            }
            
            if (Model.ENHANCEMENT_KEY.equals(key))
            {
                if (newValue instanceof BigDecimal)
                {
                    // Die Verstaerkung (enhancement) findet sich nicht im Model,
                    // sondern im PositionController, daher Zugriff ueber 'Delegate'...
                    setEnhancement((BigDecimal) newValue);
                    
                    logger.debug("enhancement=" + getEnhancement().toString());
                }    
            }
            
            ////////////////////////////////////////////////////////////////////////
            // Evtl. Kein Logging an dieser Stelle...
            // if (oldValue == null || newValue == null || !oldValue.equals(newValue))
            // {
            //     logger.debug(key + ": " + oldValue + " => " + newValue);
            // }
            ////////////////////////////////////////////////////////////////////////
            
            support.firePropertyChange(key, oldValue, newValue);
        }
    }
    
    /**
     * 
     * @param speed
     * @throws IOException 
     */
    public void setPwmMA(float speed) throws IOException
    {
        if (this.motorDriverHAT != null)
        {
            this.motorDriverHAT.setPwmMA(speed);
        }
        else
        {
            logger.error("Fehler setPwmMA()!");   
        }
    }
    
    /**
     * 
     * @param speed
     * @throws IOException 
     */
    public void setPwmMB(float speed) throws IOException
    {
        if (this.motorDriverHAT != null)
        {
            this.motorDriverHAT.setPwmMB(speed);
        }
        else
        {
            logger.error("Fehler setPwmMB()!");   
        }
    }

    /**
     * 
     * @param destination
     */
    public void calculateNumberSetPoint(BigDecimal destination)
    {
        this.numberSetPoint = destination.multiply(BigDecimal.valueOf(Model.CIRCUMFERENCE)).setScale(0, BigDecimal.ROUND_UP).longValue();
        setProperty(Model.NUMBER_SET_POINT_KEY, Long.valueOf(this.numberSetPoint));
    }
    
    /**
     * 
     * @param numberSetPoint
     */
    public void setNumberSetPoint(long numberSetPoint)
    {
        this.numberSetPoint = numberSetPoint;
    }
    
    /**
     * 
     * @return
     */
    public PositionController getPositionController()
    {
        return this.positionController;
    }
    
    /**
     * setEnhancement(BigDecimal enhancement) - Delegate...
     * @param enhancement 
     */
    public void setEnhancement(BigDecimal enhancement)
    {
        this.positionController.setEnhancement(enhancement);
    }
    
    /**
     * getEnhancement() - Delegate...
     * @return enhancement (Reglerverstaerkung) des PositionController
     */
    public BigDecimal getEnhancement()
    {
        return this.positionController.getEnhancement();
    }
    
    /**
     * doStart() - Methode wird beim Start-Button beauftragt 
     * <p>
     * In der doStart()-Methode sind die Einstellungen vorzunehmen, damit
     * die Kommunikation mit dem Arduino und der eigentliche Geschäftsprozess 
     * auf dem Raspberry gestartet werden können. 
     * </p>
     */
    public void doStart()
    {
        logger.debug("doStart()...");
        
        // Kommunikations-Status setzen...
        this.i2cStatus = ArduinoI2C.Status.INITIAL;
        
        // Zustandsgroessen initial in der View setzen...
        setProperty(Model.DATA_KEY, new Data(this.counter, 
                                             this.cycleTime, 
                                             this.token,
                                             this.numberMA,
                                             this.numberMB,
                                             this.outputMA,
                                             this.outputMB));
        
        // Status der GUI setzen..
        setProperty(GUI_STATUS_KEY, GuiStatus.START);        
    }
    
    /**
     * doReset()
     */
    public void doReset()
    {
        logger.debug("doReset()...");
        
        this.token = 0L;
        
        this.numberMA = 0L;
        this.numberMB = 0L;
        
        // Zustandsgroessen zuruecksetzen...
        doClear();
        
        // isControlled: Mit Regelung... 
        this.isControlled = false;
        setProperty(Model.CONTROL_KEY, Boolean.valueOf(this.isControlled));
        
        setProperty(Model.DATA_KEY, new Data(this.counter, 
                                             this.cycleTime, 
                                             this.token,
                                             this.numberMA,
                                             this.numberMB,
                                             this.outputMA,
                                             this.outputMB));
    }
    
    /**
     * doStop() - Methode zum Unterbrechen des Geschaeftsprozess 
     * und der Kommunikation mit derm Arduino.
     */
    public void doStop()
    {
        logger.debug("doStop()...");
        
        // Kommunikations-Status setzen...
        this.i2cStatus = ArduinoI2C.Status.NOP;
        // Status der GUI setzen...
        setProperty(GUI_STATUS_KEY, GuiStatus.STOP); 
        
        // Zustandsgroessen zuruecksetzen...
        doClear();
        
        try
        {
            setPwmMA(0.0F);
            setPwmMB(0.0F);
        }
        catch(IOException exception)
        {
            logger.error(exception.toString(), exception);
            System.err.println(exception.toString());
        }
    }
    
    /**
     * shutdown()...
     * <p>
     * Der gpioController wird auf dem Raspi heruntergefahren...
     * </p>
     */
    public void shutdown()
    {
        logger.debug("shutdown()..."); 

        // Kommunikations-Status setzen...
        this.i2cStatus = ArduinoI2C.Status.NOP;

        setProperty(GUI_STATUS_KEY, GuiStatus.END);        

        try
        {
            setPwmMA(0.0F);
            setPwmMB(0.0F);
        }
        catch(IOException exception)
        {
            logger.error(exception.toString(), exception);
            System.err.println(exception.toString());
        }
        
        if (isRaspi)
        {
            this.gpioController.shutdown();  
        }
    }

    /**
     * doClear() - Zuruecksetzen der rel. Variablen...
     */
    private void doClear()
    {
        this.totalMA[0] = 0L;
        this.totalMA[1] = 0L;
        this.totalMB[0] = 0L;
        this.totalMB[1] = 0L;
        this.controlMA[0] = BigDecimal.ZERO.setScale(SCALE_MX_VALUE);
        this.controlMA[1] = BigDecimal.ZERO.setScale(SCALE_MX_VALUE);
        this.controlMB[0] = BigDecimal.ZERO.setScale(SCALE_MX_VALUE);
        this.controlMB[1] = BigDecimal.ZERO.setScale(SCALE_MX_VALUE);
        this.outputMA = BigDecimal.ZERO.setScale(SCALE_OUTPUT);
        this.outputMB = BigDecimal.ZERO.setScale(SCALE_OUTPUT);
    }
    
    @Override
    public String toString()
    {
        return "gui.Model";
    }
    
    /**
     * GuiStatus -  beschreibt den Status der 
     * Oberflaeche (GUI)
     * <ul>
     *  <li>INIT("Init")</li>
     *  <li>START("Start")</li>
     *  <li>STOP("Stop")</li>
     *  <li>ENDE("Ende")</li>
     * </ul>
     * @author Detlef Tribius
     *
     */
    public enum GuiStatus
    {
        /**
         * INIT("Init") - Initialisierung (nach Programmstart)
         */
        INIT("Init"),
        /**
         * START("Start") - Nach Betaetigung des Start-Button
         */
        START("Start"),
        /**
         * STOP("Stop") - Nach Betaetigung des Stop-Button
         */
        STOP("Stop"),
        /**
         * END("Ende") - Nach Betaetigung des Ende-Button
         */
        END("Ende");
        /**
         * GuiStatus - priv. Konstruktor
         * @param guiStatus
         */
        private GuiStatus(String guiStatus)
        {
            this.guiStatus = guiStatus;   
        }
        
        /**
         * guiStatus - textuelle Beschreibung des Gui-Status
         */
        private final String guiStatus;
        
        /**
         * getGuiStatus()
         * @return guiStatus
         */
        public String getGuiStatus()
        {
            return this.guiStatus;
        }
        
        /**
         * toString() - zu Protokollzwecken...
         */
        public String toString()
        {
            return new StringBuilder().append("[")
                                      .append(this.guiStatus)
                                      .append("]")
                                      .toString();
        }
    }
}
