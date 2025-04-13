package com.postq.util;

import java.util.*;

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

    public static List<String> split(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static final String[] KEYWORDS = new String[]{
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE",
            "CREATE", "TABLE", "DROP", "ALTER", "INTO", "VALUES",
            "JOIN", "ON", "AS", "AND", "OR", "NOT", "NULL", "IS","LIMIT"
    };
    private static final HashSet<String> keywords = new HashSet<>(Arrays.asList(KEYWORDS));

    public static boolean isKeyword(String word) {
        return keywords.contains(word.toUpperCase());
    }

    public static boolean endWith(String s, char c) {
        return s.charAt(s.length()-1) == c;
    }

    public static int indexOf(String s, char c) {
        if (isEmpty(s)) {
            return -1;
        }
        return s.indexOf(c);
    }

    public static boolean mayField(String text, String word) {
        return indexOf(word,'.') > -1;
    }

    public static boolean isTable(String text, String word) {
        int end = text.indexOf(word);
        if (end == -1) {
            return false;
        }
        List<String> tokens = Strings.split(text);
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (Strings.endWith(token, ',')) {
                token = token.substring(0, token.length() - 1);
            }
            if (Strings.isKeyword(token)) {
                if ("from".equalsIgnoreCase(token) || "join".equalsIgnoreCase(token)) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    public static String getTableName(String text, String word) {
        String tableName = word;
        int idx = indexOf(word, '.');
        if((idx > -1)){
            tableName = word.substring(0, idx);
        }
        int end = text.indexOf(word);
        if (end == -1) {
            return null;
        }
        List<String> tokens = Strings.split(text);
        boolean findWhere = false;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if(!findWhere && !"where".equalsIgnoreCase(token)) {
                continue;
            }else if("where".equalsIgnoreCase(token)){
                findWhere = true;
                continue;
            }
            if("from".equalsIgnoreCase(token)){
                break;
            }
            if(endWith(token,',')){
                token = token.substring(0, token.length()-1);
            }
            if(tableName.equalsIgnoreCase(token)){
                if(i>1){
                    String last = tokens.get(i-1);
                    if(isKeyword(last) || endWith(last,',')){
                        return tableName;
                    }
                    return last;
                }
            }
        }
        return null;
    }
}
