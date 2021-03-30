package remoting.transport.netty.client.codec;

import compress.Compress;
import enums.CompressTypeEnum;
import enums.SerializationTypeEnum;
import extension.ExtensionLoader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;
import remoting.constants.RpcConstants;
import remoting.dto.RpcMessage;
import remoting.dto.RpcRequest;
import remoting.dto.RpcResponse;
import serialize.Serializer;

import java.util.Arrays;

/**
 * <pre>
 *   0     1     2     3     4        5     6     7     8         9          10      11     12  13  14   15 16
 *   +-----+-----+-----+-----+--------+----+----+----+------+-----------+-------+----- --+-----+-----+-------+
 *   |   magic   code        |version | full length         | messageType| codec|compress|    RequestId       |
 *   +-----------------------+--------+---------------------+-----------+-----------+-----------+------------+
 *   |                                                                                                       |
 *   |                                         body                                                          |
 *   |                                                                                                       |
 *   |                                        ... ...                                                        |
 *   +-------------------------------------------------------------------------------------------------------+
 * 4B  magic code（魔法数）   1B version（版本）   4B full length（消息长度）    1B messageType（消息类型）
 * 1B compress（压缩类型） 1B codec（序列化类型）    4B  requestId（请求的Id）
 * body（object类型数据）
 * </pre>
 *
 * 自定义解码器。负责处理 入站 消息，将ByteBuf消息格式的对象转换为我们需要的业务对象。
 * @author cyx
 * @create 2021-03-30 19:57
 */
@Slf4j
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {

    public RpcMessageDecoder() {
        // lengthFieldOffset:  魔数和版本号占开头的5个字节，长度域从5开始，所以值为5.
        // lengthFieldLength:  长度域占4个字节，所以值为4.
        // lengthAdjustment:   (数据包总长度 - 9) = (offset + length)，从左到这里已经读了9个字节了，所以值为-9
        // initialBytesToStrip: 我们需要header中的魔数和版本号做检查，所以从头开始一个都不跳过，所以值为0.
        this(RpcConstants.MAX_FRAME_LENGTH, 5, 4, -9, 0);
    }

    /**
     *
     * @param maxFrameLength 发送数据帧的最大长度，超出包的最大长度netty将会做一些特殊处理。（被丢弃）
     * @param lengthFieldOffset 定义数据长度域的偏移量。表示数据总长度的值的开始位置。
     * @param lengthFieldLength 定义数据长度域的长度。长度域offset+length表示这个长度域。
     * @param lengthAdjustment 调整长度，表示从数据长度域的末尾再读 [数据总长度+adj]个字节， 到达数据的末尾。
     * @param initialBytesToStrip 接收到的发送数据包，去除前initialBytesToStrip位。
     *                            如果你想接收全部数据，那么将此值设为0。如果只想要数据部分，那么需要跳过header部分的字节数。
     */
    public RpcMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        Object decoded = super.decode(ctx, in);
        if (decoded instanceof ByteBuf) {
            ByteBuf frame = (ByteBuf) decoded;
            if (frame.readableBytes() >= RpcConstants.TOTAL_LENGTH) {
                try {
                    return decodeFrame(frame);
                } catch (Exception e) {
                    log.error("Decode frame error!", e);
                    throw e;
                } finally {
                    frame.release();
                }
            }

        }
        return decoded;
    }

    private Object decodeFrame(ByteBuf in) {
        //注意：必须按照顺序读！
        checkMagicNumber(in);
        checkVersion(in);
        int fullLength = in.readInt();
        // 构建 RpcMessage对象
        byte messageType = in.readByte();
        byte codecType = in.readByte();
        byte compressType = in.readByte();
        int requestId = in.readInt();
        RpcMessage rpcMessage = RpcMessage.builder()
                .codec(codecType)
                .requestId(requestId)
                .messageType(messageType).build();
        // 检查是否是心跳类型的数据
        if (messageType == RpcConstants.HEARTBEAT_REQUEST_TYPE) {
            rpcMessage.setData(RpcConstants.PING);
            return rpcMessage;
        }
        if (messageType == RpcConstants.HEARTBEAT_RESPONSE_TYPE) {
            rpcMessage.setData(RpcConstants.PONG);
            return rpcMessage;
        }

        int bodyLength = fullLength - RpcConstants.HEAD_LENGTH;
        if (bodyLength > 0) {
            byte[] bs = new byte[bodyLength];
            in.readBytes(bs);
            // 解压缩
            String compressName = CompressTypeEnum.getName(compressType);
            Compress compress = ExtensionLoader.getExtensionLoader(Compress.class)
                    .getExtension(compressName);
            bs = compress.decompress(bs);
            // 反序列化
            String codecName = SerializationTypeEnum.getName(rpcMessage.getCodec());
            log.info("codec name: [{}] ", codecName);
            Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class)
                    .getExtension(codecName);
            //
            if (messageType == RpcConstants.REQUEST_TYPE) {
                RpcRequest tmpValue = serializer.deserialize(bs, RpcRequest.class);
                rpcMessage.setData(tmpValue);
            } else {
                RpcResponse tmpValue = serializer.deserialize(bs, RpcResponse.class);
                rpcMessage.setData(tmpValue);
            }
        }
        return rpcMessage;
    }

    // 检查header中的协议版本是否正确
    private void checkVersion(ByteBuf in) {
        byte version = in.readByte();
        if (version != RpcConstants.VERSION) {
            throw new RuntimeException("version isn't compatible" + version);
        }
    }
    // 检查魔数是否正确，匹配不上表示传输的并非是我们自定义的包。
    private void checkMagicNumber(ByteBuf in) {
        int len = RpcConstants.MAGIC_NUMBER.length;
        byte[] tmp = new byte[len];
        in.readBytes(tmp);
        for (int i = 0; i < len; i++) {
            if (tmp[i] != RpcConstants.MAGIC_NUMBER[i]) {
                throw new IllegalArgumentException("Unknown magic code: " + Arrays.toString(tmp));
            }
        }
    }
}
