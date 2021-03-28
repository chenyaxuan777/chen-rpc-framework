package registry;

import java.net.InetSocketAddress;

/**
 * 服务注册
 * @author Chen
 * @create 2021-03-26 22:27
 */
public interface ServiceRegistry {
    /**
     * 注册服务到注册中心
     * @param rpcServiceName    完整的服务名称（class name+group+version）
     * @param inetSocketAddress 远程服务地址
     */
    void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress);
}
