package provider;

import entity.RpcServiceProperties;

/**
 * 存储和提供服务对象
 * @author Chen
 * @create 2021-03-30 23:51
 */
public interface ServiceProvider {

    /**
     *
     * @param service 服务对象（服务接口实现类的实例）
     * @param serviceClass 服务对象实现的接口.class(也即服务接口.class)
     * @param rpcServiceProperties 服务相关的属性
     */
    void addService(Object service, Class<?> serviceClass, RpcServiceProperties rpcServiceProperties);

    /**
     *
     * @param rpcServiceProperties 服务相关的属性
     * @return
     */
    Object getService(RpcServiceProperties rpcServiceProperties);

    /**
     * @param service 服务对象
     * @param rpcServiceProperties 服务相关的属性
     */
    void publishService(Object service, RpcServiceProperties rpcServiceProperties);

    /**
     * @param service 服务对象
     */
    void publishService(Object service);
}
