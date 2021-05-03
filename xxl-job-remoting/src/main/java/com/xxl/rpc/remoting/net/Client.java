package com.xxl.rpc.remoting.net;

import com.xxl.rpc.remoting.XxlRpcFuture;
import com.xxl.rpc.remoting.XxlRpcInvokeCallback;
import com.xxl.rpc.remoting.exception.XxlRpcException;
import com.xxl.rpc.remoting.net.model.XxlRpcRequest;
import com.xxl.rpc.remoting.net.model.XxlRpcResponse;
import com.xxl.rpc.remoting.net.netty.client.NettyHttpClient;
import com.xxl.rpc.remoting.ref.XxlRpcReferenceBean;
import com.xxl.rpc.remoting.serialize.Serializer;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.*;

/**
 * i client
 *
 * @author xuxueli 2015-11-24 22:18:10
 */
public abstract class Client {
    protected static final Logger logger = LoggerFactory.getLogger(Client.class);


    protected NettyHttpClient nettyClientInstance;
    protected Serializer serializerInstance;

    public Client( Class<? extends NettyHttpClient> connectClientImpl) {
        try {
            nettyClientInstance = connectClientImpl.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------------- init ----------------------

    protected volatile XxlRpcReferenceBean xxlRpcReferenceBean;

    public void setXxlRpcReferenceBean(XxlRpcReferenceBean xxlRpcReferenceBean) {
        this.xxlRpcReferenceBean = xxlRpcReferenceBean;
    }

    public void setXxlSerializerInstance(Serializer serializerInstance) {
        this.serializerInstance = serializerInstance;
    }

    // ---------------------- future-response pool ----------------------

    private ThreadPoolExecutor responseCallbackThreadPool = null;
    private ConcurrentMap<String/*requestId*/, XxlRpcFuture> futurePool = new ConcurrentHashMap<String, XxlRpcFuture>();

    public void setInvokerFuture(String requestId, XxlRpcFuture futureResponse) {
        futurePool.put(requestId, futureResponse);
    }

    public void removeInvokerFuture(String requestId) {
        futurePool.remove(requestId);
    }

    public void notifyInvokerFuture(String requestId, final XxlRpcResponse xxlRpcResponse) {

        // get
        final XxlRpcFuture futureResponse = futurePool.get(requestId);
        if (futureResponse == null) {
            return;
        }

        // setResponse
        if (futureResponse.getInvokeCallback() == null) {
            // other nomal type
            futureResponse.setResponse(xxlRpcResponse);
        } else {
            // invoke callback
            try {
                executeResponseCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (xxlRpcResponse.getErrorMsg() != null) {
                            futureResponse.getInvokeCallback().onFailure(new XxlRpcException(xxlRpcResponse.getErrorMsg()));
                        } else {
                            futureResponse.getInvokeCallback().onSuccess(xxlRpcResponse.getResult());
                        }
                    }
                });
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        // do remove
        removeInvokerFuture(requestId);

    }

    public void executeResponseCallback(Runnable runnable) {

        if (responseCallbackThreadPool == null) {
            synchronized (this) {
                if (responseCallbackThreadPool == null) {
                    responseCallbackThreadPool = new ThreadPoolExecutor(
                            10,
                            100,
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<Runnable>(1000),
                            new ThreadFactory() {
                                @Override
                                public Thread newThread(Runnable r) {
                                    return new Thread(r, "xxl-rpc, XxlRpcInvokerFactory-responseCallbackThreadPool-" + r.hashCode());
                                }
                            },
                            new RejectedExecutionHandler() {
                                @Override
                                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                                    throw new XxlRpcException("xxl-rpc Invoke Callback Thread pool is EXHAUSTED!");
                                }
                            });        // default maxThreads 300, minThreads 60
                }
            }
        }
        responseCallbackThreadPool.execute(runnable);
    }

    public void stopCallbackThreadPool() {
        if (responseCallbackThreadPool != null) {
            responseCallbackThreadPool.shutdown();
        }
    }


    // ---------------------- send ----------------------

    /**
     * async send, bind requestId and future-response
     *
     * @param address
     * @param xxlRpcRequest
     * @return
     * @throws Exception
     */
    public abstract XxlRpcResponse syncInvoke(String address, XxlRpcRequest xxlRpcRequest, final long timeoutMillis) throws Exception;

    public abstract void asyncInvoke(String address, XxlRpcRequest xxlRpcRequest, XxlRpcInvokeCallback invokeCallback) throws Exception;

//	public abstract void asyncCallback(String address, XxlRpcRequest xxlRpcRequest) throws Exception;

    public abstract void onewayInvoke(String address, XxlRpcRequest xxlRpcRequest) throws Exception;

}
