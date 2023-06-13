package com.microsoft.mchanneltool.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更换绑定
 */
@Data
@Accessors(chain = true)
public class ChangeTheBind{
    String msgId;       // 触发消息id
    String channelUserId;   // 频道用户id
    String lastUserId;  // 最后一次登录id
    String nowUserId;   // 现在用户id
    String msg;       // 错误
    int code = -1;           // 0 成功 其他失败
}