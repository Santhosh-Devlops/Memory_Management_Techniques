import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;

public class HoleEntryPage extends JFrame {
    private Connection connection;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField startAddressField, sizeField;

    public HoleEntryPage() {
        setTitle("Hole Entry");
        setSize(800, 500); // Adjusted window size
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        JLabel titleLabel = new JLabel("Entering Holes To Allocate Process", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Times New Roman", Font.BOLD, 26));
        add(titleLabel, BorderLayout.NORTH);
        titleLabel.setForeground(new Color(0,0,0));
        connectToDatabase();
        JPanel inputPanel = new JPanel(new FlowLayout());

        JLabel startAddressLabel = new JLabel("Start Address:");
        startAddressLabel.setFont(new Font("Century Gothic",Font.BOLD,18));
        startAddressField = new JTextField(8);
        startAddressLabel.setForeground(new Color(255,255,255));

        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setFont(new Font("Century Gothic",Font.BOLD,18));
        sizeField = new JTextField(8);
        sizeLabel.setForeground(new Color(255,255,255));

        inputPanel.add(startAddressLabel);
        inputPanel.add(startAddressField);
        inputPanel.add(sizeLabel);
        inputPanel.add(sizeField);

        add(inputPanel, BorderLayout.WEST);
        JPanel buttonPanel = new JPanel(new FlowLayout());

        JButton allocateButton = createStyledButton("Allocate","");
        allocateButton.setBackground(new Color(0,0,0));
        allocateButton.setForeground(Color.WHITE);
        JButton deallocateButton = createStyledButton("Deallocate","");
        deallocateButton.setBackground(Color.BLACK);
        deallocateButton.setForeground(Color.WHITE);
        JButton backButton = createStyledButton("Back","");
        backButton.setBackground(new Color(0,0,0));
        backButton.setForeground(new Color(255,255,255));
        JButton closeButton = createStyledButton("Close","");
        closeButton.setBackground(new Color(255,0,0));
        closeButton.setForeground(new Color(255,255,255));
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

        closeButton.addActionListener(f->{
            dispose();
        });
        backButton.addActionListener(e -> {
            dispose();
            new MemoryManagementHome();
        });


        buttonPanel.add(allocateButton);
        buttonPanel.add(deallocateButton);
        buttonPanel.add(backButton);
        buttonPanel.add(closeButton);


        add(buttonPanel, BorderLayout.SOUTH);
        tableModel = new DefaultTableModel(new String[]{"Hole ID", "Start Address", "Size"}, 0);
        table = new JTable(tableModel);
        table.setForeground(new Color(0,0,0));
        table.setBackground(new Color(255,255,255));
        table.setFont(new Font("Century Gothic",Font.BOLD,14));
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
        getContentPane().setBackground(new Color(240, 248, 255));
        loadHoles();
        inputPanel.setBackground(new Color(0,0,0));
        inputPanel.setForeground(new Color(255,255,255));
        buttonPanel.setBackground(new Color(0,0,0));
        allocateButton.addActionListener(this::allocateHole);
        deallocateButton.addActionListener(this::deallocateHole);
        backButton.addActionListener(e -> dispose());
        closeButton.addActionListener(e -> System.exit(0));

        setVisible(true);
    }
    private void connectToDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("✅ MySQL JDBC Driver Loaded");

            String url = "jdbc:mysql://localhost:3306/memory_mgmt";
            String user = "root";
            String password = "password123";

            connection = DriverManager.getConnection(url, user, password);
            System.out.println("✅ Database Connected Successfully!");
        } catch (ClassNotFoundException e) {
            System.out.println("🚨 MySQL Driver Not Found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("🚨 Database Connection Failed!");
            e.printStackTrace();
        }
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
    private void loadHoles() {
        tableModel.setRowCount(0);

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM holes")) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("hole_id"),
                        rs.getInt("start_address"),
                        rs.getInt("size")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void allocateHole(ActionEvent e) {
        try {
            int startAddress;
            if (startAddressField.getText().isEmpty()) {
                startAddress = getNextAvailableStartAddress();
            } else {
                startAddress = Integer.parseInt(startAddressField.getText());
            }

            int size = Integer.parseInt(sizeField.getText());

            PreparedStatement pstmt = connection.prepareStatement("INSERT INTO holes (start_address, size) VALUES (?, ?)");
            pstmt.setInt(1, startAddress);
            pstmt.setInt(2, size);
            pstmt.executeUpdate();

            JOptionPane.showMessageDialog(this, "Hole allocated successfully!");
            loadHoles();

            startAddressField.setText("");
            sizeField.setText("");
        } catch (SQLException | NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid input or database error!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private int getNextAvailableStartAddress() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT MAX(start_address) FROM holes");

            if (rs.next()) {
                return rs.getInt(1) + 10;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 100;
    }
    private void deallocateHole(ActionEvent e) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a hole to deallocate!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int holeId = (int) tableModel.getValueAt(selectedRow, 0);
        try {
            PreparedStatement pstmt = connection.prepareStatement("DELETE FROM holes WHERE hole_id = ?");
            pstmt.setInt(1, holeId);
            pstmt.executeUpdate();

            JOptionPane.showMessageDialog(this, "Hole deallocated successfully!");
            loadHoles();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(HoleEntryPage::new);
    }
}
