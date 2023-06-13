package com.microsoft.mchanneltool.utils;

import cn.siriusbot.siriuspro.bot.api.pojo.member.Member;

import java.util.List;

public class RobotUtil {

    /**
     * 判断是否为频道主或管理员
     * @param member
     * @return
     */
    public static boolean judgmentManager(Member member){
        List<String> roles = member.getRoles();
        for (String role : roles){
            if (role.equals("2") || role.equals("4")){
                return true;
            }
        }
        return false;
    }

    public static String at(String userId){
        return String.format("<@!%s>", userId);
    }
}
