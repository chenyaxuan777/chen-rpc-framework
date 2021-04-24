package spring;

import annotation.RpcReference;
import annotation.RpcService;
import entity.RpcServiceProperties;
import extension.ExtensionLoader;
import factory.SingletonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import provider.ServiceProvider;
import provider.ServiceProviderImpl;
import proxy.RpcClientProxy;
import remoting.transport.RpcRequestTransport;

import java.lang.reflect.Field;

/**
 * @author cyx
 * @create 2021-04-02 15:30
 */
@Slf4j
@Component
public class SpringBeanPostProcessor implements BeanPostProcessor {

    private final ServiceProvider serviceProvider;
    private final RpcRequestTransport rpcClient;

    public SpringBeanPostProcessor() {
        this.serviceProvider = SingletonFactory.getInstance(ServiceProviderImpl.class);
        this.rpcClient = ExtensionLoader.getExtensionLoader(RpcRequestTransport.class).getExtension("netty");
    }


    /**
         * 服务端通过注解自动发布注册服务（标在接口上）
         * eg:
         * @RpcService(group = "test1", version = "version1")
         * public class HelloServiceImpl implements HelloService {
         *     @Override
         *     public String hello(Hello hello) {
         *         return result;
         *     }
         * }
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 判断bean上是否有 @RpcService注解
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
            // 获取 @RpcService注解
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
            // 构造 RpcServiceProperties
            RpcServiceProperties rpcServiceProperties = RpcServiceProperties.builder()
                    .group(rpcService.group()).version(rpcService.version()).build();
            // 发布服务
            serviceProvider.publishService(bean, rpcServiceProperties);
        }
        return bean;
    }


    /**
     * 客户端通过注解消费远程服务（标在属性上）
     * eg:
     * @RpcReference(version = "version1", group = "test1")
     * private HelloService helloService;
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            RpcReference rpcReference = declaredField.getAnnotation(RpcReference.class);
            if (rpcReference != null) {
                // 根据接口名、注解值 生成接口的代理
                RpcServiceProperties rpcServiceProperties = RpcServiceProperties.builder()
                        .group(rpcReference.group()).version(rpcReference.version()).build();
                RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceProperties);
                Object clientProxy = rpcClientProxy.getProxy(declaredField.getType());
                // 给bean增加一个额外的 代理 属性
                try {
                    declaredField.set(bean, clientProxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return bean;
    }
}
