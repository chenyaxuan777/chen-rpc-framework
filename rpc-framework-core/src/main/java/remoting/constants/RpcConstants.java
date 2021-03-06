package remoting.constants;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Chen
 * @create 2021-03-28 10:31
 */
public class RpcConstants {

    /**
     * Magic number. Verify RpcMessage
     * 魔数，校验RpcMessage
     */
    public static final byte[] MAGIC_NUMBER = {(byte) 'g', (byte) 'r', (byte) 'p', (byte) 'c'};
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    //version information，版本信息
    public static final byte VERSION = 1;
    public static final byte TOTAL_LENGTH = 16;
    // 消息类型 请求、响应、ping、pong
    public static final byte REQUEST_TYPE = 1;
    public static final byte RESPONSE_TYPE = 2;
    // ping
    public static final byte HEARTBEAT_REQUEST_TYPE = 3;
    // pong
    public static final byte HEARTBEAT_RESPONSE_TYPE = 4;
    // Rpc传输协议中header长度，header包括魔数，版本号，数据域长度，消息类型，压缩格式，序列化格式，请求id
    public static final int HEAD_LENGTH = 16;
    public static final String PING = "ping";
    public static final String PONG = "pong";
    // 数据包最大长度
    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;
}
