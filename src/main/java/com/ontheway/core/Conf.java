package com.ontheway.core;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;

public class Conf extends PropertiesConfiguration {

    private static final Conf INSTANCE = new Conf();

    private Conf() {
    }

    public void load() throws ConfigurationException {
        URL file = this.getClass().getClassLoader().getResource("config.properties");
        if (file == null)
            throw new ConfigurationException("Could not find config.properites in classpath");
        else
            load(file.getFile());
    }

    public void load(String filename) throws ConfigurationException {
        try {
            read(new FileReader(filename));
        } catch (IOException e) {
            throw new ConfigurationException("Could not read file " + filename);
        }
    }

    public static Conf get() {
        return INSTANCE;
    }

}
