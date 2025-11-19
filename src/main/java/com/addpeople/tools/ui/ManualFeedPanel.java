package com.addpeople.tools.ui;

import com.addpeople.tools.utils.ResourceLoader;
import javax.swing.*;
import java.awt.*;

public class ManualFeedPanel extends JPanel {
    private static final Color BLUE_BG = new Color(10, 29, 68);
    
    private JTextField companyField;
    private JTextField currencyField;
    private JTextArea urlsTextArea;
    private JCheckBox discoverCheckBox;
    private JCheckBox openAfterCheckBox;
    
    public ManualFeedPanel() {
        setLayout(new BorderLayout());
        setBackground(BLUE_BG);
        
        initComponents();
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(BLUE_BG);
        
        // Dog image
        ImageIcon dogIcon = ResourceLoader.loadDogImage();
        if (dogIcon != null) {
            JLabel dogLabel = new JLabel(dogIcon);
            dogLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            mainPanel.add(dogLabel);
            mainPanel.add(Box.createVerticalStrut(10));
        }
        
        // Company name
        JLabel companyLabel = new JLabel("Enter the company name:");
        companyLabel.setForeground(Color.WHITE);
        companyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(companyLabel);
        
        companyField = new JTextField(30);
        companyField.setMaximumSize(new Dimension(400, 25));
        mainPanel.add(companyField);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Currency code
        JLabel currencyLabel = new JLabel("Enter currency code (e.g. GBP, USD):");
        currencyLabel.setForeground(Color.WHITE);
        currencyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(currencyLabel);
        
        currencyField = new JTextField(20);
        currencyField.setMaximumSize(new Dimension(200, 25));
        mainPanel.add(currencyField);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // URLs text area
        JLabel urlsLabel = new JLabel("Paste product URLs (one per line):");
        urlsLabel.setForeground(Color.WHITE);
        urlsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(urlsLabel);
        
        urlsTextArea = new JTextArea(10, 50);
        urlsTextArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(urlsTextArea);
        scrollPane.setMaximumSize(new Dimension(600, 200));
        mainPanel.add(scrollPane);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Checkboxes
        discoverCheckBox = new JCheckBox(
            "If a line is a shop/collection/category page, auto-find product links", true);
        discoverCheckBox.setForeground(Color.WHITE);
        discoverCheckBox.setBackground(BLUE_BG);
        discoverCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(discoverCheckBox);
        
        openAfterCheckBox = new JCheckBox("Open file when finished", false);
        openAfterCheckBox.setForeground(Color.WHITE);
        openAfterCheckBox.setBackground(BLUE_BG);
        openAfterCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(openAfterCheckBox);
        mainPanel.add(Box.createVerticalStrut(20));
        
        // Generate button
        JButton generateButton = new JButton("Generate Feed");
        generateButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        generateButton.addActionListener(e -> startScraping());
        mainPanel.add(generateButton);
        
        // Note
        JLabel noteLabel = new JLabel("Note: Not all sites are guaranteed to work, but many do.");
        noteLabel.setForeground(Color.LIGHT_GRAY);
        noteLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(noteLabel);
        
        JScrollPane mainScrollPane = new JScrollPane(mainPanel);
        mainScrollPane.getViewport().setBackground(BLUE_BG);
        add(mainScrollPane, BorderLayout.CENTER);
    }
    
    private void startScraping() {
        String company = companyField.getText().trim();
        String currency = currencyField.getText().trim().toUpperCase();
        String urls = urlsTextArea.getText().trim();
        
        if (company.isEmpty() || currency.isEmpty() || urls.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "All fields must be filled.", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JOptionPane.showMessageDialog(this, 
            "Scraping functionality to be implemented", 
            "Info", 
            JOptionPane.INFORMATION_MESSAGE);
    }
}