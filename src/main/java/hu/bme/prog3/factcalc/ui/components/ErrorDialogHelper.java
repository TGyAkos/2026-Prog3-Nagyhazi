package hu.bme.prog3.factcalc.ui.components;

import javax.swing.JOptionPane;
import java.awt.Component;

public final class ErrorDialogHelper {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ErrorDialogHelper() {
    }

    /**
     * Displays a modal error message dialog with automatic line wrapping.
     *
     * @param parent  the parent component for dialog positioning, or {@code null} for screen center
     * @param title   the dialog window title
     * @param message the error message text to display
     */
    public static void showError(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrapMessage(message), title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays a modal warning message dialog with automatic line wrapping.
     *
     * @param parent  the parent component for dialog positioning, or {@code null} for screen center
     * @param title   the dialog window title
     * @param message the warning message text to display
     */
    public static void showWarning(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrapMessage(message), title, JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Displays a modal informational message dialog with automatic line wrapping.
     *
     * @param parent  the parent component for dialog positioning, or {@code null} for screen center
     * @param title   the dialog window title
     * @param message the informational message text to display
     */
    public static void showInfo(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrapMessage(message), title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Displays a modal Yes/No confirmation dialog with automatic line wrapping.
     *
     * @param parent  the parent component for dialog positioning, or {@code null} for screen center
     * @param title   the dialog window title
     * @param message the confirmation question text to display
     * @return {@code true} if the user selects Yes, {@code false} otherwise
     */
    public static boolean confirm(Component parent, String title, String message) {
        int res = JOptionPane.showConfirmDialog(
                parent,
                wrapMessage(message),
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        return res == JOptionPane.YES_OPTION;
    }

    /**
     * Wraps long message strings at word boundaries so lines stay within 90 characters.
     *
     * @param message the input message text (may be {@code null})
     * @return the wrapped message string, or the original value if {@code null} or short enough
     */
    private static String wrapMessage(String message) {
        if (message == null || message.length() <= 90) {
            return message;
        }
        StringBuilder sb = new StringBuilder();
        int lineLen = 0;
        for (String word : message.split("\\s+")) {
            if (lineLen + word.length() + 1 > 90 && lineLen > 0) {
                sb.append('\n');
                lineLen = 0;
            }
            if (lineLen > 0) {
                sb.append(' ');
                lineLen++;
            }
            sb.append(word);
            lineLen += word.length();
        }
        return sb.toString();
    }
}
