import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class SwingApp extends JFrame {
    private static final Color PAGE_BACKGROUND = new Color(244, 247, 251);
    private static final Color CARD_BACKGROUND = Color.WHITE;
    private static final Color TEXT_MUTED = new Color(89, 99, 110);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Dimension AUTH_FIELD_SIZE = new Dimension(320, 34);
    private static final DateTimeFormatter ISSUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final DataStore store;
    private final IssueManager issueManager;
    private final UserManager userManager;
    private final IssueFilter issueFilter = new IssueFilter();

    private User currentUser;
    private JTable issueTable;
    private DefaultTableModel issueTableModel;
    private JLabel titleLabel;
    private JLabel statsLabel;
    private JTextField searchField;
    private JComboBox<String> statusFilterBox;
    private JComboBox<String> categoryFilterBox;
    private JComboBox<String> priorityFilterBox;
    private JComboBox<String> departmentFilterBox;
    private JComboBox<String> timeFilterBox;
    private boolean updatingFilterControls;

    public SwingApp(DataStore store) {
        if (store == null) {
            throw new IllegalArgumentException("DataStore is required.");
        }
        this.store = store;
        this.userManager = new UserManager(store);
        this.issueManager = new IssueManager(store);

        setTitle("Neighbourhood Issue Reporter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 620);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(820, 520));
        showAuthPanel();
    }

    /**
     * Shows any problems recorded while loading data (for example, a corrupt
     * file that was backed up). Called once after the window becomes visible.
     */
    public void showStartupWarnings() {
        List<String> warnings = store.getWarnings();
        if (warnings.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("Some saved data could not be loaded:\n\n");
        for (String warning : warnings) {
            message.append("- ").append(warning).append("\n\n");
        }
        JOptionPane.showMessageDialog(
                this,
                message.toString().trim(),
                "Data Warning",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showAuthPanel() {
        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBorder(new EmptyBorder(32, 36, 32, 36));
        root.setBackground(PAGE_BACKGROUND);

        JPanel header = new JPanel(new GridLayout(0, 1, 4, 4));
        header.setOpaque(false);
        JLabel appName = new JLabel("Neighbourhood Issue Reporter");
        appName.setHorizontalAlignment(SwingConstants.CENTER);
        appName.setFont(new Font("Segoe UI", Font.BOLD, 28));
        JLabel tagline = new JLabel("Report, track, and resolve community issues.");
        tagline.setHorizontalAlignment(SwingConstants.CENTER);
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        tagline.setForeground(TEXT_MUTED);
        header.add(appName);
        header.add(tagline);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabs.addTab("Login", createLoginPanel());
        tabs.addTab("Register", createRegisterPanel());

        root.add(header, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
        revalidate();
        repaint();
    }

    private JPanel createLoginPanel() {
        JPanel panel = createFormPanel("Welcome back", "Sign in to manage neighbourhood reports.");
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = new JButton("Login");
        stylePrimaryButton(loginButton);

        addAuthField(panel, "Username", usernameField, 2);
        addAuthField(panel, "Password", passwordField, 3);
        addAuthButton(panel, loginButton, 4);

        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            currentUser = userManager.login(username, password);

            if (currentUser == null) {
                showError("Invalid username or password.");
                return;
            }
            showDashboard();
            promptForDefaultAdminPassword();
        });
        return panel;
    }

    private JPanel createRegisterPanel() {
        JPanel panel = createFormPanel("Create an account", "Register as a citizen to report local issues.");
        JTextField nameField = new JTextField();
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton registerButton = new JButton("Register");
        stylePrimaryButton(registerButton);

        addAuthField(panel, "Full name", nameField, 2);
        addAuthField(panel, "Username", usernameField, 3);
        addAuthField(panel, "Password", passwordField, 4);
        addAuthButton(panel, registerButton, 5);

        registerButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            String error = userManager.validateRegistration(username, password, name);
            if (error != null) {
                showError(error);
                return;
            }
            try {
                if (!userManager.register(username, password, name)) {
                    showError("Registration failed. Please review your details.");
                    return;
                }
            } catch (DataStoreException ex) {
                showSaveError(ex);
                return;
            }

            nameField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            JOptionPane.showMessageDialog(this, "Registered successfully. You can now log in.");
        });
        return panel;
    }

    private JPanel createFormPanel(String heading, String subheading) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(PAGE_BACKGROUND);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CARD_BACKGROUND);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 228, 238)),
                new EmptyBorder(28, 34, 30, 34)
        ));

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        headingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel subheadingLabel = new JLabel(subheading);
        subheadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subheadingLabel.setForeground(TEXT_MUTED);
        subheadingLabel.setHorizontalAlignment(SwingConstants.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 4, 0);
        form.add(headingLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 18, 0);
        form.add(subheadingLabel, gbc);

        wrapper.putClientProperty("formPanel", form);
        wrapper.add(form);
        return wrapper;
    }

    private void addAuthField(JPanel panel, String label, JComponent field, int row) {
        JPanel form = getAuthForm(panel);
        JLabel jLabel = new JLabel(label);
        jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        jLabel.setForeground(new Color(42, 51, 65));

        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setPreferredSize(AUTH_FIELD_SIZE);
        field.setMinimumSize(AUTH_FIELD_SIZE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 6, 16);
        form.add(jLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(field, gbc);
    }

    private void addAuthButton(JPanel panel, JButton button, int row) {
        JPanel form = getAuthForm(panel);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(12, 0, 0, 0);
        form.add(button, gbc);
    }

    private JPanel getAuthForm(JPanel panel) {
        Object form = panel.getClientProperty("formPanel");
        return form instanceof JPanel ? (JPanel) form : panel;
    }

    private void stylePrimaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 22, 8, 22));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void addField(JPanel panel, String label, JComponent field) {
        JLabel jLabel = new JLabel(label);
        jLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(jLabel);
        panel.add(field);
    }

    private void showDashboard() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(244, 247, 251));

        titleLabel = new JLabel();
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statsLabel.setForeground(new Color(89, 99, 110));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titleBox = new JPanel(new GridLayout(0, 1, 2, 2));
        titleBox.setOpaque(false);
        titleBox.add(titleLabel);
        titleBox.add(statsLabel);

        JButton changePasswordButton = new JButton("Change Password");
        changePasswordButton.addActionListener(e -> showChangePasswordDialog());
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> {
            resetFilterModel();
            currentUser = null;
            showAuthPanel();
        });
        JPanel accountActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountActions.setOpaque(false);
        accountActions.add(changePasswordButton);
        accountActions.add(logoutButton);

        header.add(titleBox, BorderLayout.WEST);
        header.add(accountActions, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(createFilterBar(), BorderLayout.CENTER);

        issueTableModel = new DefaultTableModel(
                new String[]{"ID", "Title", "Category", "Priority", "Status", "Department", "Location", "Reported By", "Date"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        issueTable = new JTable(issueTableModel);
        issueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(issueTableModel);
        sorter.setComparator(8, (left, right) -> {
            LocalDateTime leftDate = parseIssueDate(left);
            LocalDateTime rightDate = parseIssueDate(right);
            if (leftDate != null && rightDate != null) {
                return leftDate.compareTo(rightDate);
            }
            return String.valueOf(left).compareTo(String.valueOf(right));
        });
        issueTable.setRowSorter(sorter);
        issueTable.setRowHeight(24);
        issueTable.getTableHeader().setReorderingAllowed(false);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(issueTable), BorderLayout.CENTER);
        root.add(createActionPanel(), BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(null);
        installDashboardShortcuts(root);
        refreshDashboard();
        revalidate();
        repaint();
    }

    private JPanel createFilterBar() {
        JPanel bar = new JPanel(new GridLayout(0, 1, 0, 6));
        bar.setBackground(CARD_BACKGROUND);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 228, 238)),
                new EmptyBorder(10, 12, 10, 12)
        ));

        searchField = new JTextField();
        styleFilterField(searchField, 230);
        searchField.setToolTipText("Search title, description, location, or reporter (Ctrl+F)");

        statusFilterBox = new JComboBox<>(withAll(Issue.STATUSES));
        categoryFilterBox = new JComboBox<>(withAll(Issue.CATEGORIES));
        priorityFilterBox = new JComboBox<>(withAll(Issue.PRIORITIES));
        departmentFilterBox = new JComboBox<>(departmentFilterOptions());
        timeFilterBox = new JComboBox<>(IssueFilter.TIME_RANGES);
        styleFilterField(statusFilterBox, 110);
        styleFilterField(categoryFilterBox, 120);
        styleFilterField(priorityFilterBox, 110);
        styleFilterField(departmentFilterBox, 145);
        styleFilterField(timeFilterBox, 115);

        searchField.setText(issueFilter.getSearchText());
        statusFilterBox.setSelectedItem(issueFilter.getStatus());
        categoryFilterBox.setSelectedItem(issueFilter.getCategory());
        priorityFilterBox.setSelectedItem(issueFilter.getPriority());
        departmentFilterBox.setSelectedItem(issueFilter.getDepartment());
        timeFilterBox.setSelectedItem(issueFilter.getTimeRange());

        JPanel firstRow = createFilterRow();
        addFilterControl(firstRow, "Search", searchField);
        addFilterControl(firstRow, "Status", statusFilterBox);
        addFilterControl(firstRow, "Category", categoryFilterBox);

        JButton clearButton = new JButton("Clear Filters");
        clearButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        clearButton.addActionListener(e -> clearFilters());

        JPanel secondRow = createFilterRow();
        addFilterControl(secondRow, "Priority", priorityFilterBox);
        addFilterControl(secondRow, "Department", departmentFilterBox);
        addFilterControl(secondRow, "Reported", timeFilterBox);
        secondRow.add(clearButton);

        bar.add(firstRow);
        bar.add(secondRow);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterChanged(); }

            @Override
            public void removeUpdate(DocumentEvent e) { filterChanged(); }

            @Override
            public void changedUpdate(DocumentEvent e) { filterChanged(); }
        });
        statusFilterBox.addActionListener(e -> filterChanged());
        categoryFilterBox.addActionListener(e -> filterChanged());
        priorityFilterBox.addActionListener(e -> filterChanged());
        departmentFilterBox.addActionListener(e -> filterChanged());
        timeFilterBox.addActionListener(e -> filterChanged());

        return bar;
    }

    private JPanel createFilterRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        return row;
    }

    private void addFilterControl(JPanel row, String label, JComponent component) {
        JLabel filterLabel = new JLabel(label);
        filterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        filterLabel.setForeground(TEXT_MUTED);
        row.add(filterLabel);
        row.add(component);
    }

    private void styleFilterField(JComponent component, int width) {
        component.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        component.setPreferredSize(new Dimension(width, 28));
    }

    private String[] withAll(String[] options) {
        String[] result = new String[options.length + 1];
        result[0] = IssueFilter.ALL;
        System.arraycopy(options, 0, result, 1, options.length);
        return result;
    }

    private String[] departmentFilterOptions() {
        String[] result = new String[Issue.DEPARTMENTS.length + 2];
        result[0] = IssueFilter.ALL;
        result[1] = IssueFilter.UNASSIGNED;
        System.arraycopy(Issue.DEPARTMENTS, 0, result, 2, Issue.DEPARTMENTS.length);
        return result;
    }

    private void filterChanged() {
        if (updatingFilterControls) {
            return;
        }
        issueFilter.setSearchText(searchField.getText());
        issueFilter.setStatus((String) statusFilterBox.getSelectedItem());
        issueFilter.setCategory((String) categoryFilterBox.getSelectedItem());
        issueFilter.setPriority((String) priorityFilterBox.getSelectedItem());
        issueFilter.setDepartment((String) departmentFilterBox.getSelectedItem());
        issueFilter.setTimeRange((String) timeFilterBox.getSelectedItem());
        refreshDashboard();
    }

    private void clearFilters() {
        updatingFilterControls = true;
        resetFilterModel();
        searchField.setText("");
        statusFilterBox.setSelectedItem(IssueFilter.ALL);
        categoryFilterBox.setSelectedItem(IssueFilter.ALL);
        priorityFilterBox.setSelectedItem(IssueFilter.ALL);
        departmentFilterBox.setSelectedItem(IssueFilter.ALL);
        timeFilterBox.setSelectedItem(IssueFilter.ANY_TIME);
        updatingFilterControls = false;
        refreshDashboard();
        searchField.requestFocusInWindow();
    }

    private void resetFilterModel() {
        issueFilter.setSearchText("");
        issueFilter.setStatus(IssueFilter.ALL);
        issueFilter.setCategory(IssueFilter.ALL);
        issueFilter.setPriority(IssueFilter.ALL);
        issueFilter.setDepartment(IssueFilter.ALL);
        issueFilter.setTimeRange(IssueFilter.ANY_TIME);
    }

    private void installDashboardShortcuts(JPanel root) {
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = root.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                "focusIssueSearch");
        actionMap.put("focusIssueSearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearIssueFilters");
        actionMap.put("clearIssueFilters", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearFilters();
            }
        });
    }

    private JPanel createActionPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        actions.setOpaque(false);

        if (!isAdmin()) {
            JButton reportButton = new JButton("Report Issue");
            reportButton.addActionListener(e -> showIssueForm());
            actions.add(reportButton);
        }

        JButton detailsButton = new JButton("View Details");
        JButton refreshButton = new JButton("Refresh");
        detailsButton.addActionListener(e -> showSelectedIssueDetails());
        refreshButton.addActionListener(e -> refreshDashboard());

        actions.add(detailsButton);
        actions.add(refreshButton);

        if (isAdmin()) {
            JButton statusButton = new JButton("Update Status");
            JButton deptButton = new JButton("Assign Department");
            JButton priorityButton = new JButton("Update Priority");
            JButton deleteButton = new JButton("Delete");
            JButton usersButton = new JButton("Users");

            statusButton.addActionListener(e -> updateSelectedIssue("Status", Issue.STATUSES));
            deptButton.addActionListener(e -> updateSelectedIssue("Department", Issue.DEPARTMENTS));
            priorityButton.addActionListener(e -> updateSelectedIssue("Priority", Issue.PRIORITIES));
            deleteButton.addActionListener(e -> deleteSelectedIssue());
            usersButton.addActionListener(e -> showUsers());

            actions.add(statusButton);
            actions.add(deptButton);
            actions.add(priorityButton);
            actions.add(deleteButton);
            actions.add(usersButton);
        }

        return actions;
    }

    private void showIssueForm() {
        if (isAdmin()) {
            showError("Only citizens can report issues.");
            return;
        }

        JTextField titleField = new JTextField();
        JTextArea descriptionArea = new JTextArea(4, 24);
        JTextField locationField = new JTextField();
        JComboBox<String> categoryBox = new JComboBox<>(Issue.CATEGORIES);
        JComboBox<String> priorityBox = new JComboBox<>(Issue.PRIORITIES);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        addField(form, "Title", titleField);
        addField(form, "Description", new JScrollPane(descriptionArea));
        addField(form, "Location", locationField);
        addField(form, "Category", categoryBox);
        addField(form, "Priority", priorityBox);

        String title = "";
        String description = "";
        String location = "";
        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    this, form, "Report New Issue", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) return;

            title = titleField.getText().trim();
            description = descriptionArea.getText().trim();
            location = locationField.getText().trim();

            String error = issueManager.validateIssue(
                    title, description, location, currentUser.getUsername());
            if (error == null) break;
            showError(error);
        }

        Issue issue = new Issue(
                title,
                description,
                location,
                (String) categoryBox.getSelectedItem(),
                (String) priorityBox.getSelectedItem(),
                currentUser.getUsername()
        );
        try {
            if (!issueManager.addIssue(issue)) {
                showError("Unable to report this issue.");
                return;
            }
        } catch (DataStoreException ex) {
            showSaveError(ex);
            return;
        }
        refreshDashboard();
        JOptionPane.showMessageDialog(this, "Issue #" + issue.getId() + " reported.");
    }

    private void refreshDashboard() {
        if (currentUser == null) {
            return;
        }
        titleLabel.setText("Welcome, " + currentUser.getName() + " (" + currentUser.getRole() + ")");
        List<Issue> issues = isAdmin()
                ? issueManager.getAllIssues()
                : issueManager.getByUser(currentUser.getUsername());
        List<Issue> filtered = issueFilter.apply(issues);

        issueTableModel.setRowCount(0);
        for (Issue issue : filtered) {
            issueTableModel.addRow(new Object[]{
                    issue.getId(),
                    issue.getTitle(),
                    issue.getCategory(),
                    issue.getPriority(),
                    issue.getStatus(),
                    issue.getDepartment(),
                    issue.getLocation(),
                    issue.getReportedBy(),
                    issue.getDateReported()
            });
        }

        statsLabel.setText("Showing " + filtered.size() + " of " + issues.size()
                + "    Pending: " + countStatus(filtered, "Pending")
                + "    In Progress: " + countStatus(filtered, "In Progress")
                + "    Resolved: " + countStatus(filtered, "Resolved"));
    }

    private int countStatus(List<Issue> issues, String status) {
        int count = 0;
        for (Issue issue : issues) {
            if (issue.getStatus().equalsIgnoreCase(status)) {
                count++;
            }
        }
        return count;
    }

    private void showSelectedIssueDetails() {
        Issue issue = getSelectedIssue();
        if (issue == null) return;

        JTextArea details = new JTextArea(formatIssue(issue), 12, 42);
        details.setEditable(false);
        details.setFont(new Font("Consolas", Font.PLAIN, 13));
        JOptionPane.showMessageDialog(this, new JScrollPane(details), "Issue #" + issue.getId(), JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatIssue(Issue issue) {
        return "Title       : " + issue.getTitle() + "\n"
                + "Description : " + issue.getDescription() + "\n"
                + "Location    : " + issue.getLocation() + "\n"
                + "Category    : " + issue.getCategory() + "\n"
                + "Priority    : " + issue.getPriority() + "\n"
                + "Status      : " + issue.getStatus() + "\n"
                + "Department  : " + issue.getDepartment() + "\n"
                + "Reported By : " + issue.getReportedBy() + "\n"
                + "Date        : " + issue.getDateReported();
    }

    private static LocalDateTime parseIssueDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, ISSUE_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void updateSelectedIssue(String label, String[] options) {
        Issue issue = getSelectedIssue();
        if (issue == null) return;

        String selected = (String) JOptionPane.showInputDialog(
                this,
                "Select " + label.toLowerCase() + ":",
                "Update " + label,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );
        if (selected == null) return;

        try {
            boolean updated;
            if (label.equals("Status")) {
                updated = issueManager.updateStatus(issue.getId(), selected);
            } else if (label.equals("Department")) {
                updated = issueManager.assignDepartment(issue.getId(), selected);
            } else {
                updated = issueManager.updatePriority(issue.getId(), selected);
            }
            if (!updated) {
                showError("Unable to update issue #" + issue.getId() + ".");
                return;
            }
        } catch (DataStoreException ex) {
            showSaveError(ex);
        }
        refreshDashboard();
    }

    private void deleteSelectedIssue() {
        Issue issue = getSelectedIssue();
        if (issue == null) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete issue #" + issue.getId() + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                if (!issueManager.deleteIssue(issue.getId())) {
                    showError("Unable to delete issue #" + issue.getId() + ".");
                    return;
                }
            } catch (DataStoreException ex) {
                showSaveError(ex);
                return;
            }
            refreshDashboard();
        }
    }

    private void showUsers() {
        JDialog dialog = new JDialog(this, "Registered Users", true);
        JTextArea usersArea = new JTextArea(formatUsers(), 12, 42);
        usersArea.setEditable(false);
        usersArea.setFont(new Font("Consolas", Font.PLAIN, 13));

        JButton createAdminButton = new JButton("Create Admin");
        JButton removeAdminButton = new JButton("Remove Admin");
        JButton closeButton = new JButton("Close");
        createAdminButton.addActionListener(e -> {
            if (showCreateAdminDialog(dialog)) {
                usersArea.setText(formatUsers());
            }
        });
        removeAdminButton.addActionListener(e -> {
            if (showRemoveAdminDialog(dialog)) {
                usersArea.setText(formatUsers());
            }
        });
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(createAdminButton);
        buttons.add(removeAdminButton);
        buttons.add(closeButton);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(new JScrollPane(usersArea), BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setSize(560, 420);
        dialog.setMinimumSize(new Dimension(460, 320));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String formatUsers() {
        StringBuilder builder = new StringBuilder();
        for (User user : userManager.getAllUsers()) {
            builder.append(user.getUsername())
                    .append("    [")
                    .append(user.getRole())
                    .append("]    ")
                    .append(user.getName())
                    .append("\n");
        }
        return builder.toString();
    }

    private boolean showCreateAdminDialog(Component parent) {
        JTextField nameField = new JTextField();
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        addField(form, "Full name", nameField);
        addField(form, "Username", usernameField);
        addField(form, "Password", passwordField);
        addField(form, "Confirm password", confirmField);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    parent, form, "Create Admin Account", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }

            String name = nameField.getText().trim();
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String confirm = new String(confirmField.getPassword());

            String error = userManager.validateAdminCreation(username, password, name);
            if (error == null && !password.equals(confirm)) {
                error = "Passwords do not match.";
            }
            if (error != null) {
                showError(parent, error);
                continue;
            }

            try {
                if (!userManager.createAdmin(username, password, name)) {
                    showError(parent, "Unable to create the admin account.");
                    return false;
                }
            } catch (DataStoreException ex) {
                showSaveError(parent, ex);
                return false;
            }

            JOptionPane.showMessageDialog(
                    parent,
                    "Admin account \"" + username + "\" created.",
                    "Admin Created",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
    }

    private boolean showRemoveAdminDialog(Component parent) {
        List<String> removableAdmins = new ArrayList<>();
        for (User user : userManager.getAllUsers()) {
            if ("Admin".equalsIgnoreCase(user.getRole())
                    && !user.getUsername().equalsIgnoreCase(currentUser.getUsername())) {
                removableAdmins.add(user.getUsername());
            }
        }

        if (removableAdmins.isEmpty()) {
            showError(parent, "There are no other admin accounts to remove.");
            return false;
        }

        String selected = (String) JOptionPane.showInputDialog(
                parent,
                "Select the admin account to remove:",
                "Remove Admin",
                JOptionPane.WARNING_MESSAGE,
                null,
                removableAdmins.toArray(new String[0]),
                removableAdmins.get(0));
        if (selected == null) {
            return false;
        }

        String error = userManager.validateAdminRemoval(selected, currentUser.getUsername());
        if (error != null) {
            showError(parent, error);
            return false;
        }

        int confirm = JOptionPane.showConfirmDialog(
                parent,
                "Remove admin account \"" + selected + "\"?",
                "Confirm Remove Admin",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return false;
        }

        try {
            if (!userManager.removeAdmin(selected, currentUser.getUsername())) {
                showError(parent, "Unable to remove that admin account.");
                return false;
            }
        } catch (DataStoreException ex) {
            showSaveError(parent, ex);
            return false;
        }

        JOptionPane.showMessageDialog(
                parent,
                "Admin account \"" + selected + "\" removed.",
                "Admin Removed",
                JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    private void showChangePasswordDialog() {
        if (currentUser == null) {
            return;
        }

        JPasswordField currentField = new JPasswordField();
        JPasswordField newField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        addField(form, "Current password", currentField);
        addField(form, "New password", newField);
        addField(form, "Confirm new password", confirmField);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    this, form, "Change Password", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }

            String current = new String(currentField.getPassword());
            String newPassword = new String(newField.getPassword());
            String confirm = new String(confirmField.getPassword());

            if (!newPassword.equals(confirm)) {
                showError("New passwords do not match.");
                continue;
            }

            String error;
            try {
                error = userManager.changePassword(currentUser, current, newPassword);
            } catch (DataStoreException ex) {
                showSaveError(ex);
                return;
            }
            if (error != null) {
                showError(error);
                continue;
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Your password was changed.",
                    "Password Changed",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
    }

    private void promptForDefaultAdminPassword() {
        if (!userManager.isUsingDefaultAdminPassword(currentUser)) {
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "You are signed in with the default admin password, which is publicly documented.\n\n"
                        + "Change it now?",
                "Default Admin Password",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            showChangePasswordDialog();
        }
    }

    private Issue getSelectedIssue() {
        int row = issueTable.getSelectedRow();
        if (row < 0) {
            showError("Please select an issue first.");
            return null;
        }
        int modelRow = issueTable.convertRowIndexToModel(row);
        int id = (int) issueTableModel.getValueAt(modelRow, 0);
        return issueManager.getById(id);
    }

    private boolean isAdmin() {
        return currentUser != null && currentUser.getRole().equals("Admin");
    }

    private void showError(String message) {
        showError(this, message);
    }

    private void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showSaveError(DataStoreException ex) {
        showSaveError(this, ex);
    }

    private void showSaveError(Component parent, DataStoreException ex) {
        JOptionPane.showMessageDialog(
                parent,
                "Your change could not be saved, so it was not applied.\n\n" + ex.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE);
    }
}
