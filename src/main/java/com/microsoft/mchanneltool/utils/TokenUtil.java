package com.microsoft.mchanneltool.utils;

import java.util.Random;

public class TokenUtil {

    private static final String[] hexadecimalNumber = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"
    };

    /**
     * 随机生成十六进制数
     */
    public static String randomHexadecimalNumber(int len) {
        StringBuilder sb = new StringBuilder();
        Random ra = new Random();
        for (int i = 0; i < len; i++) {
            int a = (int) (Math.random() * 16);
            sb.append(hexadecimalNumber[a]);
        }
        return sb.toString();
    }
}
