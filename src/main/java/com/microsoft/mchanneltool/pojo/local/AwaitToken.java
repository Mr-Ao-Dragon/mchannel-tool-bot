package com.microsoft.mchanneltool.pojo.local;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AwaitToken {
    String userId;  // 用户id
    long createTime;    // 创建时间
    String verificationCode;    // 验证码
}
