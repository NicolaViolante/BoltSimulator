package org.uniroma2.PMCSN.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationManager {
    /*si tratta della classe per leggere dal file di configurazione */

    private final Properties properties = new Properties();

    public ConfigurationManager() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("Sorry, unable to find config.properties");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration file", e);
        }
    }

    public String getString(String section, String key) {
        String value = properties.getProperty(section + "." + key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + section + "." + key);
        }
        return value;
    }

    public int getInt(String section, String key) {
        String value = properties.getProperty(section + "." + key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + section + "." + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for key: " + section + "." + key, e);
        }
    }

    public double getDouble(String section, String key) {
        String value = properties.getProperty(section + "." + key);
        if (value == null) {
            throw new IllegalArgumentException("Key not found: " + section + "." + key);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double value for key: " + section + "." + key, e);
        }
    }
}
