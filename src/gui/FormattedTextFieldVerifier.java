/**
 * 
 */
package gui;

import java.text.ParseException;

import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;

/**
 * @author Detlef Tribius
 * 
 * @see https://docs.oracle.com/javase/7/docs/api/javax/swing/JFormattedTextField.html
 * 
 * auch:
 * 
 * https://docs.oracle.com/javase/tutorial/uiswing/components/formattedtextfield.html#value
 *
 */
public class FormattedTextFieldVerifier extends InputVerifier
{

    @Override
    public boolean verify(JComponent input)
    {
        if (input instanceof JFormattedTextField) 
        {
            JFormattedTextField formattedTextField = (JFormattedTextField)input;
            AbstractFormatter formatter = formattedTextField.getFormatter();
            if (formatter != null) 
            {
                String text = formattedTextField.getText();
                try 
                {
                     formatter.stringToValue(text);
                     formattedTextField.commitEdit();
                     return true;
                } 
                catch (ParseException exception) 
                {
                     return false;
                }
             }        
        }    
        return false;
    }
    
    @Override
    public boolean shouldYieldFocus(JComponent input) 
    {
        return verify(input);
    }    
}
