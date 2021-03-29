package remoting.dto;

import lombok.*;

/**
 * @author Chen
 * @create 2021-03-26 0:07
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class RpcMessage {

    //rpc message type
    //rpc 消息类型
    private byte messageType;
    //serialization type
    //序列化类型
    private byte codec;
    //compress type
    //压缩类型
    private byte compress;
    //request id
    //请求id
    private int requestId;
    //request data
    //请求数据
    private Object data;

}
