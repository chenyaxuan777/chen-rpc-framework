package remoting.transport.netty.client.codec;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 自定义解码器。负责处理 入站 消息，将ByteBuf消息格式的对象转换为我们需要的业务对象。
 * @author cyx
 * @create 2021-03-30 19:57
 */
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {

    public RpcMessageEncoder() {

    }
}
