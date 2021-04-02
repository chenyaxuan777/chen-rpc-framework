package annotation;

import java.lang.annotation.*;

/**
 * RPC服务注解，标在服务实现类上
 * @author cyx
 * @create 2021-04-02 15:08
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE}) //接口、类、枚举
@Inherited
public @interface RpcService {

    /**
     * 服务版本，默认值为空string
     */
    String version() default "";

    /**
     * 服务群组，默认为空string
     */
    String group() default "";

}
