package annotation;

import java.lang.annotation.*;

/**
 * RPC 引用注解，自动注入服务实现类
 * @author cyx
 * @create 2021-04-02 15:14
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD}) //字段、枚举的常量
@Inherited
public @interface RpcReference {

    /**
     * 服务版本，默认值为空string
     */
    String version() default "";

    /**
     * 服务群组，默认为空string
     */
    String group() default "";

}
