package com.microsoft.mchanneltool.factory;

import cn.siriusbot.siriuspro.bot.BotApi;
import com.alibaba.fastjson2.JSONObject;
import com.microsoft.mchanneltool.error.MsgException;
import com.microsoft.mchanneltool.pojo.local.AwaitToken;
import com.microsoft.mchanneltool.pojo.local.UserToken;
import com.microsoft.mchanneltool.utils.TokenUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.microsoft.mchanneltool.config.Constant.VERIF_LIST;
import static com.microsoft.mchanneltool.config.Constant.VERIF_LIST_COUNT;

/**
 * 用户认证工厂
 */
public class UserTokenFactory {
    private final Map<String, UserToken> tokenMap = new ConcurrentHashMap<>();    // 认证列表
    private final Map<String, AwaitToken> awaitToken = new ConcurrentHashMap<>(); // 等待认证列表

    BotApi botApi;

    public UserTokenFactory(BotApi botApi) {
        this.botApi = botApi;
    }

    /**
     * 保存数据
     */
    public void saveData() {
        int index = 0;
        for (String userId : tokenMap.keySet()) {
            UserToken token = tokenMap.get(userId);
            botApi.botManageApi().setServerConfig(VERIF_LIST + "." + index, JSONObject.toJSONString(token));
            index++;
        }
        botApi.botManageApi().setServerConfig(VERIF_LIST_COUNT, String.valueOf(index));
    }

    public void readData() {
        int count = 0;
        String countConfig = botApi.botManageApi().getServerConfig(VERIF_LIST_COUNT);
        try {
            count = Integer.parseInt(countConfig);
        } catch (Exception ignored) {

        }
        for (int i = 0; i < count; i++) {
            try {
                String serverConfig = botApi.botManageApi().getServerConfig(VERIF_LIST + "." + i);
                if (!serverConfig.isEmpty()) {
                    UserToken token = JSONObject.parseObject(serverConfig, UserToken.class);
                    if (token.getUserId() != null && token.getChannelUserId() != null) {
                        tokenMap.put(token.getUserId(), token);
                    }
                }

            } catch (Exception ignored) {

            }
        }

    }

    /**
     * 添加等待认证列表
     */
    public void addAwaitToken(String userId, String code) {
        this.detectionExpiration(); // 检验过期
        awaitToken.put(code,
                new AwaitToken()
                        .setUserId(userId)
                        .setCreateTime(System.currentTimeMillis())
                        .setVerificationCode(code)
        );
    }

    /**
     * 删除绑定信息
     */
    public int delBound(String channelUserId) {
        int count = 0;
        for (String userId : tokenMap.keySet()) {
            UserToken token = tokenMap.get(userId);
            if (channelUserId.equals(token.getChannelUserId())) {
                tokenMap.remove(userId);
                count++;
            }
        }
        this.saveData();    // 保存数据
        return count;
    }

    /**
     * 验证绑定码
     */
    public void verifyCode(String channelUserId, String userName, String code) {
        this.detectionExpiration(); // 检验过期
        if (!awaitToken.containsKey(code)) {
            throw new MsgException(1, "绑定码不存在!");
        }
        AwaitToken awaitToken = this.awaitToken.get(code);
        UserToken userToken = new UserToken()
                .setUserId(awaitToken.getUserId())
                .setChannelUserId(channelUserId)
                .setUserName(userName)
                .setCreateTime(System.currentTimeMillis());
        tokenMap.put(awaitToken.getUserId(), userToken);
        this.saveData();    // 保存数据
    }

    /**
     * 检测是否绑定
     *
     * @param userId
     * @return
     */
    public boolean validationBind(String userId) {
        this.detectionExpiration(); // 检验过期
        return tokenMap.containsKey(userId);
    }

    /**
     * 获取用户绑定的userid
     *
     * @param channelUserId
     * @return
     */
    public String getBindByChannelUserId(String channelUserId) {
        for (String userId : tokenMap.keySet()) {
            UserToken token = tokenMap.get(userId);
            if (channelUserId.equals(token.getChannelUserId())) {
                return token.getUserId();
            }
        }
        return null;
    }

    /**
     * 更换绑定
     *
     * @param channelUserId 频道id
     * @param lastUserId    最后一次登录id
     * @param nowUserId     现在登录id
     */
    public void changeTheBinding(String channelUserId, String lastUserId, String nowUserId) {
        UserToken tokenInfo = null;
        for (String userId : tokenMap.keySet()) {
            UserToken token = this.tokenMap.get(userId);
            if (channelUserId.equals(token.getChannelUserId())) {
                tokenInfo = token;
                tokenMap.remove(userId);
            }
        }
        // 插入新数据
        if (tokenInfo != null) {
            tokenInfo.setUserId(nowUserId);
            tokenMap.put(nowUserId, tokenInfo);
        }
        this.saveData();    // 保存数据

    }

    /**
     * 获取绑定信息
     */
    public AwaitToken getCodeInfo(String code) {
        this.detectionExpiration(); // 检验过期
        if (!awaitToken.containsKey(code)) {
            throw new MsgException(1, "绑定码不存在!");
        }
        return this.awaitToken.get(code);
    }

    public String randomHexadecimalNumber() {
        this.detectionExpiration(); // 检验过期
        String s = TokenUtil.randomHexadecimalNumber(8);
        while (awaitToken.containsKey(s)) {
            s = TokenUtil.randomHexadecimalNumber(8);
        }
        return s;
    }

    public String getUserName(String userId) {
        if (this.tokenMap.containsKey(userId)) {
            UserToken userToken = this.tokenMap.get(userId);
            return userToken.getUserName();
        }
        return "";
    }

    /**
     * 检测是否过期，并删除
     */
    private void detectionExpiration() {
        long t = System.currentTimeMillis();
//        for (String userId : tokenMap.keySet()){
//            UserToken userToken = tokenMap.get(userId);
//            if (t - userToken.getCreateTime() > 7 * 24 * 60 * 60 * 1000){
//                // 7 天过期
//                tokenMap.remove(userId);
//            }
//        }
        for (String code : awaitToken.keySet()) {
            AwaitToken awaitToken = this.awaitToken.get(code);
            if (t - awaitToken.getCreateTime() > 5 * 60 * 1000) {
                // 5 分钟过期
                this.awaitToken.remove(code);
            }
        }
    }
}
