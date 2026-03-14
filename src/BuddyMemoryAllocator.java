import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import javax.swing.table.DefaultTableModel;
import java.util.concurrent.Semaphore;

public class BuddyMemoryAllocator extends JFrame {
    private Connection connection;
    private Semaphore memorySemaphore;
    private JTable memoryTable;
    private DefaultTableModel tableModel;
    private JTextArea allocationDetailsArea;
    private static final int MAX_MEMORY_BLOCKS = 100;
    public BuddyMemoryAllocator() {

        memorySemaphore = new Semaphore(MAX_MEMORY_BLOCKS, true);
        setTitle("Buddy Memory Allocation");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        JButton allocateButton = new JButton("Allocate Memory");
        JButton freeButton = new JButton("Free Memory");
        JButton showButton = new JButton("Show Memory Blocks");
        allocateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        freeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        showButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JPanel panel = new JPanel();
        panel.add(allocateButton);
        panel.add(freeButton);
        panel.add(showButton);
        add(panel, BorderLayout.NORTH);
        JRootPane rootPane = this.getRootPane();
        String[] columnNames = {"Address", "Size", "Allocated", "Buddy Address", "Parent Address"};
        tableModel = new DefaultTableModel(columnNames, 0);
        memoryTable = new JTable(tableModel);
        styleTable();
        memoryTable.setPreferredScrollableViewportSize(new Dimension(800, 400));
        memoryTable.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(memoryTable);
        add(scrollPane, BorderLayout.CENTER);
        allocationDetailsArea = new JTextArea(10, 50);
        allocationDetailsArea.setEditable(false);
        JScrollPane textAreaScrollPane = new JScrollPane(allocationDetailsArea);
        add(textAreaScrollPane, BorderLayout.SOUTH);
        allocateButton.addActionListener(e -> allocateMemory());
        freeButton.addActionListener(e -> freeMemory(2200));
        showButton.addActionListener(e -> showMemoryBlocks());
        JPanel buttonPanel = new JPanel();
        JButton backButton = createStyledButton("Back");
        JButton closeButton = createStyledButton("Close");
        backButton.setBackground(Color.BLACK);
        backButton.setForeground(Color.WHITE);
        closeButton.setBackground(Color.RED);
        closeButton.setForeground(Color.WHITE);
        backButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backButton.addActionListener(e -> {
            this.dispose();
            new MemoryManagementHome();
        });
        JButton compactButton = new JButton("Compact Memory");
        compactButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        compactButton.setBackground(new Color(0, 128, 0));
        compactButton.setForeground(Color.WHITE);
        compactButton.addActionListener(e -> compactBuddyMemory());
        buttonPanel.add(compactButton);

        closeButton.addActionListener(e -> System.exit(0));
        buttonPanel.add(backButton);
        buttonPanel.add(closeButton);
        panel.add(buttonPanel, BorderLayout.NORTH);
        addKeyBindings(backButton, closeButton);


        connectToDatabase();
        setFocusable(true);
        setVisible(true);
    }
    private void styleTable() {
        memoryTable.setForeground(Color.BLACK);
        memoryTable.setBackground(Color.WHITE);
        memoryTable.setFont(new Font("Century Gothic", Font.BOLD, 14));
        memoryTable.setRowHeight(24);
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
    private void compactBuddyMemory() {
        SwingUtilities.invokeLater(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM memory_blocks");
                stmt.executeUpdate("INSERT INTO memory_blocks (address, size, is_allocated, buddy_address, parent_address) " +
                        "VALUES (0, 4096, FALSE, NULL, NULL)");


                allocationDetailsArea.append("Memory compacted: All free space merged into one block of size 4096.\n");
                showMemoryBlocks();
            } catch (SQLException e) {
                allocationDetailsArea.append("Compaction Failed: " + e.getMessage() + "\n");
            }
        });
    }

    private void addKeyBindings(JButton backButton, JButton closeButton) {

        JRootPane rootPane = this.getRootPane();
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
            String url = "jdbc:mysql://localhost:3306/memory_mgmt";
            String user = "root";
            String password = "password123";
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to DB successfully.");
        } catch (SQLException e) {
            System.out.println("DB connection failed: " + e.getMessage());
        }
    }
    private void allocateMemory() {
        try {

            String processSql = "SELECT * FROM processes WHERE status = 'waiting' ORDER BY memory_required ASC";
            PreparedStatement processStmt = connection.prepareStatement(processSql);
            ResultSet processRs = processStmt.executeQuery();

            boolean allocationDone = false;
            while (processRs.next() && memorySemaphore.tryAcquire()) {
                String processName = processRs.getString("pname");
                int processMemoryRequired = processRs.getInt("memory_required");
                String sql = "SELECT * FROM memory_blocks WHERE size >= ? AND is_allocated = FALSE ORDER BY size ASC LIMIT 1";
                PreparedStatement stmt = connection.prepareStatement(sql);
                stmt.setInt(1, processMemoryRequired);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int address = rs.getInt("address");
                    int size = rs.getInt("size");

                    if (size > processMemoryRequired) {
                        splitBlock(address, size);
                    }

                    markAllocated(address);

                    allocationDetailsArea.append("Memory allocated for process '" + processName + "' at address " + address + "\n");
                    String updateStatusSql = "UPDATE process SET status = 'running' WHERE pname = ?";
                    PreparedStatement updateStmt = connection.prepareStatement(updateStatusSql);
                    updateStmt.setString(1, processName);
                    updateStmt.executeUpdate();

                    allocationDone = true;
                } else {
                    allocationDetailsArea.append("No suitable block found for process '" + processName + "'\n");
                }
            }

            if (!allocationDone) {
                allocationDetailsArea.append("No processes allocated due to lack of memory.\n");
            }

            showMemoryBlocks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    private void splitBlock(int address, int size) {
        try {
            int newSize = size / 2;
            int buddyAddress = address + newSize;
            String updateOriginalSql = "UPDATE memory_blocks SET size = ? WHERE address = ?";
            PreparedStatement updateOriginalStmt = connection.prepareStatement(updateOriginalSql);
            updateOriginalStmt.setInt(1, newSize);
            updateOriginalStmt.setInt(2, address);
            updateOriginalStmt.executeUpdate();
            String insertBuddySql = "INSERT INTO memory_blocks (address, size, is_allocated, buddy_address, parent_address) VALUES (?, ?, FALSE, ?, ?)";
            PreparedStatement insertBuddyStmt = connection.prepareStatement(insertBuddySql);
            insertBuddyStmt.setInt(1, buddyAddress);
            insertBuddyStmt.setInt(2, newSize);
            insertBuddyStmt.setInt(3, address);
            insertBuddyStmt.setInt(4, address);

            insertBuddyStmt.executeUpdate();
            showMemoryBlocks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void markAllocated(int address) {
        try {
            String sql = "UPDATE memory_blocks SET is_allocated = TRUE WHERE address = ?";
            PreparedStatement stmt = connection.prepareStatement(sql);
            stmt.setInt(1, address);
            stmt.executeUpdate();
            showMemoryBlocks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void freeMemory(int address) {
        try {
            memorySemaphore.release();
            System.out.println("Semaphore released, memory is freed.");

            String sql = "UPDATE memory_blocks SET is_allocated = FALSE WHERE address = ?";
            PreparedStatement stmt = connection.prepareStatement(sql);
            stmt.setInt(1, address);
            stmt.executeUpdate();

            String buddySql = "SELECT * FROM memory_blocks WHERE buddy_address = ? AND is_allocated = FALSE";
            PreparedStatement buddyStmt = connection.prepareStatement(buddySql);
            buddyStmt.setInt(1, address);
            ResultSet rs = buddyStmt.executeQuery();

            if (rs.next()) {
                int buddyAddress = rs.getInt("address");
                mergeBlocks(address, buddyAddress);
            }
            showMemoryBlocks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void mergeBlocks(int address, int buddyAddress) {
        try {
            String sql = "DELETE FROM memory_blocks WHERE address = ?";
            PreparedStatement stmt = connection.prepareStatement(sql);
            stmt.setInt(1, buddyAddress);
            stmt.executeUpdate();
            String parentSql = "SELECT * FROM memory_blocks WHERE address = ?";
            PreparedStatement parentStmt = connection.prepareStatement(parentSql);
            parentStmt.setInt(1, address);
            ResultSet rs = parentStmt.executeQuery();
            if (rs.next()) {
                int parentSize = rs.getInt("size") * 2;
                sql = "UPDATE memory_blocks SET size = ? WHERE address = ?";
                stmt = connection.prepareStatement(sql);
                stmt.setInt(1, parentSize);
                stmt.setInt(2, address);
                stmt.executeUpdate();
            }
            showMemoryBlocks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showMemoryBlocks() {
        try {
            tableModel.setRowCount(0);
            String sql = "SELECT * FROM memory_blocks ORDER BY address ASC";
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                int address = rs.getInt("address");
                int size = rs.getInt("size");
                boolean isAllocated = rs.getBoolean("is_allocated");
                int buddyAddress = rs.getInt("buddy_address");
                int parentAddress = rs.getInt("parent_address");

                Object[] row = {
                        address,
                        size,
                        isAllocated ? "Yes" : "No",
                        buddyAddress != 0 ? buddyAddress : "N/A",
                        parentAddress != 0 ? parentAddress : "N/A"
                };

                tableModel.addRow(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    private void navigateToBuddyMemoryAllocatorPage() {
        JOptionPane.showMessageDialog(this, "Navigating to Buddy Memory Allocator Page!");
    }

    private void navigateToPreviousPage() {
        System.out.println("Navigating to previous page...");
        dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BuddyMemoryAllocator());
    }
}
