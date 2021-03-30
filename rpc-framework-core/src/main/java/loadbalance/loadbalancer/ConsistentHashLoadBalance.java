package loadbalance.loadbalancer;

import loadbalance.AbstractLoadBalance;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参考dubbo的一致性hash负载均衡算法：http://dubbo.apache.org/zh-cn/blog/dubbo-consistent-hash-implementation.html
 * @author Chen
 * @create 2021-03-30 21:11
 */
@Slf4j
public class ConsistentHashLoadBalance extends AbstractLoadBalance {

    // 服务名rpcServiceName，负载均衡选择器selectors
    private final ConcurrentHashMap<String, ConsistentHashSelector> selectors = new ConcurrentHashMap<>();

    @Override
    protected String doSelect(List<String> serviceAddresses, String rpcServiceName) {
        // 计算哈希值 key，通过这个字段来识别出调用实例是否有变化，有变化则重新创建 ConsistentHashSelector
        int identityHashCode = System.identityHashCode(serviceAddresses);

        ConsistentHashSelector selector = selectors.get(rpcServiceName);

        // check for updates
        // 没有存在 selector 或者 invokers 实例有变化，重新创建
        if (selector == null || selector.identityHashCode != identityHashCode) {
            selectors.put(rpcServiceName, new ConsistentHashSelector(serviceAddresses, 160, identityHashCode));
            selector = selectors.get(rpcServiceName);
        }

        return selector.select(rpcServiceName);
    }

    /**
     * 负载均衡选择器
     */
    static class ConsistentHashSelector {
        // 虚节点
        private final TreeMap<Long, String> virtualInvokers;
        // 哈希码
        private final int identityHashCode;
        // 服务地址，每个节点对应生成的虚节点个数，哈希码
        ConsistentHashSelector(List<String> invokers, int replicaNumber, int identityHashCode) {

            this.virtualInvokers = new TreeMap<>();
            this.identityHashCode = identityHashCode;

            // A: 生成A1、A2、A3虚节点, 对这些虚节点的hash()都会映射到A上
            for (String invoker : invokers) {
                for (int i = 0; i < replicaNumber / 4; i++) {
                    byte[] digest = md5(invoker + i);
                    for (int h = 0; h < 4; h++) {
                        long m = hash(digest, h);
                        virtualInvokers.put(m, invoker);
                    }
                }
            }

        }

        static byte[] md5(String key) {
            MessageDigest md = null;

            try {
                md = MessageDigest.getInstance("md5");
                byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
                md.update(bytes);
            } catch (NoSuchAlgorithmException e) {
                log.error("An encryption algorithm that does not exist is used: ", e);
                e.printStackTrace();
            }

            return md.digest();
        }

        static long hash(byte[] digest, int idx) {
            return ((long) (digest[3 + idx * 4] & 255) << 24 | (long) (digest[2 + idx * 4] & 255) << 16 | (long) (digest[1 + idx * 4] & 255) << 8 | (long) (digest[idx * 4] & 255)) & 4294967295L;
        }

        public String select(String rpcServiceName) {
            byte[] digest  = md5(rpcServiceName);
            return selectForKey(hash(digest, 0));
        }

        /**
         * 找出所有 >= hashCode中 最小的那一个
         * @param hashCode
         * @return
         */
        public String selectForKey(long hashCode) {
            // 获取一个子集。其所有对象的 key 的值大于等于 fromKey
            Map.Entry<Long, String> entry = virtualInvokers.tailMap(hashCode, true).firstEntry();

            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }

            return entry.getValue();
        }

    }
}
