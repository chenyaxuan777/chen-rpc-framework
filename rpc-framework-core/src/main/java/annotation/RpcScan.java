package annotation;

import org.springframework.context.annotation.Import;
import spring.CustomScannerRegistar;

import java.lang.annotation.*;

/**
 * @author cyx
 * @create 2021-04-02 15:16
 */
@Target({ElementType.TYPE, ElementType.METHOD})//接口、类、枚举 /方法
@Retention(RetentionPolicy.RUNTIME)
@Import(CustomScannerRegistar.class)
@Documented
public @interface RpcScan {

    String[] basePackage();

}
