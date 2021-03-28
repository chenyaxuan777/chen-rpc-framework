package enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Chen
 * @create 2021-03-27 18:40
 */
@AllArgsConstructor
@Getter
public enum RpcConfigEnum {

    RPC_CONFIG_PATH("rpc.properties"),
    ZK_ADDRESS("rpc.zookeeper.address");

    private final String propertyValue;

}
