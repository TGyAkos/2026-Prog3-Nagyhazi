package hu.bme.prog3;

import hu.bme.prog3.factcalc.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    /**
     * Application entry point that configures the Nimbus Look and Feel and launches the main window on the Swing EDT.
     *
     * @param args command-line arguments passed to the JVM
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to default Swing Look and Feel
            }
            new MainFrame().setVisible(true);
        });
    }
}