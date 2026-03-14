import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

public class WorstFit extends JFrame {
    private Connection connection;
    private JTable table;
    private DefaultTableModel model;
    private JTextArea logArea;
    private final Semaphore semaphore = new Semaphore(1); // Binary semaphore for mutual exclusion

    public WorstFit() {
        setTitle("Worst Fit Memory Allocation with Semaphore");
        setSize(800, 500);
        setBackground(Color.BLACK);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        model = new DefaultTableModel(new String[]{"Block ID", "PID", "Start Address", "Size"}, 0);
        table = new JTable(model);
        styleTable();

        add(new JScrollPane(table), BorderLayout.CENTER);

        logArea = new JTextArea(5, 30);
        styleTextArea();
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel();
        JButton backButton = createStyledButton("Back", "");
        backButton.setBackground(Color.BLACK);
        backButton.setForeground(Color.WHITE);
        JButton closeButton = createStyledButton("Close", "");
        closeButton.setBackground(Color.RED);
        closeButton.setForeground(Color.WHITE);

        addKeyBindings(backButton, closeButton);

        backButton.addActionListener(e -> {
            dispose();
            new MemoryManagementHome(); // Replace with your home screen class
        });

        closeButton.addActionListener(e -> System.exit(0));

        buttonPanel.add(backButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.NORTH);

        connectToDatabase();

        // Run allocation in a separate thread with semaphore control
        new Thread(() -> {
            try {
                semaphore.acquire(); // Wait for permit
                performWorstFitAllocation();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                semaphore.release(); // Release permit
            }
        }).start();

        setVisible(true);
    }

    private void styleTable() {
        table.setForeground(Color.BLACK);
        table.setBackground(Color.WHITE);
        table.setFont(new Font("Century Gothic", Font.BOLD, 14));
    }

    private void styleTextArea() {
        logArea.setForeground(Color.BLACK);
        logArea.setBackground(Color.WHITE);
        logArea.setFont(new Font("Century Gothic", Font.BOLD, 14));
        logArea.setEditable(false);
    }

    private JButton createStyledButton(String text, String iconPath) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 20));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(40, 40, 40));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void addKeyBindings(JButton backButton, JButton closeButton) {
        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke('c'), "closeAction");
        actionMap.put("closeAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                closeButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke('b'), "backAction");
        actionMap.put("backAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                backButton.doClick();
            }
        });
    }

    private void connectToDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/memory_mgmt", "root", "password123");
            logArea.append("Connected to database successfully.\n");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database Connection Failed!", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void performWorstFitAllocation() {
        String holeQuery = "SELECT hole_id, start_address, size FROM holes ORDER BY size DESC";
        String processQuery = "SELECT pid, pname, memory_required FROM processes WHERE status = 'allocated'";
        String insertQuery = "INSERT INTO memory_allocation_worst_fit (block_id, pid, start_address, size) VALUES (?, ?, ?, ?)";

        try (PreparedStatement holeStmt = connection.prepareStatement(holeQuery);
             PreparedStatement processStmt = connection.prepareStatement(processQuery);
             PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {

            List<int[]> holes = new ArrayList<>();
            ResultSet holeResult = holeStmt.executeQuery();
            while (holeResult.next()) {
                int holeId = holeResult.getInt("hole_id");
                int startAddress = holeResult.getInt("start_address");
                int holeSize = holeResult.getInt("size");
                holes.add(new int[]{holeId, startAddress, holeSize});
            }

            holes.sort((h1, h2) -> Integer.compare(h2[2], h1[2]));
            Map<Integer, Integer> remainingSizes = new LinkedHashMap<>();
            for (int[] hole : holes) {
                remainingSizes.put(hole[0], hole[2]);
            }

            ResultSet processResult = processStmt.executeQuery();
            while (processResult.next()) {
                int pid = processResult.getInt("pid");
                String pname = processResult.getString("pname");
                int processSize = processResult.getInt("memory_required");
                boolean allocated = false;

                for (int[] hole : holes) {
                    int holeId = hole[0];
                    int holeSize = remainingSizes.get(holeId);

                    if (processSize <= holeSize) {
                        int startAddress = hole[1];

                        insertStmt.setInt(1, holeId);
                        insertStmt.setInt(2, pid);
                        insertStmt.setInt(3, startAddress);
                        insertStmt.setInt(4, processSize);
                        insertStmt.executeUpdate();

                        remainingSizes.put(holeId, holeSize - processSize);

                        logArea.append(String.format("Allocated Process %s (PID %d) to Block %d | Start: %d KB | Allocated: %d KB | Remaining: %d KB\n",
                                pname, pid, holeId, startAddress, processSize, holeSize - processSize));
                        allocated = true;
                        break;
                    }
                }

                if (!allocated) {
                    logArea.append(String.format("Process %s (PID %d) could not be allocated due to insufficient memory.\n", pname, pid));
                }
            }

            int totalRemainingMemory = remainingSizes.values().stream().mapToInt(Integer::intValue).sum();
            logArea.append("Total Remaining Memory after Worst Fit Allocation: " + totalRemainingMemory + " KB\n");

            loadAllocationResults();
            logArea.append("Worst Fit Memory Allocation completed successfully with Semaphore!\n");

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Memory allocation failed!", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void loadAllocationResults() {
        String query = "SELECT * FROM memory_allocation_worst_fit";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet resultSet = stmt.executeQuery()) {

            model.setRowCount(0);
            while (resultSet.next()) {
                model.addRow(new Object[]{
                        resultSet.getInt("block_id"),
                        resultSet.getInt("pid"),
                        resultSet.getInt("start_address"),
                        resultSet.getInt("size")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(WorstFit::new);
    }
}
