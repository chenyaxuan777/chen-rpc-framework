package factory;

import exception.RpcException;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取单例对象的工厂类
 * @author Chen
 * @create 2021-03-28 23:36
 */
public class SingletonFactory {
    private static final Map<String, Object> OBJECT_MAP = new HashMap<>();

    private SingletonFactory() {
    }

    public static <T> T getInstance(Class<T> c) {
        String key = c.toString();
        Object instance = OBJECT_MAP.get(key);
        if (instance != null) {
            return c.cast(instance);
        }
        synchronized (SingletonFactory.class) {
            instance = OBJECT_MAP.get(key);
            if (instance == null) {
                try {
                    instance = c.getDeclaredConstructor().newInstance();
                    OBJECT_MAP.put(key, instance);
                } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
        return c.cast(instance);
    }
}
