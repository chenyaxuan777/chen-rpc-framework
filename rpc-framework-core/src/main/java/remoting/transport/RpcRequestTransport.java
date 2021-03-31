package remoting.transport;

import extension.SPI;
import remoting.dto.RpcRequest;

/**
 * 发送rpc请求的顶层接口（可由netty方式或Socket方式实现）
 * @author Chen
 * @create 2021-03-26 0:44
 */
@SPI
public interface RpcRequestTransport {
    /**
     * 发送rpc请求到服务端，并且获取结果
     * @param rpcRequest 消息体
     * @return 服务端返回的数据
     */
    Object sendRpcRequest(RpcRequest rpcRequest);
}
