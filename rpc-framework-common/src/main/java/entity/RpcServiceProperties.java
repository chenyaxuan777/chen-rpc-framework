package entity;

import lombok.*;

/**
 * @author Chen
 * @create 2021-03-25 23:56
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class RpcServiceProperties {
    /**
     * 服务版本
     */
    private String version;
    /**
     * 当接口有多个实现类时，按组进行区分
     */
    private String group;
    private String serviceName;

    public String toRpcServiceName() {
        return this.getServiceName() + this.getGroup() + this.getVersion();
    }
}
