package com.addpeople.tools.ui;

import javax.swing.*;

public class MainWindow extends JFrame {
    private JTabbedPane tabbedPane;
    
    public MainWindow() {
        setTitle("Add People Tools");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        
        initComponents();
    }
    
    private void initComponents() {
        tabbedPane = new JTabbedPane();
        
        // Add tabs
        tabbedPane.addTab("Manual Feed", new ManualFeedPanel());
        
        add(tabbedPane);
    }
}