package remoting.dto;

import entity.RpcServiceProperties;
import lombok.*;

import java.io.Serializable;

/**
 * rpc请求实体，包含了要调用的目标方法和类的名称、参数等数据
 * @author Chen
 * @create 2021-03-26 0:08
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class RpcRequest implements Serializable {

    private static final long serialVersionUID = -7072350265910398307L;
    private String requestId;
    private String interfaceName;
    private String methodName;
    private Object[] parameters;
    private Class<?>[] paramTypes;
    // 服务版本，主要为后续不兼容升级提供可能
    private String version;
    // 主要应对一个接口多个实现类的情况
    private String group;

    // RpcServiceProperties中各属性组成完整的服务名
    public RpcServiceProperties toRpcProperties() {
        return RpcServiceProperties.builder().serviceName(this.getInterfaceName())
                .version(this.getVersion())
                .group(this.getGroup()).build();
    }
}
