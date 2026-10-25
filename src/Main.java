import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                DataStore store = new DataStore(Paths.get("data"));
                SwingApp app = new SwingApp(store);
                app.setVisible(true);
                app.showStartupWarnings();
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(
                        null,
                        "The application could not start.\n\n" + e.getMessage(),
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
