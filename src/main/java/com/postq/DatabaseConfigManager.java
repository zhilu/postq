package com.postq;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class DatabaseConfigManager {
    private static final String CONFIG_FILE = System.getProperty("user.home")+ "/database_config.properties";
    private Properties properties;

    public DatabaseConfigManager() {
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

    // 获取所有已保存的数据库标题
    public String[] getAllDatabaseTitles() {
        return properties.keySet().stream()
                .map(Object::toString)
                .filter(key -> key.endsWith(".host"))  // 假设每个数据库配置都有 host 字段
                .map(key -> key.replace(".host", ""))
                .toArray(String[]::new);
    }
}
