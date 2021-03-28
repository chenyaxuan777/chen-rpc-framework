package loadbalance.loadbalancer;

import loadbalance.AbstractLoadBalance;

import java.util.List;

/**
 * @author Chen
 * @create 2021-03-27 21:11
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    @Override
    protected String doSelect(List<String> serviceAddresses, String rpcServiceName) {
        return null;
    }
}
