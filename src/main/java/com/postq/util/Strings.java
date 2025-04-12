package com.postq.util;

import java.util.Objects;

public class Strings {
    public static boolean isEmpty(String value){
        return null == value || value.length() == 0;
    }

    public static String trim(String value){
        if(Objects.isNull(value)){
            return null;
        }
        return value.trim();
    }
}
