//package com.xxl.job.executor;
//
//import com.xxl.job.common.api.ExecutorBiz;
//import com.xxl.job.common.enums.ExecutorBlockStrategyEnum;
//import com.xxl.job.common.enums.GlueTypeEnum;
//import com.xxl.job.common.model.ReturnT;
//import com.xxl.job.common.model.TriggerParam;
//import com.xxl.rpc.remoting.CallType;
//import com.xxl.rpc.remoting.net.netty.client.HttpClient;
//import com.xxl.rpc.remoting.ref.XxlRpcReferenceBean;
//import com.xxl.rpc.remoting.route.LoadBalance;
//import com.xxl.rpc.remoting.serialize.impl.HessianSerializer;
//
//
///**
// * executor-api client, test
// *
// * Created by xuxueli on 17/5/12.
// */
//public class ExecutorBizTest {
//
//    public static void main(String[] args) throws Exception {
//
//        // param
//        String jobHandler = "demoJobHandler";
//        String params = "";
//
//        runTest(jobHandler, params);
//    }
//
//    /**
//     * run jobhandler
//     *
//     * @param jobHandler
//     * @param params
//     */
//    private static void runTest(String jobHandler, String params) throws Exception {
//        // trigger data
//        TriggerParam triggerParam = new TriggerParam();
//        triggerParam.setJobId(1);
//        triggerParam.setExecutorHandler(jobHandler);
//        triggerParam.setExecutorParams(params);
//        triggerParam.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.COVER_EARLY.name());
//        triggerParam.setGlueType(GlueTypeEnum.BEAN.name());
//        triggerParam.setGlueSource(null);
//        triggerParam.setGlueUpdatetime(System.currentTimeMillis());
//        triggerParam.setLogId(1);
//        triggerParam.setLogDateTime(System.currentTimeMillis());
//
//        // do remote trigger
//        String accessToken = null;
//
//        XxlRpcReferenceBean referenceBean = new XxlRpcReferenceBean();
//        referenceBean.setClient(HttpClient.class);
//        referenceBean.setSerializer(HessianSerializer.class);
//        referenceBean.setCallType(CallType.SYNC);
//        referenceBean.setLoadBalance(LoadBalance.ROUND);
//        referenceBean.setIface(ExecutorBiz.class);
//        referenceBean.setVersion(null);
//        referenceBean.setTimeout(3000);
//        referenceBean.setAddress("127.0.0.1:9999");
//        referenceBean.setAccessToken(accessToken);
//        referenceBean.setInvokeCallback(null);
//
//        ExecutorBiz executorBiz = (ExecutorBiz) referenceBean.getObject();
//
//        ReturnT<String> runResult = executorBiz.run(triggerParam);
//
//        System.out.println(runResult);
////        XxlRpcInvokerFactory.getInstance().stop();
//    }
//
//}
