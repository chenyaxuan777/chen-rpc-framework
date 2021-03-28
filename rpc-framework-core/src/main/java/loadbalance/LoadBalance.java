package loadbalance;

import java.util.List;

/**
 * 负载均衡策略（从多个服务地址中选择一个）
 * @author Chen
 * @create 2021-03-27 21:14
 */
public interface LoadBalance {
    /**
     * 从某服务的多个地址列表中，根据负载均衡策略选择出一个
     * @param serviceAddresses 服务地址列表
     * @param rpcServiceName 服务名
     * @return 目标服务地址
     */
    String selectServiceAddress(List<String> serviceAddresses, String rpcServiceName);
}
