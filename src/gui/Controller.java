package gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.text.DecimalFormat;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Der Controller verbindet die View mit dem Model.
 * Aktionen an der View werden ueber den Controller
 * an das Model weitergereicht.
 * <p>
 * Nur die View uebernimmt Aufgaben der Presentation und
 * der Interaktion mit dem User.
 * </p>
 * <p>
 * Nur das Model beinhaltet die Geschaeftslogik. Die View
 * muss frei von Geschaeftslogik bleiben.
 * </p>
 * 
 * @author Detlef Tribius
 *
 */
public class Controller implements ActionListener
{
    
    /**
     * logger - Logger, hier slf4j...
     */
    private final static Logger logger = LoggerFactory.getLogger(Controller.class);      
    
    /**
     * view - Referenz auf die angemeldete View...
     */
    View view;
    
    /**
     * model - Referenz auf das Model, das Model haelt alle 
     * Daten/Zustandsgroessen der Anwendung...
     */
    Model model;
    
    /**
     * <p>
     * Der Controller verbindet View und Model.
     * </p>
     * <p>
     * Die View nimmt die Darstellung vor, das Model 
     * haelt die Daten und beauftragtt die View bei Datenaenderung.
     * </p>
     * @param view die View
     * @param model das Model
     */
    public Controller(View view, Model model)
    {
        this.view = view;
        this.view.addActionListener(this);
        this.model = model;
        this.model.addPropertyChangeListener(this.view);
    }
    
    /**
     * actionPerformed(ActionEvent event) wird durch das SwingWindow
     * beauftragt und muss die Aktion an das Model weiterreichen...
     * Das Model nimmt die Datenaenderung auf und reagiert entsprechend.
     * Dann erfolgt das Nachziehen der View durch das Model...
     */
    @Override
    public void actionPerformed(ActionEvent event)
    {
        final JComponent source = (JComponent)event.getSource();
        final String name = source.getName();
        if (source instanceof JButton)
        {
            logger.debug("actionPerformed(): " + event.getActionCommand() + " " + name);
            if (Model.NAME_START_BUTTON.equals(name))
            {
                // Start-Button...
                this.model.doStart();
                return;
            }
            if (Model.NAME_STOP_BUTTON.equals(name))
            {
                // Stop-Button...
                this.model.doStop();
                return;
            }
            if (Model.NAME_RESET_BUTTON.equals(name))
            {
                // Reset-Button...
                this.model.doReset();
                return;
            }
            if (Model.NAME_END_BUTTON.equals(name))
            {
                // Ende-Button...
                this.model.shutdown();
                System.exit(0);
            }
        }
        if (source instanceof JComboBox<?>)
        {
            final BigDecimal value = (BigDecimal)((JComboBox<?>)source).getSelectedItem();
            logger.debug("actionPerformed(): " + event.getActionCommand() + " " + name + " " + ((value != null)? value : ""));
            this.model.setProperty(name, value);
            return;
        }
        if (source instanceof JFormattedTextField)
        {
            JFormattedTextField formattedTextField = (JFormattedTextField)source;
            final Object value = formattedTextField.getValue();
            AbstractFormatter formatter = formattedTextField.getFormatter();
            
            try
            {
                // => Eingabe ist vom Typ String, wird als solcher auch ins Model gesetzt.
                final String output = formatter.valueToString(value);
                // getActionCommand() liefert command aus dem Konstruktor des ActionEvent... 
                this.model.setProperty(name, output);
                
                // Jetzt muss die Umrechnung Umdrehungsanzahl in Impulsanzahl vorgenommen werden,
                // d.h. output (Umdrehungsanzahl) * Model.CIRCUMFERENCE (= Anzahl Impulse pro Umdrehung)
                // ergibt. phiSetPoint (Sollwert in Impulsanzahl).
                final DecimalFormat decimalFormat = new DecimalFormat(Model.FORMATTED_TEXT_FIELD_PATTERN);
                decimalFormat.setParseBigDecimal(true);
                
                final BigDecimal destination = (BigDecimal) decimalFormat.parse(output);

                this.model.calculateNumberSetPoint(destination);
                
                logger.debug("actionPerformed(): " + event.getActionCommand() + " " + name + ": "  + output);
                
            }
            catch (Exception exception)
            {
                logger.error("actionPerformed()", exception);
            }
            return;
        }
        if (source instanceof JCheckBox)
        {
            JCheckBox checkBox = (JCheckBox)source; 
            final boolean isSelected = checkBox.isSelected();
            
            logger.debug("actionPerformed(): " + event.getActionCommand() + " " + (isSelected? "selected" : "deselected") );
            
            this.model.setProperty(name, Boolean.valueOf(isSelected));
            return;
        }
    }
}
