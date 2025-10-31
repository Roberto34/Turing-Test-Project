import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import javax.swing.text.MaskFormatter;

class Main extends JFrame{
    String databaseUrl;
    String databaseUsername;
    String databasePassword;

    static List<Person> contacts;
    static Person selectedContact;

    static JToolBar toolbar;
    static JTable contactsTable;

    static class ContactsTableModel implements TableModel {
        @Override
        public int getRowCount() {
            return contacts.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> "Nome";
                case 1 -> "Cognome";
                case 2 -> "Telefono";
                default -> throw new IllegalStateException("Unexpected value: " + columnIndex);
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return switch (columnIndex) {
                case 0 -> contacts.get(rowIndex).getFirstName();
                case 1 -> contacts.get(rowIndex).getLastName();
                case 2 -> contacts.get(rowIndex).getPhoneNumber();
                default -> throw new IllegalStateException("Unexpected value: " + columnIndex);
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {}

        @Override
        public void addTableModelListener(TableModelListener l) {}

        @Override
        public void removeTableModelListener(TableModelListener l) {}
    }

    public void refreshContacts() {
        try (
                Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM contacts.person");
                ResultSet result = statement.executeQuery()
        ) {
            contacts.clear();

            while (result.next()) {
                contacts.add(new Person(
                        result.getInt("Id"),
                        result.getString("FirstName"),
                        result.getString("LastName"),
                        result.getString("Address"),
                        result.getString("PhoneNumber"),
                        result.getInt("Age")
                ));
            }

            if (contactsTable != null) {
                SwingUtilities.updateComponentTreeUI(contactsTable);
                contactsTable.clearSelection();
                selectedContact = null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    void main() throws URISyntaxException, IOException {
        String applicationDirectoryPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();

        Properties properties = new Properties();
        properties.load(new FileInputStream(applicationDirectoryPath + "/credenziali_database.properties"));

        databaseUrl = "jdbc:mysql://" + properties.getProperty("ServerIp") + ":" + properties.getProperty("Porta") + "/contacts?serverTimezone=UTC";
        databaseUsername = properties.getProperty("Username");
        databasePassword = properties.getProperty("Password");

        this.setTitle("Rubrica");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setResizable(false);

        contacts = new ArrayList<>();

        Container loginScreen = getLoginScreen();

        this.setContentPane(loginScreen);

        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    private Container getLoginScreen() {
        Container loginScreen = new Container();
        loginScreen.setLayout(new GridLayout(3, 1));

        // Username
        JPanel usernamePanel = new JPanel();

        JLabel usernameLabel = new JLabel("Nome Utente");
        usernamePanel.add(usernameLabel);
        JTextField usernameField = new JTextField(16);
        usernameField.addActionListener(action -> {
            if (action.getID() == ActionEvent.ACTION_PERFORMED) usernameField.transferFocus();
        });
        usernamePanel.add(usernameField);

        loginScreen.add(usernamePanel);

        // Password
        JPanel passwordPanel = new JPanel();

        JLabel passwordLabel = new JLabel("Password");
        passwordPanel.add(passwordLabel);
        JTextField passwordField = new JTextField(16);
        passwordField.addActionListener(action -> {
            if (action.getID() == ActionEvent.ACTION_PERFORMED) {
                String username = usernameField.getText();
                String password = passwordField.getText();

                attemptLogin(username, password);
            }
        });
        passwordPanel.add(passwordField);

        loginScreen.add(passwordPanel);

        JPanel buttonPanel = new JPanel();

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(_ -> {
            String username = usernameField.getText();
            String password = passwordField.getText();

            attemptLogin(username, password);
        });
        buttonPanel.add(loginButton, BorderLayout.CENTER);
        loginScreen.add(buttonPanel);

        return loginScreen;
    }

    private Container getMainScreen() {
        Container mainScreen = new Container();
        mainScreen.setLayout(new BorderLayout());

        toolbar = getToolbar();
        mainScreen.add(toolbar, BorderLayout.NORTH);

        contactsTable = getContactsTable();
        mainScreen.add(new JScrollPane(contactsTable), BorderLayout.CENTER);
        return mainScreen;
    }

    private JTable getContactsTable() {
        contactsTable = new JTable();

        contactsTable.setModel(new ContactsTableModel());
        contactsTable.setCellSelectionEnabled(false);
        contactsTable.setRowSelectionAllowed(true);
        contactsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        contactsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            int index = contactsTable.getSelectedRow();
            selectedContact = index >= 0 ? contacts.get(index) : null;
        });

        return contactsTable;
    }

    private JToolBar getToolbar() {
        toolbar = new JToolBar();

        toolbar.setLayout(new FlowLayout(FlowLayout.CENTER));

        Action addPersonAction = new AbstractAction("Aggiungi nuovo contatto") {
            @Override
            public void actionPerformed(ActionEvent event) {
                System.out.println("Opening new person dialog");

                try {
                    getPersonDialog(false).setVisible(true);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        toolbar.add(addPersonAction);

        toolbar.add(new JPanel());

        Action editPersonAction = new AbstractAction("Modifica contatto") {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (selectedContact == null) {
                    IO.println("No contact selected");

                    JOptionPane.showMessageDialog(Main.this, "Nessun contatto selezionato", "Errore", JOptionPane.ERROR_MESSAGE);

                    return;
                }

                IO.println("Editing contact at index: " + contacts.indexOf(selectedContact));

                try {
                    getPersonDialog(true).setVisible(true);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        toolbar.add(editPersonAction);

        toolbar.add(new JPanel());

        Action removePersonAction = new AbstractAction("Elimina contatto") {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (selectedContact == null) {
                    IO.println("No contact selected");

                    JOptionPane.showMessageDialog(Main.this, "Nessun contatto selezionato", "Errore", JOptionPane.ERROR_MESSAGE);

                    return;
                }

                IO.println("Trying to remove contact with Id: " + selectedContact.getId());

                int res = JOptionPane.showOptionDialog(
                        null,
                        "Eliminare il contatto " + selectedContact.getFirstName() +
                                " " + selectedContact.getLastName() + "?",
                        "Conferma eliminazione",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        new String[]{"Conferma", "Annulla"},
                        null
                );

                if (res != JOptionPane.YES_OPTION) return;

                try (
                        Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
                        PreparedStatement statement = connection.prepareStatement(
                                "DELETE FROM contacts.person WHERE Id = " +  selectedContact.getId())
                ) {
                    statement.execute();
                    refreshContacts();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        toolbar.add(removePersonAction);

        return toolbar;
    }

    private JDialog getPersonDialog(boolean edit) throws ParseException {
        JDialog dialog = new JDialog();

        if (edit) dialog.setTitle("Modifica contatto");
        else dialog.setTitle("Nuovo contatto");

        dialog.setMinimumSize(new Dimension(0, 360));
        dialog.setSize(new Dimension(getSize().width * 2 / 3, getSize().height * 2 / 3));
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);

        Container contentPane = dialog.getContentPane();


        Container fields = new Container();
        fields.setLayout(new GridLayout(5, 1));

        // First name
        JPanel firstNamePanel = new JPanel();

        JLabel firstNameLabel = new JLabel("Nome");
        firstNamePanel.add(firstNameLabel);
        JTextField firstNameField = new JTextField(24);
        if (edit && selectedContact != null) firstNameField.setText(selectedContact.getFirstName());
        firstNameField.addActionListener(action -> {
            if (action.getID() == ActionEvent.ACTION_PERFORMED) firstNameField.transferFocus();
        });
        firstNamePanel.add(firstNameField);

        // Last name
        JPanel lastNamePanel = new JPanel();

        JLabel lastNameLabel = new JLabel("Cognome");
        lastNamePanel.add(lastNameLabel);
        JTextField lastNameField = new JTextField(24);
        if (edit && selectedContact != null) lastNameField.setText(selectedContact.getLastName());
        lastNameField.addActionListener(action -> {
            if (action.getID() == ActionEvent.ACTION_PERFORMED) lastNameField.transferFocus();
        });
        lastNamePanel.add(lastNameField);

        // Phone number
        JPanel phoneNumberPanel = new JPanel();

        JLabel phoneNumberLabel = new JLabel("Telefono");
        phoneNumberPanel.add(phoneNumberLabel);

        JFormattedTextField phoneNumberField = new JFormattedTextField(new MaskFormatter("##########"));
        phoneNumberField.setColumns(24);

        //JTextField phoneNumberField = new JTextField(24);
        if (edit && selectedContact != null) phoneNumberField.setText(selectedContact.getPhoneNumber());
        phoneNumberField.addActionListener(action -> {
            if (action.getID() == ActionEvent.ACTION_PERFORMED) phoneNumberField.transferFocus();
        });
        phoneNumberPanel.add(phoneNumberField);

        // Address
        JPanel addressPanel = new JPanel();

        JLabel addressLabel = new JLabel("Indirizzo");
        addressPanel.add(addressLabel);
        JTextField addressField = new JTextField(24);
        if (edit && selectedContact != null) addressField.setText(selectedContact.getAddress());
        addressPanel.add(addressField);

        // Age
        JPanel agePanel = new JPanel();

        JLabel ageLabel = new JLabel("Età");
        agePanel.add(ageLabel);
        JSpinner ageSpinner = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
        ((JSpinner.DefaultEditor) ageSpinner.getEditor()).getTextField().setColumns(4);
        if (edit && selectedContact != null) ageSpinner.setValue(selectedContact.getAge());
        agePanel.add(ageSpinner);


        fields.add(firstNamePanel);
        fields.add(lastNamePanel);
        fields.add(phoneNumberPanel);
        fields.add(addressPanel);
        fields.add(agePanel);

        contentPane.add(fields, BorderLayout.CENTER);


        JPanel buttonPanel = new JPanel();

        JButton okButton = new JButton("Salva");
        okButton.addActionListener(_ -> {
            IO.println("Ok pressed");

            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String address = addressField.getText();
            String phoneNumber = phoneNumberField.getText();
            Integer age = (Integer) ageSpinner.getValue();

            try (
                    Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
                    PreparedStatement statement = edit ? connection.prepareStatement(
                            "UPDATE contacts.person set FirstName = ?, LastName = ?, Address = ?, PhoneNumber = ?, Age = ? WHERE ID = " + selectedContact.getId()
                    ) : connection.prepareStatement(
                            "INSERT INTO contacts.person(FirstName, LastName, Address, PhoneNumber, Age)" +
                                    "VALUES (?, ?, ?, ?, ?)"
                    )
            ) {
                if (firstName.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Il campo Nome è vuoto",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!firstName.matches("^[a-zA-ZàáâäãåąčćęèéêëėįìíîïłńòóôöõøùúûüųūÿýżźñçšžæÀÁÂÄÃÅĄĆČĖĘÈÉÊËÌÍÎÏĮŁŃÒÓÔÖÕØÙÚÛÜŲŪŸÝŻŹÑßÇŒÆŠŽ∂ð ,.'-]+$")) {
                    JOptionPane.showMessageDialog(null, "Il campo Nome contiene caratteri non validi",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (lastName.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Il campo Cognome è vuoto",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!lastName.matches("^[a-zA-ZàáâäãåąčćęèéêëėįìíîïłńòóôöõøùúûüųūÿýżźñçšžæÀÁÂÄÃÅĄĆČĖĘÈÉÊËÌÍÎÏĮŁŃÒÓÔÖÕØÙÚÛÜŲŪŸÝŻŹÑßÇŒÆŠŽ∂ð ,.'-]+$")) {
                    JOptionPane.showMessageDialog(null, "Il campo Cognome contiene caratteri non validi",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (address.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Il campo Indirizzo è vuoto",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (phoneNumber.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Il campo Telefono è vuoto",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!phoneNumber.matches("^\\d{10}$")) {
                    JOptionPane.showMessageDialog(null, "Il campo Telefono contiene caratteri non validi",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Should never come up since the JSpinner prevents values < 0
                if (age < 0) {
                    JOptionPane.showMessageDialog(null, "L' età è negativa",  "Errore", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                statement.setString(1, firstName);
                statement.setString(2, lastName);
                statement.setString(3, address);
                statement.setString(4, phoneNumber);
                statement.setInt(5, age);

                statement.execute();

                dialog.dispose();
                refreshContacts();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        buttonPanel.add(okButton);

        buttonPanel.add(new JPanel());
        buttonPanel.add(new JPanel());
        buttonPanel.add(new JPanel());

        JButton cancelButton = new JButton("Annulla");
        cancelButton.addActionListener(_ -> {
            IO.println("Operation cancelled");
            dialog.dispose();
        });
        buttonPanel.add(cancelButton);

        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        return dialog;
    }


    private void attemptLogin(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Si prega di inserire il proprio nome utente e la propria password", "Errore", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (
                Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM contacts.user WHERE username = ?")
        ) {
            IO.println("Trying to log in with credentials: " + username + " - " + password);

            statement.setString(1, username);

            ResultSet result = statement.executeQuery();

            if (result.next() && new BCryptPasswordEncoder().matches(password, result.getString("Password"))) {
                this.setContentPane(getMainScreen());
                SwingUtilities.updateComponentTreeUI(Main.this);
                refreshContacts();
                this.setSize(new Dimension(469, 504));
                this.setLocationRelativeTo(null);
            } else {
                JOptionPane.showMessageDialog(null, "Nome utente o password non validi", "Errore", JOptionPane.ERROR_MESSAGE);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}