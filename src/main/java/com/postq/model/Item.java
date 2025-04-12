package com.postq.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author shiguihong
 * @since 2025-04-11
 */
@Setter
@Getter
public class Item {
    private ItemType itemType;

    public boolean isTable() {
        return ItemType.TABLE.equals(itemType);
    }
}
