package com.addpeople.tools.utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class ResourceLoader {
    private static ImageIcon dogIcon = null;
    
    public static ImageIcon loadDogImage() {
        if (dogIcon != null) {
            return dogIcon;
        }
        
        try {
            InputStream is = ResourceLoader.class.getResourceAsStream("/golden_lab.png");
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                Image scaledImg = img.getScaledInstance(80, 80, Image.SCALE_SMOOTH);
                dogIcon = new ImageIcon(scaledImg);
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return dogIcon;
    }
    
    public static String loadProductTypes() {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = ResourceLoader.class.getResourceAsStream("/product_types.txt");
            if (is != null) {
                java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8");
                while (scanner.hasNextLine()) {
                    sb.append(scanner.nextLine()).append("\n");
                }
                scanner.close();
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}