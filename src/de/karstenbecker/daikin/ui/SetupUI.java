package de.karstenbecker.daikin.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import de.karstenbecker.daikin.Daikin;
import de.karstenbecker.daikin.DaikinPollingSettings;
import de.karstenbecker.daikin.DaikinProperty;
import de.karstenbecker.daikin.DaikinProperty.PollingInterval;

public class SetupUI extends JPanel {
    private static final int GAP = 8;
    private static JFrame frame;
    private boolean DEBUG = false;
    private BeanPropertyModel<DaikinProperty> model;
    private DaikinPollingSettings settings;
    private JTextField homieURLText;
    private JTextField homieUserText;
    private JTextField homiePasswordText;
    private JTextField homieDeviceNameText;
    private JTextField daikinIpAddressText;
    private Daikin daikin;
    private JButton discoverBtn;
    private List<String> endPoints;

    public SetupUI(DaikinPollingSettings settings, List<String> endPoints) throws Exception {
        super(createLayout());
        daikin = new Daikin();
        this.settings = settings;
        this.endPoints=endPoints;
        model = new BeanPropertyModel<DaikinProperty>(DaikinProperty.class, settings.getProperties(), "pollInterval",
                "name", "unit", "format", "dataType", "value");
        final JTable table = new JTable(model);
        table.setPreferredScrollableViewportSize(new Dimension(500, 70));
        table.setFillsViewportHeight(true);
        model.updateColumns(table);

        // Create the scroll pane and add the table to it.
        JScrollPane scrollPane = new JScrollPane(table);

        add(createButtons(), BorderLayout.PAGE_START);
        add(scrollPane, BorderLayout.CENTER);
        add(createHomieSettings(), BorderLayout.PAGE_END);
    }

    private static BorderLayout createLayout() {
        BorderLayout layout = new BorderLayout();
        layout.setHgap(GAP);
        layout.setVgap(GAP);
        return layout;
    }

    private JPanel createHomieSettings() {
        GridLayout layout = new GridLayout(4, 2);
        layout.setHgap(GAP);
        layout.setVgap(GAP);
        JPanel mqtt = new JPanel(layout);
        homieURLText = new JTextField(settings.getHomieServer(), 30);
        addField(mqtt, "MQTT Broker URL", homieURLText);
        homieUserText = new JTextField(settings.getHomieUser(), 15);
        addField(mqtt, "MQTT Broker User", homieUserText);
        homiePasswordText = new JTextField(settings.getHomiePassword(), 15);
        addField(mqtt, "MQTT Broker Password", homiePasswordText);
        homieDeviceNameText = new JTextField(settings.getHomieDeviceName(), 15);
        homieDeviceNameText.setInputVerifier(new InputVerifier() {

            @Override
            public boolean verify(JComponent input) {
                return homieDeviceNameText.getText().matches("[a-z0-9-]+");
            }
        });
        addField(mqtt, "Device name", homieDeviceNameText);
        return mqtt;
    }

    private void addField(JPanel mqtt, String string, JTextField textField) {
        mqtt.add(new JLabel(string));
        mqtt.add(textField);
        textField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                warn();
            }

            public void removeUpdate(DocumentEvent e) {
                warn();
            }

            public void insertUpdate(DocumentEvent e) {
                warn();
            }

            public void warn() {
                updateSettingsModel();
            }
        });
    }

    private JToolBar createButtons() {
        // Add the scroll pane to this panel.
        JToolBar buttons = new JToolBar("DaikinButtons");
        buttons.add(new JLabel("IP of Daikin"));
        daikinIpAddressText = new JTextField(settings.getDaikinIP(), 15);
        buttons.add(daikinIpAddressText);
        discoverBtn = new JButton("Discover");
        discoverBtn.addActionListener(this::discover);
        buttons.add(discoverBtn);
        buttons.addSeparator();
        JButton guessPolling = new JButton("Guess Polling");
        guessPolling.addActionListener(this::guessPolling);
        buttons.add(guessPolling);
        JButton guessUnit = new JButton("Guess Units");
        guessUnit.addActionListener(this::guessUnits);
        buttons.add(guessUnit);
        JButton saveFile = new JButton("Save file");
        saveFile.addActionListener(this::saveFile);
        buttons.add(saveFile);
        return buttons;
    }

    public void discover(ActionEvent e) {
        daikinIpAddressText.setEditable(false);
        discoverBtn.setEnabled(false);
        try {
            new Thread(() -> {
                try {
                    runDiscovery();
                    discoverBtn.setEnabled(true);
                    daikinIpAddressText.setEditable(true);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }).start();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private void runDiscovery() throws Exception, IOException {
        Set<DaikinProperty> discoverProperties = daikin
                .discoverProperties(new InetSocketAddress(settings.getDaikinIP(), settings.getDaikinPort()), endPoints);
        model.data = new ArrayList<DaikinProperty>(discoverProperties);
        model.fireTableDataChanged();
    }

    public void saveFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Specify a file to save");
        fileChooser.setSelectedFile(new File("PollingSettings.json"));
        int userSelection = fileChooser.showSaveDialog(frame);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            System.out.println("Save as file: " + fileToSave.getAbsolutePath());
            updateSettingsModel();
            String json = settings.toJSON(false);
            try {
                Files.writeString(fileToSave.toPath(), json);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void updateSettingsModel() {
        settings.setProperties(model.data);
        settings.setDaikinIP(daikinIpAddressText.getText());
        settings.setHomieDeviceName(homieDeviceNameText.getText());
        settings.setHomiePassword(homiePasswordText.getText());
        settings.setHomieUser(homieUserText.getText());
        settings.setHomieServer(homieURLText.getText());
    }

    public void guessUnits(ActionEvent e) {
        for (DaikinProperty p : model.data) {
            String name = p.getName().toLowerCase();
            if (name.contains("temperature")) {
                p.setUnit("°C");
                model.setDirty(true);
                continue;
            }
        }
        if (model.isDirty())
            model.fireTableDataChanged();
    }

    public void guessPolling(ActionEvent e) {
        for (DaikinProperty p : model.data) {
            String name = p.getName().toLowerCase();
            if (name.contains("childlock")) {
                p.setPollInterval(PollingInterval.NEVER);
                model.setDirty(true);
                continue;
            }
            if (name.contains("unitinfo") || name.contains("unitprofile") || name.contains("unitidentifier")
                    || name.contains("datetime")) {
                p.setPollInterval(PollingInterval.ONCE);
                model.setDirty(true);
                continue;
            }
            if (name.contains("schedule/list") || name.contains("holiday")) {
                p.setPollInterval(PollingInterval.DAILY);
                model.setDirty(true);
                continue;
            }
            if (name.contains("sensor") || name.contains("operation") || name.contains("error")
                    || name.endsWith("state")) {
                p.setPollInterval(PollingInterval.MINUTELY);
                model.setDirty(true);
                continue;
            }
            if (name.contains("consumption")) {
                p.setPollInterval(PollingInterval.BI_HOURLY);
                model.setDirty(true);
                continue;
            }

        }
        if (model.isDirty())
            model.fireTableDataChanged();
    }

    /**
     * Create the GUI and show it. For thread safety, this method should be invoked
     * from the event-dispatching thread.
     * @param endPoints 
     */
    private static void createAndShowGUI(DaikinPollingSettings settings, List<String> endPoints) {
        // Create and set up the window.
        frame = new JFrame("Daikin Adapter to Homie Setup");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        try {
            SetupUI newContentPane = new SetupUI(settings, endPoints);
            newContentPane.setOpaque(true); // content panes must be opaque
            Border padding = BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP);
            newContentPane.setBorder(padding);
            frame.setContentPane(newContentPane);
            if (settings.getDaikinIP() == null || settings.getDaikinIP().isBlank()) {
                CustomDialog dialog = new CustomDialog(frame, "", newContentPane);
                dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                dialog.pack();
                dialog.setVisible(true);
            } else {
                frame.pack();
                frame.setVisible(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void show(DaikinPollingSettings settings, List<String> endPoints) {
        // Schedule a job for the event-dispatching thread:
        // creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI(settings, endPoints);
            }
        });
    }

    public void openForIP(String ip) {
        System.out.println("SetupUI.openForIP()" + ip);
        settings.setDaikinIP(ip);
        daikinIpAddressText.setText(ip);
        // Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    public String discoverIP() throws Exception {
        InetSocketAddress[] discoverDevice = daikin.discoverDevice();
        if (discoverDevice.length > 0)
            return discoverDevice[0].getHostString();
        return null;
    }

}
