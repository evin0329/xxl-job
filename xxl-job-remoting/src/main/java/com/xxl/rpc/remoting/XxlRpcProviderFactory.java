package com.xxl.rpc.remoting;

import com.xxl.rpc.remoting.net.Server;
import com.xxl.rpc.remoting.net.model.BaseCallback;
import com.xxl.rpc.remoting.exception.XxlRpcException;
import com.xxl.rpc.remoting.net.netty.server.NettyHttpServer;
import com.xxl.rpc.remoting.serialize.Serializer;
import com.xxl.rpc.remoting.serialize.impl.HessianSerializer;
import com.xxl.rpc.remoting.util.IpUtil;
import com.xxl.rpc.remoting.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * provider
 *
 * @author xuxueli 2015-10-31 22:54:27
 */
public class XxlRpcProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(XxlRpcProviderFactory.class);

    // ---------------------- config ----------------------

    private Class<? extends Server> server;
    private Class<? extends Serializer> serializer;

    private int corePoolSize = 60;
    private int maxPoolSize = 300;

    private BaseCallback startedCallback;
    private BaseCallback stopedCallback;

    private String ip = null;                    // for registry
    private int port = 7080;                    // default port
    private String accessToken = null;

    private Map<String, String> serviceRegistryParam = null;

    // set
    public void setServer(Class<? extends Server> server) {
        this.server = server;
    }

    public void setSerializer(Class<? extends Serializer> serializer) {
        this.serializer = serializer;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setStartedCallback(BaseCallback startedCallback) {
        this.startedCallback = startedCallback;
    }

    public void setStopedCallback(BaseCallback stopedCallback) {
        this.stopedCallback = stopedCallback;
    }

    public void setServiceRegistryParam(Map<String, String> serviceRegistryParam) {
        this.serviceRegistryParam = serviceRegistryParam;
    }

    // get
    public Serializer getSerializerInstance() {
        return serializerInstance;
    }

    public int getPort() {
        return port;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    // ---------------------- start / stop ----------------------

    private Server serverInstance;
    private Serializer serializerInstance;
    private String serviceAddress;

    public static void main(String[] args) throws Exception {
        XxlRpcProviderFactory xxlRpcProviderFactory = new XxlRpcProviderFactory();
        // 设置 http client
        xxlRpcProviderFactory.setServer(NettyHttpServer.class);
        xxlRpcProviderFactory.setSerializer(HessianSerializer.class);
        xxlRpcProviderFactory.setCorePoolSize(20);
        xxlRpcProviderFactory.setMaxPoolSize(200);
        xxlRpcProviderFactory.setIp("127.0.0.7");
        xxlRpcProviderFactory.setPort(1234);
        xxlRpcProviderFactory.setAccessToken(null);

        Server serverInstance = xxlRpcProviderFactory.getServerInstance();
        serverInstance.start();

        // add services
        serverInstance.addService("test", null, new XxlRpcProviderFactory());

        LockSupport.park();
    }

    public Server getServerInstance() throws Exception {
        checkValid();
        // init serializerInstance
        this.serializerInstance = serializer.newInstance();

        // start server port
        serviceAddress = IpUtil.getIpPort(this.ip, port);
        // new server instance
        serverInstance = server.newInstance();

        serverInstance.setStartedCallback(this.startedCallback);
        serverInstance.setStopedCallback(this.stopedCallback);
        serverInstance.setCorePoolSize(this.corePoolSize);
        serverInstance.setMaxPoolSize(this.maxPoolSize);
        serverInstance.setPort(this.port);
        serverInstance.setSerializerInstance(this.serializerInstance);
        serverInstance.setAccessToken(this.accessToken);

        return serverInstance;
    }


    private void start() throws Exception {

        // valid
//        checkValid();

        // init serializerInstance
        this.serializerInstance = serializer.newInstance();

        // start server
        serviceAddress = IpUtil.getIpPort(this.ip, port);
        serverInstance = server.newInstance();
//		serverInstance.setStartedCallback(new BaseCallback() {		// serviceRegistry started
//			@Override
//			public void run() throws Exception {
//				// start registry
//				if (serviceRegistry != null) {
//					serviceRegistryInstance = serviceRegistry.newInstance();
//					serviceRegistryInstance.start(serviceRegistryParam);
//					if (serviceData.size() > 0) {
//						serviceRegistryInstance.registry(serviceData.keySet(), serviceAddress);
//					}
//				}
//			}
//		});
//		serverInstance.setStopedCallback(new BaseCallback() {		// serviceRegistry stoped
//			@Override
//			public void run() {
//				// stop registry
//				if (serviceRegistryInstance != null) {
//					if (serviceData.size() > 0) {
//						serviceRegistryInstance.remove(serviceData.keySet(), serviceAddress);
//					}
//					serviceRegistryInstance.stop();
//					serviceRegistryInstance = null;
//				}
//			}
//		});
        serverInstance.setStartedCallback(this.startedCallback);
        serverInstance.setStopedCallback(this.stopedCallback);
        serverInstance.setCorePoolSize(this.corePoolSize);
        serverInstance.setMaxPoolSize(this.maxPoolSize);
        serverInstance.setPort(this.port);
        serverInstance.setSerializerInstance(this.serializerInstance);
        serverInstance.setAccessToken(this.accessToken);


    }

    private void checkValid() {
        if (this.server == null) {
            throw new XxlRpcException("xxl-rpc provider server missing.");
        }
        if (this.serializer == null) {
            throw new XxlRpcException("xxl-rpc provider serializer missing.");
        }
        if (!(this.corePoolSize > 0 && this.maxPoolSize > 0 && this.maxPoolSize >= this.corePoolSize)) {
            this.corePoolSize = 60;
            this.maxPoolSize = 300;
        }
        if (this.ip == null) {
            this.ip = IpUtil.getIp();
        }
        if (this.port <= 0) {
            this.port = 7080;
        }
        if (NetUtil.isPortUsed(this.port)) {
            throw new XxlRpcException("xxl-rpc provider port[" + this.port + "] is used.");
        }
    }

    public void stop() throws Exception {
        // stop server
        serverInstance.stop();
    }

    /**
     * make service key
     *
     * @param iface
     * @param version
     * @return
     */
    public static String makeServiceKey(String iface, String version) {
        String serviceKey = iface;
        if (version != null && version.trim().length() > 0) {
            serviceKey += "#".concat(version);
        }
        return serviceKey;
    }

}
