package com.postq.util;

public class SQLs {

    public static String getSQL(String text, int pos){
        int start = pos - 1;
        while (start >= 0 && text.charAt(start) != ';') {
            start--;
        }

        int end = pos > 0 ? pos - 1 : 0;
        while (end < text.length() && text.charAt(end) != ';') {
            end++;
        }

        if(end >= text.length()){
            end--;
        }

        String sql = text.substring(start + 1, end + 1);
        return sql.trim();
    }

}
