# 简介

本项目主要用于学习，从零实现一个RPC框架。

# RPC原理简介

RPC(Remote Procedure Call)即远程过程调用。

当我们需要调用的服务在另外的主机上时，因为两台服务器上的服务提供方法不在同一内存空间，所以需要通过网络编程传递方法调用所需参数，同时接收方法调用的结果。通过RPC可以帮助我们调用远程计算机上的某个服务的方法，这个过程就像调用本地方法一样简单。并且我们不需要了解底层网络编程的具体细节。

RPC核心功能如下组成：

* `客户端`：调用远程方法的一端。
* `客户端stub`：客户端代理类。接收到调用后将方法、参数等组成能够进行网络传输的消息体（序列化）。
* `网络传输`：将需要调用的方法信息比如参数等传输到服务端；服务端处理完成之后再把返回结构通过网络传输回来。可以通过Netty实现。
* `服务端stub`：接收到客户端执行方法的请求后，去指定对应的方法然后返回结果给客户端的类。
* `服务端`：提供远程方法的一端。

# 注册中心

注册中心功能位于`registry`包下

注册中心最基础的功能是：它记录了服务和服务地址的映射关系，相当于**目录功能**。

定义了两个接口`ServiceDiscovery`和`ServiceRegistry`，分别定义了**服务发现**和**服务注册**行为。

```java
public interface ServiceDiscovery {
    /**
     * 根据服务名 获取远程服务地址
     * @param rpcServiceName 完整的服务名称（class name+group+version）
     * @return 远程服务地址
     */
    InetSocketAddress lookupService(String rpcServiceName);
}
```

```java
public interface ServiceRegistry {
    /**
     * 注册服务到注册中心
     * @param rpcServiceName    完整的服务名称（class name+group+version）
     * @param inetSocketAddress 远程服务地址
     */
    void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress);
}
```

接下来，使用**ZooKeeper**作为注册中心的实现方式，并实现了这两个接口。

当我们需要将服务注册到注册中心时，将完整的服务名称（class name + group + version）作为根节点，服务地址（ip + port）作为子节点。因为同一服务可能会部署在多个服务器上，所以一个根节点可能会有多个子节点。

```java
/**
 * 服务注册（基于zookeeper实现）
 * @author Chen
 * @create 2021-03-27 20:37
 */
@Slf4j
public class ZkserviceRegistry implements ServiceRegistry {
    /**
     * 根节点是完整的服务名称，子节点是对应的服务地址
     * （服务可能被部署在多台机器上，所以可能对应多个子节点）
     * @param rpcServiceName    完整的服务名称（class name+group+version）
     * @param inetSocketAddress 远程服务地址
     */
    @Override
    public void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress) {
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName + "/" + inetSocketAddress.toString();
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        CuratorUtils.createPersistentNode(zkClient, servicePath);
    }
}
```

如果我们需要获得某个服务对应的地址，可以通过服务名称来获取到其所有子结点，然后根据**负载均衡策略**取出一个即可。

```java
/**
 * 服务发现（基于zookeeper实现）
 * @author Chen
 * @create 2021-03-27 20:37
 */
@Slf4j
public class ZkServiceDiscovery implements ServiceDiscovery {

    private final LoadBalance loadBalance;

    public ZkServiceDiscovery() {
        this.loadBalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension("loadBalance");
    }

    @Override
    public InetSocketAddress lookupService(String rpcServiceName) {
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        List<String> serviceUrlList = CuratorUtils.getChildrenNodes(zkClient, rpcServiceName);
        if (serviceUrlList.size() == 0) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND, rpcServiceName);
        }
        // 负载均衡
        String targetServiceUrl = loadBalance.selectServiceAddress(serviceUrlList, rpcServiceName);
        log.info("Successfully found the service address:[{}]", targetServiceUrl);
        String[] socketAddressArray = targetServiceUrl.split(":");
        String host = socketAddressArray[0];
        int port = Integer.parseInt(socketAddressArray[1]);
        return new InetSocketAddress(host, port);
    }
}
```



# 网络传输

网络传输模块位于`remoting`包下

## 传输实体

`RpcMessage`，所有需在网络上传输的数据都被封装为**RpcMessage**

```java
public class RpcMessage {
    //rpc 消息类型
    private byte messageType;
    //序列化类型
    private byte codec;
    //压缩类型
    private byte compress;
    //请求id
    private int requestId;
    //请求数据
    private Object data;
}
```

`RpcRequest`，客户端调用远程服务时，会先由代理类将调用参数及一些额外信息封装为**RpcRequest**

```java
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
```

`RpcResponse`，调用结果同一封装为**RpcResponse**

```java
public class RpcResponse<T> implements Serializable {
    private static final long serialVersionUID = 5921418276597300311L;
    private String requestId;

    private Integer code;
    private String message;
    private T data;

    public static <T> RpcResponse<T> success(T data, String requestId) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(RpcResponseCodeEnum.SUCCESS.getCode());
        response.setMessage(RpcResponseCodeEnum.SUCCESS.getMessage());
        response.setRequestId(requestId);
        if (data != null)
            response.setData(data);

        return response;
    }

    public static <T> RpcResponse<T> fail(RpcResponseCodeEnum rpcResponseCodeEnum) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setCode(rpcResponseCodeEnum.getCode());
        response.setMessage(rpcResponseCodeEnum.getMessage());
        return response;
    }
}
```

**RpcRequesr** 和 **RpcResponse** 都作为 **RpcMessage**的数据部分。



## 网络传输

网络传输部分位于`transport`包下，基于**Netty**实现。

`NettyRpcClient`，客户端相关。客户端主要用于发送网络请求到服务端（目标方法所在的服务器）。当我们知道了服务端的地址后，可以通过**NettyRpcClient**发送rpc请求（**RpcRequest**）到服务端。

主要提供如下：

* `doConnect()`：用于连接服务端（目标所在的服务器）。
* `sendRpcRequest()`：用于传输rpc请求（**RpcRequest**）到服务端。
* `NettyClientHandler`：自定义Handler，继承于Netty中的`ChannelInbountHandlerAdapter`，处理服务器发送的数据（自定义一些处理逻辑）。

```java
@Override
public Object sendRpcRequest(RpcRequest rpcRequest) {
    // 创建返回值
    CompletableFuture<RpcResponse<Object>> resultFuture = new CompletableFuture<>();
    // 通过rpcRequest构造service name
    String rpcServiceName = rpcRequest.toRpcProperties().toRpcServiceName();
    // 获取服务地址
    InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcServiceName);
    // 获取与服务地址关联的channel
    Channel channel = getChannel(inetSocketAddress);
    if (channel.isActive()) {
        // 放置未处理的请求
        unprocessedRequests.put(rpcRequest.getRequestId(), resultFuture);
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setData(rpcRequest);
        rpcMessage.setCodec(SerializationTypeEnum.PROTOSTUFF.getCode());
        rpcMessage.setCompress(CompressTypeEnum.GZIP.getCode());
        rpcMessage.setMessageType(RpcConstants.REQUEST_TYPE);
        channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("client send message: [{}]", rpcMessage);
            } else {
                future.channel().close();
                resultFuture.completeExceptionally(future.cause());
                log.error("Send failed:", future.cause());
            }
        });
    } else {
        throw new IllegalStateException();
    }
    return resultFuture;
}
```

`NettyRpcServer`，服务端相关。服务端主要监听客户端的连接，并且处理接收到的rpc请求。当客户端发送的rpc请求（**RpcRequest**）来了之后，服务端进行处理，处理完成之后把结果封装为rpc响应（**RpcResponse**）传输回客户端。

* `NettyServerHandler`：自定义Handler，继承于Netty中的`ChannelInbountHandlerAdapter`，处理客户端发送的数据（自定义一些处理逻辑）。

```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    try {
        if (msg instanceof RpcMessage) {
            log.info("server receive msg: [{}] ", msg);
            // 构造返回的message消息
            byte messageType = ((RpcMessage) msg).getMessageType();
            RpcMessage rpcMessage = new RpcMessage();
            rpcMessage.setCodec(SerializationTypeEnum.PROTOSTUFF.getCode());
            rpcMessage.setCompress(CompressTypeEnum.GZIP.getCode());
            // 如果请求中是心跳包，则也返回心跳包，pong
            if (messageType == RpcConstants.HEARTBEAT_REQUEST_TYPE) {
                rpcMessage.setMessageType(RpcConstants.HEARTBEAT_RESPONSE_TYPE);
                rpcMessage.setData(RpcConstants.PONG);
            } else {
                RpcRequest rpcRequest = (RpcRequest) ((RpcMessage) msg).getData();
                // 执行目标方法并且获得目标方法的返回值：借助RpcRequestHandler来实现
                Object result = rpcRequestHandler.handle(rpcRequest);
                log.info(String.format("server get result: %s", result.toString()));
                rpcMessage.setMessageType(RpcConstants.RESPONSE_TYPE);
                // 以上就构造完成了返回message消息

                if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                    // 构建统一返回：RpcResponse,并将其封装到message的data中
                    RpcResponse<Object> rpcResponse = RpcResponse.success(result, rpcRequest.getRequestId());
                    rpcMessage.setData(rpcResponse);
                } else {
                    RpcResponse<Object> rpcResponse = RpcResponse.fail(RpcResponseCodeEnum.FAIL);
                    rpcMessage.setData(rpcResponse);
                    log.error("not writable now, message dropped");
                }
                ctx.writeAndFlush(rpcMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        }
    } finally {
        // 确保ButeBuf的释放，否则可能导致内存泄漏问题
        ReferenceCountUtil.release(msg);
    }
}
```



## 传输协议

为通信双方制定传输协议，定义需要传输哪些类型的数据，并且规定每一种类型的数据应该占多少字节。这样我们在接收到二进制数据之后，就可以正确的解析出我们需要的数据。

```java
*   0     1     2     3     4        5     6     7     8         9          10      11     12  13  14   15 16
*   +-----+-----+-----+-----+--------+----+----+----+------+-----------+-------+----- --+-----+-----+-------+
*   |   magic   code        |version | full length         | messageType| codec|compress|    RequestId       |
*   +-----------------------+--------+---------------------+-----------+-----------+-----------+------------+
*   |                                                                                                       |
*   |                                         body                                                          |
*   |                                                                                                       |
*   |                                        ... ...                                                        |
*   +-------------------------------------------------------------------------------------------------------+
* 4B  magic code（魔法数）   1B version（版本）   4B full length（消息长度）    1B messageType（消息类型）
* 1B compress（压缩类型） 1B codec（序列化类型）    4B  requestId（请求的Id）
* body（object类型数据）
```

`魔数：`为了筛选到达服务端的数据包。当数据包到达服务端后，会首先取出前四个字节进行对比，可以迅速识别此数据包是否遵循传输协议。如果不遵循协议，可以直接关闭连接以节省资源。

`版本号：`为了兼容传输协议的多个版本问题。

`消息体长度：`运行时计算出来的。

`消息类型：`标识此消息为普通的消息类型，还是心跳包类型。



# 动态代理

通过动态代理，代理本地接口，得到的代理是一个增强后的接口。

然后通过代理接口调用接口中的方法时，会做一系列额外的事情：

1.当通过本地接口调用：xx方法（参数）时，会帮我们拿到接口、方法、参数信息，并将它们封装为rpcRequest。

2.帮我们调用netty客户端，调用其中的方法发送rpcRequest到指定服务器。

```java
public class RpcClientProxy implements InvocationHandler {
    //目标类的类加载、代理需要实现的接口、代理对象自定义handler
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
    }
    
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log.info("invoked method: [{}]", method.getName());
        //
        RpcRequest rpcRequest = RpcRequest.builder()
                .methodName(method.getName()) // 方法名
                .parameters(args)             // 参数列表
                .interfaceName(method.getDeclaringClass().getName())              // 方法所属的接口名
                .requestId(UUID.randomUUID().toString())
                .group(rpcServiceProperties.getGroup())
                .version(rpcServiceProperties.getVersion())
                .build();

        RpcResponse<Object> rpcResponse = null;
        if (rpcRequestTransport instanceof NettyRpcClient) {
            CompletableFuture<RpcResponse<Object>> completableFuture = (CompletableFuture<RpcResponse<Object>>) rpcRequestTransport.sendRpcRequest(rpcRequest);
            rpcResponse = completableFuture.get();
        }

        this.check(rpcResponse, rpcRequest);
        return rpcResponse.getData();
    }
}
```

# 服务相关

## 服务提供

服务提供位于`provider`包下。

主要完成服务发布功能。

当发布服务时，主要两大步

1.先将<服务名,服务对象>放到缓存中：表示提供服务的列表。

2.再将<服务名，服务地址>注册到注册中心。

2是定位到目标主机端口，1是直接定位到主机端口上的服务对象。

```java
private final Map<String, Object> serviceMap;//serviceMap记录了<服务名，服务对象>
private final Set<String> registeredService;//记录了已发布的服务名

// 服务发布
@Override
public void publishService(Object service, RpcServiceProperties rpcServiceProperties) {
    try {
        // 获取本机host
        String host = InetAddress.getLocalHost().getHostAddress();
        // 获取服务对象相关的接口
        Class<?> serviceRelatedInterface = service.getClass().getInterfaces()[0];
        String serviceName = serviceRelatedInterface.getCanonicalName();
        rpcServiceProperties.setServiceName(serviceName);
        // 添加服务
        this.addService(service, serviceRelatedInterface, rpcServiceProperties);
        // 向服务中心(zk)注册服务
        serviceRegistry.registerService(rpcServiceProperties.toRpcServiceName(), new InetSocketAddress(host, NettyRpcServer.PORT));
    } catch (UnknownHostException e) {
        log.error("occur exception when getHostAddress", e);
    }
}
```

## 服务处理

服务提供位于`handler`包下。

它主要负责处理服务，需要传入一个**rpcRequest**。

1. 从rpcRequest中拿到**服务名**
2. 根据**服务名**从**ServiceProvider类**中拿到**服务对象**
3. 通过反射从**服务对象**中拿到所有方法，然后靠**rpcRequest**中的方法名和参数确定执行哪个**目标方法**
4. 通过反射的方式：**服务对象**调用这个**目标方法**
5. 返回目标方法调用后的返回值

```java
public class RpcRequestHandler {

    private final ServiceProvider serviceProvider;

    public RpcRequestHandler() {
        serviceProvider = SingletonFactory.getInstance(ServiceProviderImpl.class);
    }

    /**
     * 处理rpcRequest：调用请求中的相关方法，并且获取该方法的执行结果
     * @param rpcRequest 客户端请求
     * @return  响应结果
     */
    public Object handle(RpcRequest rpcRequest) {
        // 根据服务名拿到相应的服务对象
        Object service = serviceProvider.getService(rpcRequest.toRpcProperties());
		return invokeTargetMethod(rpcRequest, service);
    }

    /**
     * 获取目标方法的执行结果
     * @param rpcRequest 客户端请求
     * @param service    服务对象
     * @return           目标方法执行后的返回结果
     */
    private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) {
        Object result;
        try {
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            result = method.invoke(service, rpcRequest.getParameters());
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
            throw new RpcException(e.getMessage(), e);
        }
        return result;
    }
}
```

## 通过注解注册/消费服务

一、定义两个注解

* `@RpcService` 注册服务

* `@RpcReference` 消费服务

```java
@Target({ElementType.TYPE}) //接口、类、枚举
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

@Target({ElementType.FIELD}) //字段、枚举的常量
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
```

二、实现`BeanPostProcessor`接口，并重写`postProcessAfterInitialization()` 和 `postProcessBeforeInitialization()`在对服务类型的Bean初始化前后增加一些额外的逻辑

- 在`postProcessBeforeInitialization()`方法中：判断类上是否有`@RpcService`注解，如果有的话，就取出`group`和`version`的值；然后调用ServiceProvider的publishService()方法发布服务即可。
- 在`postProcessAfterInitialization()`方法中：遍历类的属性，是否有属性上含有`@RpcRefence`注解。如果有的话，就对它进行代理加强，然后将加强后的代理接口作为额外增加的属性给这个Bean。

```
服务端通过注解自动发布注册服务（标在接口上）
// eg:
@RpcService(group = "test1", version = "version1")
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(Hello hello) {
        return result;
    }
}

/**
客户端通过注解消费远程服务（标在属性上）
eg:
@RpcReference(version = "version1", group = "test1")
private HelloService helloService;
```

# 总体调用过程

客户端想要调用某个接口中的方法时。

一、

服务端，提供了接口的实现类，将<服务名（接口名+群组+服务版本），服务对象（实现类对象）>放入本机Map中，并将<服务名，服务地址（本机地址）>注册到注册中心中。

客户端将要调用的接口，声明为属性，标上注解@RpcReference。因为首先当前类的bean会被扫描进IOC容器，定义了`BeanPostProcessor`在bean初始化前后可以扫描注解，利用动态代理，对接口生成代理。

二、

直接使用 接口.方法 调用，实际上调用的是代理后的接口。

三、

代理中会帮我们拿到接口名、方法名、参数信息，并将它们封装为rpcRequest；代理会通过zk注册中心，根据服务名找到服务地址；代理根据服务地址，调用网络传输模块发送rpcRequest。

四、

netty网络传输，会先将rpcRequest封装为消息类rpcMessage在网络上传输。rpcMessage是自定义的传输类，除了数据部分外还规定了 消息类型：表示是一个正常的rpc请求还是心跳包、序列化类型、压缩类型、等。

五、

在用netty向网络发数据时，就是出站时，先会经由pipline的自定义编码器encoder来对要传输的rpcMessage进行编码，就是将rpcMessage中的信息按序写为一个byte[]数组，有魔数、版本、消息长度、消息类型、序列化类型、压缩类型、请求id（这几部分相当于一个首部）、最后是数据部分，数据部分是根据首部的信息对数据(obj)进行序列化，压缩后的数据(byte[])。

六、

服务端netty收数据时，就是入站时，会先经由一个decoder，用的是netty中继承于LengthFieldBasedFrameDecoder这个基于长度的解码器， RocketMQ底层也是用的它！主要是靠设置属性，长度、偏移量来解码二进制字节流。同样是按顺序读，首先检查魔数：是不是我们的rpc协议的消息；检查版本号；然后拿到总长度后将byte[]还原成rpcMessage类，里面主要是拿消息头中的序列化类型、压缩类型进行解压缩、反序列化还原为数据。

七、

netty的pipeline除了编码器和解码器外，中间的自定义handler是实际业务处理部分。客户端就是，读取rpcMessage,然后将其中的数据部分构建成rpcResponse，就是接口调用的返回值。

服务器，拿到rpcMessage后，拿出数据部分rpcRequest，

1. 从rpcRequest中拿到**服务名**
2. 根据**服务名**从**ServiceProvider类**中拿到**服务对象**
3. 通过反射从**服务对象**中拿到所有方法，然后靠**rpcRequest**中的方法名和参数确定执行哪个**目标方法**
4. 通过反射的方式：**服务对象**调用这个**目标方法**
5. 返回目标方法调用后的返回值

然后将返回值封装到rpcResponse中，再构建成rpcMessage，使用netty传输回客户端。
