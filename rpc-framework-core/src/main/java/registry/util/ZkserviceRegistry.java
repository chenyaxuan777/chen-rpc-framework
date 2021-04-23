package registry.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import registry.ServiceRegistry;

import java.net.InetSocketAddress;

/**
 * 服务注册（基于zookeeper实现）
 * @author Chen
 * @create 2021-03-27 20:37
 */
@Slf4j
public class ZkserviceRegistry implements ServiceRegistry {
    /**
     * 根节点是完整的服务名称，子节点是对应的服务地址
     * （服务可能被部署在多台机器上，所以可能对应多个子节点）
     * @param rpcServiceName    完整的服务名称（class name+group+version）
     * @param inetSocketAddress 远程服务地址
     */
    @Override
    public void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress) {
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName + "/" + inetSocketAddress.toString();
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        CuratorUtils.createPersistentNode(zkClient, servicePath);
    }
}
