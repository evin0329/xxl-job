package com.xxl.rpc.remoting.net;

import com.xxl.rpc.remoting.XxlRpcProviderFactory;
import com.xxl.rpc.remoting.net.model.BaseCallback;
import com.xxl.rpc.remoting.net.model.XxlRpcRequest;
import com.xxl.rpc.remoting.net.model.XxlRpcResponse;
import com.xxl.rpc.remoting.serialize.Serializer;
import com.xxl.rpc.remoting.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * server
 *
 * @author xuxueli 2015-11-24 20:59:49
 */
public abstract class Server {
	protected static final Logger logger = LoggerFactory.getLogger(Server.class);

	private int corePoolSize;
	private int maxPoolSize;
	private int port;					// default port

	private Serializer serializerInstance;
	private Map<String, Object> serviceData = new HashMap<String, Object>();;
	private String accessToken = null;


	private BaseCallback startedCallback;
	private BaseCallback stopedCallback;

	public void setStartedCallback(BaseCallback startedCallback) {
		this.startedCallback = startedCallback;
	}

	public void setStopedCallback(BaseCallback stopedCallback) {
		this.stopedCallback = stopedCallback;
	}

	public int getCorePoolSize() {
		return corePoolSize;
	}

	public void setCorePoolSize(int corePoolSize) {
		this.corePoolSize = corePoolSize;
	}

	public int getMaxPoolSize() {
		return maxPoolSize;
	}

	public void setMaxPoolSize(int maxPoolSize) {
		this.maxPoolSize = maxPoolSize;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public Serializer getSerializerInstance() {
		return serializerInstance;
	}

	public void setSerializerInstance(Serializer serializerInstance) {
		this.serializerInstance = serializerInstance;
	}

	public Map<String, Object> getServiceData() {
		return serviceData;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	/**
	 * start server
	 *
//	 * @param xxlRpcProviderFactory
	 * @throws Exception
	 */
	public abstract void start() throws Exception;

	/**
	 * callback when started
	 */
	public void onStarted() {
		if (startedCallback != null) {
			try {
				startedCallback.run();
			} catch (Exception e) {
				logger.error(">>>>>>>>>>> xxl-rpc, server startedCallback error.", e);
			}
		}
	}

	/**
	 * stop server
	 *
	 * @throws Exception
	 */
	public abstract void stop() throws Exception;

	/**
	 * callback when stoped
	 */
	public void onStoped() {
		if (stopedCallback != null) {
			try {
				stopedCallback.run();
			} catch (Exception e) {
				logger.error(">>>>>>>>>>> xxl-rpc, server stopedCallback error.", e);
			}
		}
	}

	public XxlRpcResponse invokeService(XxlRpcRequest xxlRpcRequest) {

		//  make response
		XxlRpcResponse xxlRpcResponse = new XxlRpcResponse();
		xxlRpcResponse.setRequestId(xxlRpcRequest.getRequestId());

		// match service bean
		String serviceKey = XxlRpcProviderFactory.makeServiceKey(xxlRpcRequest.getClassName(), xxlRpcRequest.getVersion());
		Object serviceBean = serviceData.get(serviceKey);

		// valid
		if (serviceBean == null) {
			xxlRpcResponse.setErrorMsg("The serviceKey[" + serviceKey + "] not found.");
			return xxlRpcResponse;
		}

		if (System.currentTimeMillis() - xxlRpcRequest.getCreateMillisTime() > 3 * 60 * 1000) {
			xxlRpcResponse.setErrorMsg("The timestamp difference between admin and executor exceeds the limit.");
			return xxlRpcResponse;
		}
		if (accessToken != null && accessToken.trim().length() > 0 && !accessToken.trim().equals(xxlRpcRequest.getAccessToken())) {
			xxlRpcResponse.setErrorMsg("The access token[" + xxlRpcRequest.getAccessToken() + "] is wrong.");
			return xxlRpcResponse;
		}

		try {
			// invoke
			Class<?> serviceClass = serviceBean.getClass();
			String methodName = xxlRpcRequest.getMethodName();
			Class<?>[] parameterTypes = xxlRpcRequest.getParameterTypes();
			Object[] parameters = xxlRpcRequest.getParameters();

			Method method = serviceClass.getMethod(methodName, parameterTypes);
			method.setAccessible(true);
			Object result = method.invoke(serviceBean, parameters);

			/*FastClass serviceFastClass = FastClass.create(serviceClass);
			FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
			Object result = serviceFastMethod.invoke(serviceBean, parameters);*/

			xxlRpcResponse.setResult(result);
		} catch (Throwable t) {
			// catch error
			logger.error("xxl-rpc provider invokeService error.", t);
			xxlRpcResponse.setErrorMsg(ThrowableUtil.toString(t));
		}

		return xxlRpcResponse;
	}



	/**
	 * add service
	 *
	 * @param iface
	 * @param version
	 * @param serviceBean
	 */
	public void addService(String iface, String version, Object serviceBean){
		String serviceKey = XxlRpcProviderFactory.makeServiceKey(iface, version);
		serviceData.put(serviceKey, serviceBean);

		logger.info(">>>>>>>>>>> xxl-rpc, provider factory add service success. serviceKey = {}, serviceBean = {}", serviceKey, serviceBean.getClass());
	}

}
