package registry;

import java.net.InetSocketAddress;

/**
 * @author Chen
 * @create 2021-03-26 22:27
 */
public interface ServiceDiscovery {
    /**
     * 根据rpcService 获取远程服务地址
     * @param rpcServiceName 完整的服务名称（class name+group+version）
     * @return 远程服务地址
     */
    InetSocketAddress lookupService(String rpcServiceName);
}
