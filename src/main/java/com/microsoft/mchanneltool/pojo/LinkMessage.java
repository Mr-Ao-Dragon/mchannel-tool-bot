package com.microsoft.mchanneltool.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LinkMessage{
    String userId;
    String userName;
    String message;
}
