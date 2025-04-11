package com.postq.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author shiguihong
 * @since 2025-04-11
 */
@Getter
@Setter
public class Table extends Item{

    private String tableName;

    public Table(){
        setItemType(ItemType.TABLE);
    }

    @Override
    public String toString() {
        return tableName;
    }
}
