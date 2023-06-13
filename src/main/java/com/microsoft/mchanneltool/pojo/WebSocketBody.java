package com.microsoft.mchanneltool.pojo;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebSocketBody {
    String id;  // 消息id
    String type;    // 消息类型
    Object data;    // 消息数据
}
