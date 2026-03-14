import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MemoryManagementHome extends JFrame {
    private Connection connection;

    public MemoryManagementHome() {
        setTitle("Memory Management");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(0, 0, 0));

        JLabel titleLabel = new JLabel("Memory Management", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Times New Roman", Font.BOLD, 42));
        titleLabel.setForeground(Color.white);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        add(titleLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(4, 3, 50, 100));
        buttonPanel.setBackground(new Color(0, 0, 0));

        JButton firstFitButton = createStyledButton("First Fit", "best_fit.png");
        JButton bestFitButton = createStyledButton("Best Fit", "best_fit.png");
        JButton worstFitButton = createStyledButton("Worst Fit", "worst_fit.png");
        JButton processAllocationButton = createStyledButton("Process Allocation", "process_allocation.png");
        JButton holeEntryButton = createStyledButton("Hole Entry", "hole_entry.png");
        JButton slabButton = createStyledButton("Slab Allocation", "best_fit.png");
        JButton buddyButton = createStyledButton("Buddy Allocation", "best_fit.png");

        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke('f'), "firstFitAction");
        actionMap.put("firstFitAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                firstFitButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke('b'), "bestFitAction");
        actionMap.put("bestFitAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                bestFitButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke('w'), "worstFitAction");
        actionMap.put("worstFitAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                worstFitButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke('p'), "processAllocationAction");
        actionMap.put("processAllocationAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                processAllocationButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke('h'), "holeEntryAction");
        actionMap.put("holeEntryAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                holeEntryButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke('s'), "slabAllocationAction");
        actionMap.put("slabAllocationAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                slabButton.doClick();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke('u'), "buddyAllocationAction");
        actionMap.put("buddyAllocationAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                buddyButton.doClick();
            }
        });


        firstFitButton.addActionListener(e -> switchTo(FirstFit.class));
        bestFitButton.addActionListener(e -> switchTo(BestFit.class));
        worstFitButton.addActionListener(e -> switchTo(WorstFit.class));
        processAllocationButton.addActionListener(e -> switchTo(MemoryAllocationPage.class));
        holeEntryButton.addActionListener(e -> switchTo(HoleEntryPage.class));
        slabButton.addActionListener(e -> switchTo(SlabAllocatorApp.class));
        buddyButton.addActionListener(e -> switchTo(BuddyMemoryAllocator.class));

        buttonPanel.add(firstFitButton);
        buttonPanel.add(bestFitButton);
        buttonPanel.add(worstFitButton);
        buttonPanel.add(processAllocationButton);
        buttonPanel.add(holeEntryButton);
        buttonPanel.add(slabButton);
        buttonPanel.add(buddyButton);


        add(buttonPanel, BorderLayout.CENTER);

        JButton closeButton = createStyledButton("CLOSE", "");
        closeButton.setBackground(Color.RED);
        closeButton.addActionListener(e ->dispose());

                inputMap.put(KeyStroke.getKeyStroke('c'), "closeAction");
        actionMap.put("closeAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                closeButton.doClick();
            }
        });

        JPanel closePanel = new JPanel();
        closePanel.setBackground(new Color(20, 20, 20));
        closePanel.add(closeButton);
        add(closePanel, BorderLayout.SOUTH);

        connectToDatabase();
        setVisible(true);
    }

    private JButton createStyledButton(String text, String iconPath) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 20));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(40, 40, 40));
        button.setFocusPainted(false);
        if (!iconPath.isEmpty()) {
            try {
                ImageIcon icon = new ImageIcon(getClass().getResource(iconPath));
                button.setIcon(icon);
                button.setHorizontalTextPosition(SwingConstants.CENTER);
                button.setVerticalTextPosition(SwingConstants.BOTTOM);
            } catch (Exception e) {
                System.out.println("Icon not found: " + iconPath);
            }
        }
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return button;
    }

    private void switchTo(Class<?> cls) {
        try {
            JFrame nextPage = (JFrame) cls.getDeclaredConstructor().newInstance();
            nextPage.setVisible(true);
            dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void connectToDatabase() {
        try {

            String url = "jdbc:mysql://localhost:3306/memory_mgmt";
            String user = "root";
            String password = "password123";

            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to DB successfully.");
        } catch (SQLException e) {
            System.out.println("DB connection failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MemoryManagementHome::new);
    }
}
