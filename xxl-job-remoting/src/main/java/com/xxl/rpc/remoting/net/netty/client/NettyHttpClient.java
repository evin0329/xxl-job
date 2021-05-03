package com.xxl.rpc.remoting.net.netty.client;

import com.xxl.rpc.remoting.net.Client;
import com.xxl.rpc.remoting.net.model.Beat;
import com.xxl.rpc.remoting.net.model.XxlRpcRequest;
import com.xxl.rpc.remoting.net.model.XxlRpcResponse;
import com.xxl.rpc.remoting.ref.XxlRpcReferenceBean;
import com.xxl.rpc.remoting.serialize.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * netty_http
 *
 * @author xuxueli 2015-11-24 22:25:15
 */
public class NettyHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyHttpClient.class);


    private EventLoopGroup group;
//    private Channel channel;

    private String address;
    private String host;
    private DefaultFullHttpRequest beatRequest;

    private static volatile ConcurrentMap<String/*address*/, Channel> channelMap;        // (static) alread addStopCallBack
    private static volatile ConcurrentMap<String/*address*/, Object/*lock*/> channelLockMap = new ConcurrentHashMap<>();

    private Serializer serializer;
    private BiConsumer<String, XxlRpcResponse> notifyInvokerCallback;

    public Serializer getSerializer() {
        return serializer;
    }

    public BiConsumer<String, XxlRpcResponse> getNotifyInvokerCallback() {
        return notifyInvokerCallback;
    }

    private Channel connect(String address) throws Exception {
        final NettyHttpClient thisClient = this;

        if (!address.toLowerCase().startsWith("http")) {
            address = "http://" + address;    // IP:PORT, need parse to url
        }

        this.address = address;
        URL url = new URL(address);
        this.host = url.getHost();
        int port = url.getPort() > -1 ? url.getPort() : 80;


        this.group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline()
                                .addLast(new IdleStateHandler(0, 0, Beat.BEAT_INTERVAL, TimeUnit.SECONDS))   // beat N, close if fail
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(5 * 1024 * 1024))
                                .addLast(new NettyHttpClientHandler(thisClient));
                    }
                })
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        Channel channel = bootstrap.connect(host, port).sync().channel();

        // valid
        if (!isValidate(channel)) {
            close(channel);
            return null;
        }

        logger.debug(">>>>>>>>>>> xxl-rpc netty client proxy, connect to server success at host:{}, port:{}", host, port);
        return channel;
    }

    public boolean isValidate(Channel channel) {
        if (channel != null) {
            return channel.isActive();
        }
        return false;
    }


    public void close(Channel channel) {
        if (channel != null && channel.isActive()) {
            channel.close();        // if this.channel.isOpen()
        }
//        if (this.group != null && !this.group.isShutdown()) {
//            this.group.shutdownGracefully();
//        }
        logger.debug(">>>>>>>>>>> xxl-rpc netty client close.");
    }

    private void check() {
        if (serializer == null) {

        }
        if (notifyInvokerCallback == null) {

        }
    }

    protected Channel getChannel(String address, BiConsumer<String, XxlRpcResponse> notifyInvokerCallback, Serializer serializer) throws Exception {
        this.notifyInvokerCallback = notifyInvokerCallback;
        this.serializer = serializer;

        // init base compont, avoid repeat init
        if (channelMap == null) {
            synchronized (NettyHttpClient.class) {
                if (channelMap == null) {
                    // init
                    channelMap = new ConcurrentHashMap<String, Channel>();
                }
            }
        }

        // get-valid client
        Channel channel = channelMap.get(address);
        if (isValidate(channel)) {
            return channel;
        }

        // lock
        Object channelLock = channelLockMap.get(address);
        if (channelLock == null) {
            channelLockMap.putIfAbsent(address, new Object());
            channelLock = channelLockMap.get(address);
        }

        // remove-create new client
        synchronized (channelLock) {

            // get-valid client, avlid repeat
            channel = channelMap.get(address);
            if (isValidate(channel)) {
                return channel;
            }

            // remove old
            if (channel != null) {
                close(channel);
                channelMap.remove(address);
            }

            Channel connectNew = null;
            // set pool
            try {
                connectNew = this.connect(address);
                channelMap.put(address, connectNew);

            } catch (Exception e) {
                close(connectNew);
                throw e;
            }

            return connectNew;
        }

    }

    public void writeAndFlush(Channel channel, XxlRpcRequest xxlRpcRequest) throws Exception {
        byte[] requestBytes = serializer.serialize(xxlRpcRequest);

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, new URI(address).getRawPath(), Unpooled.wrappedBuffer(requestBytes));
        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());

        channel.writeAndFlush(request).sync();
    }

}
