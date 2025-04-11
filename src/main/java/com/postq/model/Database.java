package com.postq.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author shiguihong
 * @since 2025-04-11
 */
@Getter
@Setter
public class Database extends Item{
    private String title;
    private String host;
    private String port;
    private String databaseName;
    private String userName;
    private String password;

    public Database(){
        setItemType(ItemType.DB);
    }

    public String getUrl(){
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    }

    @Override
    public String toString() {
        return title;
    }
}
