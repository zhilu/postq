package com.postq.service;

import com.postq.model.Database;
import com.postq.model.Item;
import javafx.scene.control.TreeItem;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE = System.getProperty("user.home")+ "/database_config.properties";
    public static ConfigManager instance = new ConfigManager();
    private Properties properties;

    public ConfigManager() {
        this.properties = new Properties();
    }

    // 保存数据库配置到 Properties 文件
    public void saveConfig(String title, String host, String port, String userName, String password, String databaseName) {
        properties.setProperty(title + ".host", host);
        properties.setProperty(title + ".port", port);
        properties.setProperty(title + ".userName", userName);
        properties.setProperty(title + ".password", password);
        properties.setProperty(title + ".databaseName", databaseName);

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Database Connection Configurations");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 从 Properties 文件读取所有数据库的配置
    public Properties loadConfig() {
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            properties.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }

    public String[] getAllDatabaseTitles() {
        return properties.keySet().stream()
                .map(Object::toString)
                .filter(key -> key.endsWith(".host"))
                .map(key -> key.replace(".host", ""))
                .toArray(String[]::new);
    }

    public List<Item> getDatabases() {
        Properties properties = instance.loadConfig();
        List<Item> databases = new ArrayList<>();
        if (!properties.isEmpty()) {
            String[] dbTitles = instance.getAllDatabaseTitles();
            for (String dbTitle : dbTitles) {
                databases.add( instance.getDatabase(dbTitle));
            }
        }
        return databases;
    }

    public Database getDatabase(String dbTitle) {
        Database db = new Database();
        db.setTitle(dbTitle);
        db.setHost(properties.getProperty(dbTitle + ".host"));
        db.setPort(properties.getProperty(dbTitle + ".port"));
        db.setDatabaseName(properties.getProperty(dbTitle + ".databaseName"));
        db.setUserName(properties.getProperty(dbTitle + ".userName"));
        db.setPassword(properties.getProperty(dbTitle + ".password"));
        return db;
    }
}
