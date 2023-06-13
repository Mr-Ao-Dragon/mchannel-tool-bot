package com.microsoft.mchanneltool.pojo.local;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserToken {
    String userId;  // 用户id
    String channelUserId;   // 频道用户ID
    String userName;    // 用户昵称
    long createTime;    // 认证时间
}
