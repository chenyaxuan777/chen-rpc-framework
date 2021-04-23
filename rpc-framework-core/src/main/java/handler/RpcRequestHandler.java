package handler;

import exception.RpcException;
import factory.SingletonFactory;
import provider.ServiceProvider;
import provider.ServiceProviderImpl;
import remoting.dto.RpcRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * RpcRequest 处理器
 * @author cyx
 * @create 2021-04-01 17:39
 */
public class RpcRequestHandler {

    private final ServiceProvider serviceProvider;

    public RpcRequestHandler() {
        serviceProvider = SingletonFactory.getInstance(ServiceProviderImpl.class);
    }

    /**
     * 处理rpcRequest：调用请求中的相关方法，并且获取该方法的执行结果
     * @param rpcRequest 客户端请求
     * @return  响应结果
     */
    public Object handle(RpcRequest rpcRequest) {
        // 根据服务名拿到相应的服务对象
        Object service = serviceProvider.getService(rpcRequest.toRpcProperties());
        return invokeTargetMethod(rpcRequest, service);
    }

    /**
     * 获取目标方法的执行结果
     * @param rpcRequest 客户端请求
     * @param service    服务对象
     * @return           目标方法执行后的返回结果
     */
    private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) {
        Object result;
        try {
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            result = method.invoke(service, rpcRequest.getParameters());
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
            throw new RpcException(e.getMessage(), e);
        }
        return result;
    }

}
