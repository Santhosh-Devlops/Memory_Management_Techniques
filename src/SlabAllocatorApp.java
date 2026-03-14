import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;
import java.util.concurrent.Semaphore;

public class SlabAllocatorApp {
    private JFrame frame;
    private JTextArea textArea;
    private JTextArea detailsArea;
    private JTable table;
    private DefaultTableModel tableModel;
    private Connection conn;
    private Semaphore slabSemaphore = new Semaphore(1);

    public SlabAllocatorApp() {
        initialize();
        connectToDatabase();
        createSlabAllocationTable();
        new Thread(this::allocateSlabs).start();
    }

    private void initialize() {
        frame = new JFrame("Slab Allocator - Memory Management");
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        frame.getContentPane().add(panel, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel();
        JButton backButton = createStyledButton("Back");
        JButton closeButton = createStyledButton("Close");
        backButton.setBackground(Color.BLACK);
        backButton.setForeground(Color.WHITE);
        closeButton.setBackground(Color.RED);
        closeButton.setForeground(Color.WHITE);

        backButton.addActionListener(e -> {
            frame.dispose();
            new MemoryManagementHome();
        });
        JButton compactButton = createStyledButton("Compact");
        compactButton.setBackground(new Color(0, 128, 0));
        compactButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        compactButton.setForeground(Color.WHITE);
        compactButton.addActionListener(e -> compactMemory());
        buttonPanel.add(compactButton);

        closeButton.addActionListener(e -> System.exit(0));
        buttonPanel.add(backButton);
        buttonPanel.add(closeButton);
        panel.add(buttonPanel, BorderLayout.NORTH);
        addKeyBindings(backButton, closeButton);
        tableModel = new DefaultTableModel(new Object[]{"Slab ID", "Process ID", "Hole ID", "Object Allocated"}, 0);
        table = new JTable(tableModel);
        styleTable();
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        textArea = new JTextArea(10, 50);
        textArea.setEditable(false);
        panel.add(new JScrollPane(textArea), BorderLayout.SOUTH);
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        panel.add(new JScrollPane(detailsArea), BorderLayout.EAST);

        frame.setVisible(true);
    }

    private void styleTable() {
        table.setForeground(Color.BLACK);
        table.setBackground(Color.WHITE);
        table.setFont(new Font("Century Gothic", Font.BOLD, 14));
        table.setRowHeight(24);
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 20));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(40, 40, 40));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }
    private void compactMemory() {
        SwingUtilities.invokeLater(() -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM holes");
                appendToTextArea("All holes removed. Compaction started...");
                ResultSet rs = stmt.executeQuery("SELECT SUM(CASE " +
                        "WHEN status = 'allocated' THEN " +
                        "CASE " +
                        "WHEN memory_required <= 128 THEN 128 " +
                        "WHEN memory_required <= 256 THEN 256 " +
                        "ELSE 512 " +
                        "END " +
                        "ELSE 0 END) AS used FROM processes");
                int used = 0;
                if (rs.next()) used = rs.getInt("used");
                int remaining = 4096 - used;

                stmt.executeUpdate("INSERT INTO holes (start_address,size) VALUES (1001, " + remaining +")");
                appendToTextArea("Compaction complete. Free hole of " + remaining + " bytes created.");
            } catch (SQLException e) {
                appendToTextArea("Compaction Failed: " + e.getMessage());
            }
        });
    }


    private void addKeyBindings(JButton backButton, JButton closeButton) {
        JRootPane rootPane = frame.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke('b'), "backAction");
        inputMap.put(KeyStroke.getKeyStroke('c'), "closeAction");

        actionMap.put("backAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                backButton.doClick();
            }
        });

        actionMap.put("closeAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                closeButton.doClick();
            }
        });
    }

    private void connectToDatabase() {
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/memory_mgmt", "root", "password123");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(frame, "Database Connection Failed: " + e.getMessage());
        }
    }

    private void createSlabAllocationTable() {
        try (Statement stmt = conn.createStatement()) {
            String query = "CREATE TABLE IF NOT EXISTS slab_allocation (" +
                    "slab_id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "process_id INT, " +
                    "hole_id INT, " +
                    "object_allocated BOOLEAN)";
            stmt.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void allocateSlabs() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT pid, memory_required FROM processes")) {

            while (rs.next()) {
                int processId = rs.getInt("pid");
                int memoryRequired = rs.getInt("memory_required");

                new Thread(() -> {
                    try {
                        slabSemaphore.acquire();
                        int slabSize = getSlabSize(memoryRequired);
                        int holeId = findHoleForSlab(slabSize);
                        boolean allocated = holeId != -1;

                        if (allocated) {
                            markHoleAsUsed(holeId);
                            appendToTextArea("Allocated Process " + processId + " to Hole " + holeId + " using Slab " + slabSize);
                        } else {
                            appendToTextArea("No suitable hole for Process " + processId + " needing " + memoryRequired + " bytes");
                        }

                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO slab_allocation (process_id, hole_id, object_allocated) VALUES (?, ?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                            ps.setInt(1, processId);
                            if (allocated) ps.setInt(2, holeId);
                            else ps.setNull(2, Types.INTEGER);
                            ps.setBoolean(3, allocated);
                            ps.executeUpdate();

                            ResultSet keys = ps.getGeneratedKeys();
                            if (keys.next()) {
                                int slabId = keys.getInt(1);
                                SwingUtilities.invokeLater(() -> tableModel.addRow(new Object[]{
                                        slabId, processId, allocated ? holeId : "None", allocated
                                }));
                            }
                        }

                        updateDetailsArea(processId, memoryRequired, slabSize, holeId, allocated);

                    } catch (InterruptedException | SQLException e) {
                        e.printStackTrace();
                    } finally {
                        slabSemaphore.release();
                    }
                }).start();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getSlabSize(int memoryRequired) {
        if (memoryRequired <= 128) return 128;
        else if (memoryRequired <= 256) return 256;
        else return 512;
    }

    private int findHoleForSlab(int slabSize) throws SQLException {
        String query = "SELECT hole_id FROM holes WHERE size = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, slabSize);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("hole_id");
        }
        return -1;
    }

    private void markHoleAsUsed(int holeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM holes WHERE hole_id = ?")) {
            ps.setInt(1, holeId);
            ps.executeUpdate();
        }
    }

    private void appendToTextArea(String text) {
        SwingUtilities.invokeLater(() -> textArea.append(text + "\n"));
    }

    private void updateDetailsArea(int processId, int memoryRequired, int slabSize, int holeId, boolean allocated) {
        StringBuilder sb = new StringBuilder();
        sb.append("Process ID       : ").append(processId).append("\n");
        sb.append("Memory Required  : ").append(memoryRequired).append(" bytes\n");
        sb.append("Slab Size Chosen : ").append(slabSize).append(" bytes\n");
        sb.append("Hole ID          : ").append(holeId != -1 ? holeId : "None").append("\n");
        sb.append("Allocated        : ").append(allocated ? "YES" : "NO").append("\n");
        sb.append("-----------------------------------------\n");

        SwingUtilities.invokeLater(() -> detailsArea.append(sb.toString()));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SlabAllocatorApp::new);
    }
}
