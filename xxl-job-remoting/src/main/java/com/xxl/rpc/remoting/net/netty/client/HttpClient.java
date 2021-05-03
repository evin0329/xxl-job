package com.xxl.rpc.remoting.net.netty.client;

import com.xxl.rpc.remoting.XxlRpcFuture;
import com.xxl.rpc.remoting.XxlRpcInvokeCallback;
import com.xxl.rpc.remoting.exception.XxlRpcException;
import com.xxl.rpc.remoting.net.Client;
import com.xxl.rpc.remoting.net.model.XxlRpcRequest;
import com.xxl.rpc.remoting.net.model.XxlRpcResponse;
import com.xxl.rpc.remoting.ref.XxlRpcReferenceBean;
import com.xxl.rpc.remoting.serialize.impl.HessianSerializer;
import io.netty.channel.Channel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * netty_http client
 *
 * @author xuxueli 2015-11-24 22:25:15
 */
public class HttpClient extends Client {

    public HttpClient() {
        super(NettyHttpClient.class);
    }


//    @Override
//    public void asyncInvoke(String address, XxlRpcRequest xxlRpcRequest) throws Exception {
//
//        // client pool	[tips03 : may save 35ms/100invoke if move it to constructor, but it is necessary. cause by ConcurrentHashMap.get]
//        NettyHttpClient client = getPool(address, connectClientImpl, xxlRpcReferenceBean);
//
//        try {
//            // do invoke
//            client.send(xxlRpcRequest);
//        } catch (Exception e) {
//            throw e;
//        }
//    }

    @Override
    public XxlRpcResponse syncInvoke(String address, XxlRpcRequest xxlRpcRequest, final long timeoutMillis) throws Exception {
        // future-response set
        XxlRpcFuture futureResponse = new XxlRpcFuture(this, xxlRpcRequest, null);
        try {

            Channel channel = nettyClientInstance.getChannel(address, this::notifyInvokerFuture, serializerInstance);
            try {
                // do invoke
                nettyClientInstance.writeAndFlush(channel, xxlRpcRequest);
            } catch (Exception e) {
                throw e;
            }

            // future get
            XxlRpcResponse xxlRpcResponse = futureResponse.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (xxlRpcResponse == null) {
                if (!futureResponse.isDone()) {
                    throw new XxlRpcException("futureResponse = " + futureResponse.isDone());
                } else {
                    throw new XxlRpcException(futureResponse.toString());
                }
            }

            if (xxlRpcResponse.getErrorMsg() != null) {
                throw new XxlRpcException(xxlRpcResponse.getErrorMsg());
            }

            return xxlRpcResponse;
        } catch (Exception e) {
            logger.info(">>>>>>>>>>> xxl-rpc, invoke error, address:{}, XxlRpcRequest{}", address, xxlRpcRequest);

            throw (e instanceof XxlRpcException) ? e : new XxlRpcException(e);
        } finally {
            // future-response remove
            futureResponse.removeInvokerFuture();
        }
    }

    @Override
    public void asyncInvoke(String address, XxlRpcRequest xxlRpcRequest, XxlRpcInvokeCallback invokeCallback) throws Exception {

        // future-response set
        XxlRpcFuture futureResponse = new XxlRpcFuture(this, xxlRpcRequest, invokeCallback);
        try {
            Channel channel = nettyClientInstance.getChannel(address, this::notifyInvokerFuture, serializerInstance);
            // do invoke
            nettyClientInstance.writeAndFlush(channel, xxlRpcRequest);
        } catch (Exception e) {
            logger.info(">>>>>>>>>>> xxl-rpc, invoke error, address:{}, XxlRpcRequest{}", address, xxlRpcRequest);

            // future-response remove
            futureResponse.removeInvokerFuture();

            throw (e instanceof XxlRpcException) ? e : new XxlRpcException(e);
        }

    }

    @Override
    public void onewayInvoke(String address, XxlRpcRequest xxlRpcRequest) throws Exception {
        asyncInvoke(address, xxlRpcRequest, null);
    }


    public static void main(String[] args) throws Exception {


        XxlRpcRequest xxlRpcRequest = new XxlRpcRequest();
        xxlRpcRequest.setRequestId(UUID.randomUUID().toString());
        xxlRpcRequest.setCreateMillisTime(System.currentTimeMillis());
        xxlRpcRequest.setClassName("className");
        xxlRpcRequest.setMethodName("methodName");
        xxlRpcRequest.setParameterTypes(null);
        xxlRpcRequest.setParameters(null);


        HttpClient httpClient = new HttpClient();
        XxlRpcResponse xxlRpcResponse = httpClient.syncInvoke("127.0.0.1:1234", xxlRpcRequest, 10000);

        System.out.println(xxlRpcResponse);
    }
}