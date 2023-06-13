package com.microsoft.mchanneltool;

import cn.siriusbot.siriuspro.bot.BotApi;
import cn.siriusbot.siriuspro.bot.annotation.*;
import cn.siriusbot.siriuspro.bot.api.pojo.User;
import cn.siriusbot.siriuspro.bot.api.pojo.member.Member;
import cn.siriusbot.siriuspro.bot.api.tuple.Tuple;
import cn.siriusbot.siriuspro.bot.application.SiriusApplication;
import cn.siriusbot.siriuspro.bot.application.SiriusApplicationInfo;
import cn.siriusbot.siriuspro.bot.pojo.e.MessageType;
import cn.siriusbot.siriuspro.bot.pojo.message.PrivateDomainEvent.PrivateDomainMessageInfo;
import cn.siriusbot.siriuspro.web.R.R;
import cn.siriusbot.siriuspro.web.pojo.BotHttpRequest;
import cn.siriusbot.siriuspro.web.websocket.surface.WebsocketSession;
import com.alibaba.fastjson2.JSONObject;
import com.microsoft.mchanneltool.config.Constant;
import com.microsoft.mchanneltool.error.MsgException;
import com.microsoft.mchanneltool.factory.UserTokenFactory;
import com.microsoft.mchanneltool.pojo.*;
import com.microsoft.mchanneltool.pojo.local.AwaitToken;
import com.microsoft.mchanneltool.utils.RSAUtils;
import com.microsoft.mchanneltool.utils.RobotUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SiriusApp implements SiriusApplication {

    BotApi botApi;
    UserTokenFactory userTokenFactory;

    Set<WebsocketSession> sessions = new CopyOnWriteArraySet<>();

    Map<String, Boolean> verification = new ConcurrentHashMap<>();

    private boolean bindSwitch = true;  // 绑定开关
    private boolean authSwitch = true;  // 认证开关

    private void sendAll(String s) {
        for (WebsocketSession session : sessions) {
            try {
                session.send(s);
            } catch (Exception ignored) {

            }
        }
    }

    @Override
    public void SiriusAppInit(BotApi botApi) {
        this.botApi = botApi;
        this.userTokenFactory = new UserTokenFactory(botApi);
        this.userTokenFactory.readData();
    }

    @Override
    public SiriusApplicationInfo appInfo() {
        return new SiriusApplicationInfo()
                .setAppName("Mchannel-tool")
                .setAppAuthor("小银")
                .setAppDesc("我的世界")
                .setAppVersion("1.0")
                .setPackageName("com.microsoft.mchanneltool");
    }

    /**
     * ws连接事件
     */
    @OnExpandOpen
    public void expandWebSocketOpen(WebsocketSession session) {
        this.sessions.add(session);
    }

    /**
     * ws关闭事件
     */
    @OnExpandClose
    public void expandWebSocketClose(WebsocketSession session) {
        this.sessions.remove(session);
    }

    /**
     * ws消息事件
     */
    @OnExpandMessage
    public void expandWebSocketEvent(WebsocketSession session, String message) {
        System.out.println(message);
        try {
            WebSocketBody body = JSONObject.parseObject(message, WebSocketBody.class);
            JSONObject data = null;
            if (body.getData() instanceof JSONObject) {
                data = (JSONObject) body.getData();
            }
            if (data != null) {
                if (!verification.containsKey(session.getId()) || !verification.get(session.getId())) {
                    return;
                }
                switch (body.getType()) {
                    case "ChannelBind": {
                        WebSocketBody sendBody = new WebSocketBody()
                                .setId(body.getId())
                                .setType("ChannelBindResult");
                        if (!this.authSwitch) {
                            ChannelBindResult result = new ChannelBindResult()
                                    .setClose(true)
                                    .setPass(false);
                            sendBody.setData(result);
                            session.send(JSONObject.toJSONString(sendBody));
                            break;
                        }
                        ChannelBind channelBind = data.toJavaObject(ChannelBind.class);
                        // 检测是否绑定
                        if (!userTokenFactory.validationBind(channelBind.getUserId())) {
                            // 未绑定
                            String code = userTokenFactory.randomHexadecimalNumber();
                            ChannelBindResult channelBindResult = new ChannelBindResult()
                                    .setPass(false)
                                    .setVerificationCode(code);
                            sendBody.setData(channelBindResult);
                            this.userTokenFactory.addAwaitToken(channelBind.getUserId(), code);
                        } else {
                            ChannelBindResult channelBindResult = new ChannelBindResult()
                                    .setPass(true);
                            sendBody.setData(channelBindResult);
                        }
                        session.send(JSONObject.toJSONString(sendBody));
                        break;
                    }
                    case "LinkMessage": {
                        // 服务器消息
                        LinkMessage linkMessage = data.toJavaObject(LinkMessage.class);
                        String userName = this.userTokenFactory.getUserName(linkMessage.getUserId());
                        String botId = this.botApi.botManageApi().getServerConfig(Constant.ROBOT_BIND_ID);
                        String channel = this.botApi.botManageApi().getServerConfig(Constant.MSG_BIND_CHANNEL);
                        if (!channel.equals("") && !botId.equals("")) {
                            this.botApi.messageApi().sendMessage(
                                    botId,
                                    channel,
                                    String.format("[%s] -> %s", userName, linkMessage.getMessage()),
                                    null,
                                    null,
                                    null
                            );
                        }
                        break;
                    }
                    case "ChangeTheBind": {
                        // 更换绑定
                        ChangeTheBind changeTheBind = data.toJavaObject(ChangeTheBind.class);
                        if (changeTheBind.getCode() == 0) {
                            this.userTokenFactory.changeTheBinding(changeTheBind.getChannelUserId(), changeTheBind.getLastUserId(), changeTheBind.getNowUserId());
                        }
                        if (changeTheBind.getMsg() != null) {
                            String botId = this.botApi.botManageApi().getServerConfig(Constant.ROBOT_BIND_ID);
                            String channel = this.botApi.botManageApi().getServerConfig(Constant.VERIF_BIND_CHANNEL);
                            this.botApi.messageApi().sendMessage(
                                    botId,
                                    channel,
                                    RobotUtil.at(changeTheBind.getChannelUserId()) + " " + changeTheBind.getMsg(),
                                    null,
                                    changeTheBind.getMsgId(),
                                    null
                            );
                        }
                    }

                }
            } else {
                // Data为空或不为对象时
                switch (body.getType()) {
                    case "WebSocketVerify": {
                        if (body.getData() instanceof String s) {
                            String decrypt = RSAUtils.decrypt(s, Constant.PRIVATE_KEY);
                            WebSocketVerify verify = JSONObject.parseObject(decrypt, WebSocketVerify.class);
                            System.out.println(verify);
                            if (!Constant.WS_TOKEN.equals(verify.getToken())) {
                                break;
                            }
                            long t = verify.getT() * 7;
                            if (System.currentTimeMillis() - t > 15000) {
                                // 15秒延迟
                                break;
                            }
                            verification.put(session.getId(), true);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 私域消息事件
     */
    @OnEventMessage(type = MessageType.MESSAGE_CREATE)
    public void messageCreate(String bot_id, PrivateDomainMessageInfo event) {
        String channel = event.getD().getChannel_id();
        String pattern;
        Pattern r;
        Matcher m;
        // 绑定验证
        if (channel.equals(this.botApi.botManageApi().getServerConfig(Constant.VERIF_BIND_CHANNEL))) {
            // 是否在验证频道
            pattern = "/mctbind ?([0-9a-f]+)";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                if (!this.bindSwitch) {
                    this.botApi.messageApi().sendMessage(
                            bot_id,
                            event.getD().getChannel_id(),
                            String.format("%s 绑定功能已关闭!", RobotUtil.at(event.getD().getAuthor().getId())),
                            null,
                            event.getD().getId(),
                            null
                    );
                    return;
                }
                String code = m.group(1);
                try {
                    String channelUserId = event.getD().getAuthor().getId();
                    String bindUserId = this.userTokenFactory.getBindByChannelUserId(channelUserId);// 上一次登录游戏存档id
                    if (bindUserId != null) {
                        // 替换绑定信息
//                        AwaitToken codeInfo = this.userTokenFactory.getCodeInfo(code);
//                        String userId = codeInfo.getUserId();   // 当前登录游戏存档id
//                        ChangeTheBind changeTheBind = new ChangeTheBind()
//                                .setChannelUserId(channelUserId)
//                                .setLastUserId(bindUserId)
//                                .setNowUserId(userId);
//                        WebSocketBody sendBody = new WebSocketBody()
//                                .setId(UUID.randomUUID().toString())
//                                .setType("ChangeTheBind")
//                                .setData(changeTheBind);
//                        this.sendAll(JSONObject.toJSONString(sendBody));
                        this.botApi.messageApi().sendMessage(
                                bot_id,
                                event.getD().getChannel_id(),
                                String.format("%s 当前已经绑定了，无法继续绑定，请先清除绑定!", RobotUtil.at(event.getD().getAuthor().getId())),
                                null,
                                event.getD().getId(),
                                null
                        );
                        return;
                    }

                    this.userTokenFactory.verifyCode(channelUserId, event.getD().getAuthor().getUsername(), code);
                    this.botApi.messageApi().sendMessage(
                            bot_id,
                            event.getD().getChannel_id(),
                            String.format("%s 绑定游戏信息成功!", RobotUtil.at(event.getD().getAuthor().getId())),
                            null,
                            event.getD().getId(),
                            null
                    );
                } catch (MsgException e) {
                    this.botApi.messageApi().sendMessage(
                            bot_id,
                            event.getD().getChannel_id(),
                            String.format("%s %s", RobotUtil.at(event.getD().getAuthor().getId()), e.getMessage()),
                            null,
                            event.getD().getId(),
                            null
                    );
                }
                return;
            }
            // 销毁登录状态
            pattern = "/delbind";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                int num = this.userTokenFactory.delBound(event.getD().getAuthor().getId());
                if (num == 0) {
                    this.botApi.messageApi().sendMessage(
                            bot_id,
                            event.getD().getChannel_id(),
                            String.format("%s 暂无绑定游戏数据!", RobotUtil.at(event.getD().getAuthor().getId())),
                            null,
                            event.getD().getId(),
                            null
                    );
                } else {
                    this.botApi.messageApi().sendMessage(
                            bot_id,
                            event.getD().getChannel_id(),
                            String.format("%s 清除绑定成功!", RobotUtil.at(event.getD().getAuthor().getId())),
                            null,
                            event.getD().getId(),
                            null
                    );
                }
            }
        }


        // 管理员事件
        if (RobotUtil.judgmentManager(event.getD().getMember())) {
            // 设置消息转发频道
            pattern = "/set-msg-here";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                this.botApi.botManageApi().setServerConfig(Constant.ROBOT_BIND_ID, bot_id);
                this.botApi.botManageApi().setServerConfig(Constant.MSG_BIND_CHANNEL, channel);
                this.botApi.messageApi().sendMessage(
                        bot_id,
                        event.getD().getChannel_id(),
                        String.format("%s 已绑定消息转发频道到此子频道!", RobotUtil.at(event.getD().getAuthor().getId())),
                        null,
                        event.getD().getId(),
                        null
                );
            }
            // 设置认证频道
            pattern = "/set-verif-here";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                this.botApi.botManageApi().setServerConfig(Constant.VERIF_BIND_CHANNEL, channel);
                this.botApi.messageApi().sendMessage(
                        bot_id,
                        event.getD().getChannel_id(),
                        String.format("%s 已绑定信息功能频道到此子频道!", RobotUtil.at(event.getD().getAuthor().getId())),
                        null,
                        event.getD().getId(),
                        null
                );
            }
            // 配置临时开关
            pattern = "/mctool-enable-bind";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                this.bindSwitch = true;
                this.botApi.messageApi().sendMessage(
                        bot_id,
                        event.getD().getChannel_id(),
                        String.format("%s 已开启绑定功能!", RobotUtil.at(event.getD().getAuthor().getId())),
                        null,
                        event.getD().getId(),
                        null
                );
            }
            pattern = "/mctool-disable-bind";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                this.bindSwitch = false;
                this.botApi.messageApi().sendMessage(
                        bot_id,
                        event.getD().getChannel_id(),
                        String.format("%s 已关闭绑定功能!", RobotUtil.at(event.getD().getAuthor().getId())),
                        null,
                        event.getD().getId(),
                        null
                );
            }
            pattern = "/mctool-enable-auth";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                this.authSwitch = true;
                this.botApi.messageApi().sendMessage(
                        bot_id,
                        event.getD().getChannel_id(),
                        String.format("%s 已开启认证功能!", RobotUtil.at(event.getD().getAuthor().getId())),
                        null,
                        event.getD().getId(),
                        null
                );
            }
            pattern = "/mctool-disable-auth";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                this.authSwitch = false;
                this.botApi.messageApi().sendMessage(
                        bot_id,
                        event.getD().getChannel_id(),
                        String.format("%s 已关闭认证功能!", RobotUtil.at(event.getD().getAuthor().getId())),
                        null,
                        event.getD().getId(),
                        null
                );
            }
        }

        // 转发消息
        if (event.getD().getContent() != null && channel.equals(this.botApi.botManageApi().getServerConfig(Constant.MSG_BIND_CHANNEL))) {
            // 格式化消息
            String content = event.getD().getContent();
            pattern = "<@!(\\d+)>";
            r = Pattern.compile(pattern);
            m = r.matcher(event.getD().getContent());
            if (m.find()) {
                Tuple<Member, String> memberInfo = botApi.memberApi().getMemberInfo(bot_id, event.getD().getGuild_id(), m.group(1));
                content = content.replace(m.group(0), memberInfo.getFirst().getNick());
            }
            //
            User author = event.getD().getAuthor();
            LinkMessage linkMessage = new LinkMessage()
                    .setUserName(author.getUsername())
                    .setMessage(content);
            WebSocketBody sendBody = new WebSocketBody()
                    .setId(UUID.randomUUID().toString())
                    .setType("LinkMessage")
                    .setData(linkMessage);
            this.sendAll(JSONObject.toJSONString(sendBody));
        }
    }

    @OnWebRequestEvent(name = "command")
    public R command(BotHttpRequest request) {
        if (!request.getLocalIp().equals(request.getSourceIp())) {
            throw new MsgException(500, "暂无权限调用");
        }
        JSONObject json = JSONObject.parseObject(request.getBody());
        String cmd = json.getString("cmd");
        if (cmd == null) {
            throw new MsgException(500, "指令错误");
        }
        switch (cmd) {
            case "exit": {
                this.botApi.botManageApi().closeFrame();
                break;
            }
            case "reload": {
                WebSocketBody sendBody = new WebSocketBody()
                        .setId(UUID.randomUUID().toString())
                        .setType("McReload");
                this.sendAll(JSONObject.toJSONString(sendBody));
                break;
            }
        }
        return new R()
                .setMsg("处理完成");
    }

}
