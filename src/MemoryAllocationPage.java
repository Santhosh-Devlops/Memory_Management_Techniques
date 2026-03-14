import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;

public class MemoryAllocationPage extends JFrame {
    private JTextField pidField, pnameField, memoryRequiredField;
    private JComboBox<String> allocationTypeBox;
    private JTable allocationTable;
    private DefaultTableModel tableModel;
    private Connection connection;

    public MemoryAllocationPage() {
        setTitle("Memory Allocation");
        setSize(900, 500);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 🔹 Set Background Color
        getContentPane().setBackground(new Color(25, 25, 25 )); // Midnight Blue

        // 🔹 Title Label
        JLabel titleLabel = new JLabel("Process    Allocation   For    Memory", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Times New Roman", Font.CENTER_BASELINE, 30));
        titleLabel.setForeground(Color.WHITE);
        add(titleLabel, BorderLayout.NORTH);

        // 🔹 Input Panel
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 5, 200));
        inputPanel.setBackground(new Color(25,25,25));

        JLabel pidLabel = new JLabel("Process ID");
        pidLabel.setForeground(Color.WHITE);
        pidLabel.setFont(new Font("Century Gothic",Font.BOLD,18));
        pidField = new JTextField();

        JLabel pnameLabel = new JLabel("Name");
        pnameLabel.setForeground(Color.WHITE);
        pnameLabel.setFont(new Font("Century Gothic",Font.BOLD,18));
        pnameField = new JTextField();

        JLabel memoryLabel = new JLabel("Memory Size");
        memoryLabel.setForeground(Color.WHITE);
        memoryLabel.setFont(new Font("Century Gothic",Font.BOLD,18));
        memoryRequiredField = new JTextField();

        JLabel allocationTypeLabel = new JLabel("Allocation");
        allocationTypeLabel.setForeground(Color.WHITE);
        allocationTypeLabel.setFont(new Font("Century Gothic",Font.BOLD,18));
        allocationTypeBox = new JComboBox<>(new String[]{"First Fit", "Best Fit", "Worst Fit"});

        inputPanel.add(pidLabel);
        inputPanel.add(pidField);
        inputPanel.add(pnameLabel);
        inputPanel.add(pnameField);
        inputPanel.add(memoryLabel);
        inputPanel.add(memoryRequiredField);
        inputPanel.add(allocationTypeLabel);
        inputPanel.add(allocationTypeBox);

        add(inputPanel, BorderLayout.WEST);
        tableModel = new DefaultTableModel(new String[]{"PID", "Process Name", "Memory", "Status", "Allocated At"}, 0);
        allocationTable = new JTable(tableModel);
        allocationTable.setBackground(new Color(255,255,255));
        allocationTable.setFont(new Font("Century Gothic",Font.BOLD,12));
        JScrollPane tableScrollPane = new JScrollPane(allocationTable);
        add(tableScrollPane, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 30, 5));
        buttonPanel.setBackground(new Color(255,255,255));

        JButton allocateButton =  createStyledButton("Allocate","");
        JButton deallocateButton = createStyledButton("Deallocate","");
        JButton backButton = createStyledButton("Back","");
        JButton closeButton = createStyledButton("Close","");
        allocateButton.setBackground(new Color(0,0,0));
        deallocateButton.setBackground(new Color(255,140,0));
        backButton.setBackground(new Color(0,0,0));
        closeButton.setBackground(new Color(255, 0, 0));

        allocateButton.setForeground(Color.WHITE);
        deallocateButton.setForeground(Color.WHITE);
        backButton.setForeground(Color.WHITE);
        closeButton.setForeground(Color.WHITE);
        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke('a'), "allocateAction");
        actionMap.put("allocateAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                allocateButton.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke('d'), "deallocateAction");
        actionMap.put("deallocateAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                deallocateButton.doClick();
            }
        });

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



        allocateButton.addActionListener(this::allocateMemory);
        deallocateButton.addActionListener(this::deallocateMemory);
        backButton.addActionListener(e -> {
            dispose();
            new MemoryManagementHome();
        });
        closeButton.addActionListener(e -> System.exit(0));

        buttonPanel.add(allocateButton);
        buttonPanel.add(deallocateButton);
        buttonPanel.add(backButton);
        buttonPanel.add(closeButton);

        add(buttonPanel, BorderLayout.SOUTH);
        connectToDatabase();
        loadTableData();

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
    private void connectToDatabase() {
        String url = "jdbc:mysql://localhost:3306/memory_mgmt";
        String user = "root";
        String password = "password123";

        try {
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to Database!");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database Connection Failed!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void loadTableData() {
        tableModel.setRowCount(0);
        String query = "SELECT pid, pname, memory_required, status, allocated_at FROM processes";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("pid"),
                        rs.getString("pname"),
                        rs.getInt("memory_required"),
                        rs.getString("status"),
                        rs.getTimestamp("allocated_at")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void allocateMemory(ActionEvent e) {
        String pname = pnameField.getText();
        String memoryText = memoryRequiredField.getText();
        String allocationType = (String) allocationTypeBox.getSelectedItem();

        if (pname.isEmpty() || memoryText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All fields must be filled!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int memoryRequired;
        try {
            memoryRequired = Integer.parseInt(memoryText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid numeric memory size!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String query = "INSERT INTO processes (pname, memory_required, status) VALUES (?, ?, 'allocated')";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, pname);
            pstmt.setInt(2, memoryRequired);
            pstmt.executeUpdate();

            JOptionPane.showMessageDialog(this, "Memory Allocated Successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            pnameField.setText("");
            memoryRequiredField.setText("");
            loadTableData();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Allocation Failed!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void deallocateMemory(ActionEvent e) {
        String pidText = pidField.getText();

        if (pidText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter Process ID to Deallocate!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int pid;
        try {
            pid = Integer.parseInt(pidText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid Process ID!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String query = "UPDATE processes SET status = 'terminated' WHERE pid = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, pid);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                JOptionPane.showMessageDialog(this, "Memory Deallocated Successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Process Not Found!", "Error", JOptionPane.ERROR_MESSAGE);
            }
            pidField.setText("");
            pnameField.setText("");
            memoryRequiredField.setText("");
            loadTableData();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Deallocation Failed!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MemoryAllocationPage::new);
    }
}
