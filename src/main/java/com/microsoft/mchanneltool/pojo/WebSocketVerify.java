package com.microsoft.mchanneltool.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * ws连接验证
 */
@Data
@Accessors(chain = true)
public class WebSocketVerify {
    String token;   // 密钥
    Long t; // 创建时间(除以7)
}
