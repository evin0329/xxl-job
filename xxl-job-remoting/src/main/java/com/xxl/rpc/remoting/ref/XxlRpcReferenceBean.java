package com.xxl.rpc.remoting.ref;

import com.xxl.rpc.remoting.*;
import com.xxl.rpc.remoting.exception.XxlRpcException;
import com.xxl.rpc.remoting.net.Client;
import com.xxl.rpc.remoting.net.model.XxlRpcRequest;
import com.xxl.rpc.remoting.net.model.XxlRpcResponse;

import com.xxl.rpc.remoting.route.LoadBalance;
import com.xxl.rpc.remoting.serialize.Serializer;
import com.xxl.rpc.remoting.serialize.impl.HessianSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * rpc reference bean, use by api
 *
 * @author xuxueli 2015-10-29 20:18:32
 */
public class XxlRpcReferenceBean {
    private static final Logger logger = LoggerFactory.getLogger(XxlRpcReferenceBean.class);
    // [tips01: save 30ms/100invoke. why why why??? with this logger, it can save lots of time.]


    // ---------------------- config ----------------------

    private Class<? extends Client> client;
    private Class<? extends Serializer> serializer = HessianSerializer.class;
    private CallType callType = CallType.SYNC;
    private LoadBalance loadBalance = LoadBalance.ROUND;

    private Class<?> iface = null;
    private String version = null;

    private long timeout = 1000;

    private String address = null;
    private String accessToken = null;

    private XxlRpcInvokeCallback invokeCallback = null;



    // set
    public void setClient(Class<? extends Client> client) {
        this.client = client;
    }

    public void setSerializer(Class<? extends Serializer> serializer) {
        this.serializer = serializer;
    }

    public void setCallType(CallType callType) {
        this.callType = callType;
    }

    public void setLoadBalance(LoadBalance loadBalance) {
        this.loadBalance = loadBalance;
    }

    public void setIface(Class<?> iface) {
        this.iface = iface;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setInvokeCallback(XxlRpcInvokeCallback invokeCallback) {
        this.invokeCallback = invokeCallback;
    }



    // get
    public Serializer getSerializerInstance() {
        return serializerInstance;
    }

    public long getTimeout() {
        return timeout;
    }

    public Class<?> getIface() {
        return iface;
    }


    // ---------------------- initClient ----------------------

    private Client clientInstance = null;
    private Serializer serializerInstance = null;

    private void checkValid() throws Exception {

        // valid
        if (this.client == null) {
            throw new XxlRpcException("xxl-rpc reference client missing.");
        }
        if (this.serializer == null) {
            throw new XxlRpcException("xxl-rpc reference serializer missing.");
        }
        if (this.callType == null) {
            throw new XxlRpcException("xxl-rpc reference callType missing.");
        }
        if (this.loadBalance == null) {
            throw new XxlRpcException("xxl-rpc reference loadBalance missing.");
        }
        if (this.iface == null) {
            throw new XxlRpcException("xxl-rpc reference iface missing.");
        }
        if (this.timeout < 0) {
            throw new XxlRpcException("xxl-rpc reference timeout missing.");
        }
    }


    // ---------------------- util ----------------------

    public Object getObject() throws Exception {

        // checkValid
        checkValid();

        // init serializerInstance
        this.serializerInstance = serializer.newInstance();

        // init Client
        clientInstance = client.newInstance();
        clientInstance.setXxlRpcReferenceBean(this);
        clientInstance.setXxlSerializerInstance(serializerInstance);


        // newProxyInstance
        return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{iface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                        // method param
                        String className = method.getDeclaringClass().getName();    // iface.getName()
                        String varsion_ = version;
                        String methodName = method.getName();
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        Object[] parameters = args;

                        // filter method like "Object.toString()"
                        if (className.equals(Object.class.getName())) {
                            logger.info(">>>>>>>>>>> xxl-rpc proxy class-method not support [{}#{}]", className, methodName);
                            throw new XxlRpcException("xxl-rpc proxy class-method not support");
                        }

                        // address
                        String finalAddress = address;

                        // 如果地址为空，则使用路由，否则为点对点
                        if (finalAddress == null || finalAddress.trim().length() == 0) {
                            finalAddress = route();
                        }

                        if (finalAddress == null || finalAddress.trim().length() == 0) {
                            throw new XxlRpcException("xxl-rpc reference bean[" + className + "] address empty");
                        }


                        // build request
                        XxlRpcRequest xxlRpcRequest = new XxlRpcRequest();
                        xxlRpcRequest.setRequestId(UUID.randomUUID().toString());
                        xxlRpcRequest.setCreateMillisTime(System.currentTimeMillis());
                        xxlRpcRequest.setAccessToken(accessToken);
                        xxlRpcRequest.setClassName(className);
                        xxlRpcRequest.setMethodName(methodName);
                        xxlRpcRequest.setParameterTypes(parameterTypes);
                        xxlRpcRequest.setParameters(parameters);
                        xxlRpcRequest.setVersion(version);

                        // send
                        if (CallType.SYNC == callType) {
                            return clientInstance.syncInvoke(address, xxlRpcRequest, timeout).getResult();
                        }
                        else if (CallType.FUTURE == callType) {
                            // future-response set
                            XxlRpcFuture futureResponse = new XxlRpcFuture(clientInstance, xxlRpcRequest, null);
                            try {
                                // invoke future set
                                XxlRpcInvokeFuture invokeFuture = new XxlRpcInvokeFuture(futureResponse);
                                XxlRpcInvokeFuture.setFuture(invokeFuture);

                                // do invoke
                                clientInstance.asyncInvoke(finalAddress, xxlRpcRequest, null);

                                return null;
                            } catch (Exception e) {
                                logger.info(">>>>>>>>>>> xxl-rpc, invoke error, address:{}, XxlRpcRequest{}", finalAddress, xxlRpcRequest);

                                // future-response remove
                                futureResponse.removeInvokerFuture();

                                throw (e instanceof XxlRpcException) ? e : new XxlRpcException(e);
                            }

                        }
                        else if (CallType.CALLBACK == callType) {

                            // get callback
                            XxlRpcInvokeCallback finalInvokeCallback = invokeCallback;
                            XxlRpcInvokeCallback threadInvokeCallback = XxlRpcInvokeCallback.getCallback();
                            if (threadInvokeCallback != null) {
                                finalInvokeCallback = threadInvokeCallback;
                            }
                            if (finalInvokeCallback == null) {
                                throw new XxlRpcException("xxl-rpc XxlRpcInvokeCallback（CallType=" + CallType.CALLBACK.name() + "） cannot be null.");
                            }

                            clientInstance.asyncInvoke(address, xxlRpcRequest, finalInvokeCallback);
                            return null;
                        }
                        else if (CallType.ONEWAY == callType) {
                            clientInstance.onewayInvoke(finalAddress, xxlRpcRequest);
                            return null;
                        }
                        else {
                            throw new XxlRpcException("xxl-rpc callType[" + callType + "] invalid");
                        }

                    }
                });
    }

    private String route() {
        return null;
    }


}
