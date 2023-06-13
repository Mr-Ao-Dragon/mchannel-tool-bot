package com.microsoft.mchanneltool.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ChannelBindResult {
    Boolean close = false;  // 是否关闭验证
    Boolean pass;   // 是否允许进入
    String verificationCode;    // 验证码
}
