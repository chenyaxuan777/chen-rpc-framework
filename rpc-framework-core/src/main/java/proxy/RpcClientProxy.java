package proxy;

import entity.RpcServiceProperties;
import enums.RpcErrorMessageEnum;
import enums.RpcResponseCodeEnum;
import exception.RpcException;
import lombok.extern.slf4j.Slf4j;
import remoting.dto.RpcRequest;
import remoting.dto.RpcResponse;
import remoting.transport.RpcRequestTransport;
import remoting.transport.netty.client.NettyRpcClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author cyx
 * @create 2021-03-31 21:21
 */
@Slf4j
public class RpcClientProxy implements InvocationHandler {

    private static final String INTERFACE_NAME = "interfaceName";

    /**
     * 用于向服务器发送请求。有两种实现:socket和netty
     */
    private final RpcRequestTransport rpcRequestTransport;
    private final RpcServiceProperties rpcServiceProperties;

    public RpcClientProxy(RpcRequestTransport rpcRequestTransport, RpcServiceProperties rpcServiceProperties) {
        this.rpcRequestTransport = rpcRequestTransport;
        if (rpcServiceProperties.getGroup() == null) {
            rpcServiceProperties.setGroup("");
        }
        if (rpcServiceProperties.getVersion() == null) {
            rpcServiceProperties.setVersion("");
        }
        this.rpcServiceProperties = rpcServiceProperties;
    }

    public RpcClientProxy(RpcRequestTransport rpcRequestTransport) {
        this.rpcRequestTransport = rpcRequestTransport;
        this.rpcServiceProperties = RpcServiceProperties.builder().group("").version("").build();
    }

    /**
     * 获取代理对象
     * @param clazz 服务接口.class
     * @param <T> 接口
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
    }

    /**
     *
     * @param proxy 通过getProxy()方法获得的对象
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log.info("invoked method: [{}]", method.getName());
        RpcRequest rpcRequest = RpcRequest.builder()
                .methodName(method.getName()) // 方法名
                .parameters(args)             // 参数列表
                .interfaceName(method.getDeclaringClass().getName())              // 方法所属的接口名
                .requestId(UUID.randomUUID().toString())
                .group(rpcServiceProperties.getGroup())
                .version(rpcServiceProperties.getVersion())
                .build();

        RpcResponse<Object> rpcResponse = null;
        if (rpcRequestTransport instanceof NettyRpcClient) {
            CompletableFuture<RpcResponse<Object>> completableFuture = (CompletableFuture<RpcResponse<Object>>) rpcRequestTransport.sendRpcRequest(rpcRequest);
            rpcResponse = completableFuture.get();
        }
//        if (rpcRequestTransport instanceof SocketRpcClient) {
//            rpcResponse = (RpcResponse<Object>) rpcRequestTransport.sendRpcRequest(rpcRequest);
//        }

        this.check(rpcResponse, rpcRequest);
        return rpcResponse.getData();
    }

    private void check(RpcResponse<Object> rpcResponse, RpcRequest rpcRequest) {
        if (rpcResponse == null) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }

        if (!rpcRequest.getRequestId().equals(rpcResponse.getRequestId())) {
            throw new RpcException(RpcErrorMessageEnum.REQUEST_NOT_MATCH_RESPONSE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }

        if (rpcResponse.getCode() == null || !rpcResponse.getCode().equals(RpcResponseCodeEnum.SUCCESS.getCode())) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
    }
}
