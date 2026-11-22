package com.xiaoniu.finance.router;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.xiaoniu.finance.router.agent.AgentManager;
import com.xiaoniu.finance.router.core.XnAbstractTrack;
import com.xiaoniu.finance.router.core.XnRouteMeta;
import com.xiaoniu.finance.router.core.XnRouterException;
import com.xiaoniu.finance.router.core.XnRouterRequest;
import com.xiaoniu.finance.router.core.XnRouterResponse;
import com.xiaoniu.finance.router.core.XnRouterRule;
import com.xiaoniu.finance.router.result.XnResultCode;
import com.xiaoniu.finance.router.result.XnRouterResult;
import com.xiaoniu.finance.router.rule.RuleManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.xiaoniu.finance.router.result.XnResultCode.CODE_NOT_FOUND;

/**
 * XnRouter涓浆鍣? * @author wenzhonghu
 */
public class XnRouter {
    private static final String TAG = "XnRouter";

    private volatile static XnRouter sInstance = null;
    private ConcurrentHashMap<String, XnRouteMeta> mTracks = null;
    public volatile static boolean isDebug = false;

    private XnRouter() {
        mTracks = new ConcurrentHashMap<>();
    }

    /**
     * 鍗曚緥妯″紡,澶氫釜璋冪敤涓嬪彧鏈変竴涓崟渚?     *
     * @return
     */
    public static synchronized XnRouter getInstance() {
        if (sInstance == null) {
            synchronized (XnRouter.class) {
                if (sInstance == null) {
                    sInstance = new XnRouter();
                }
            }
        }
        return sInstance;
    }

    /**
     * 娉ㄥ唽妯″潡
     *
     * @param moduleName path璺緞
     * @param module path瀵瑰簲鐨勪笟鍔℃ā鍧?     */
    public void registerModule(String moduleName, XnRouteMeta module) {
        mTracks.put(moduleName, module);
    }

    /**
     * 璺敱璺宠浆
     *
     * @param context 涓婁笅鏂?     * @param builder 璇锋眰鏁版嵁
     * @return
     */
    public XnRouterResponse from(Context context, @NonNull XnRouterRequest.Builder builder) {
        return from(context, builder.build());
    }

    /**
     * 璺敱璺宠浆
     *
     * @param context 涓婁笅鏂?     * @param routerRequest 璇锋眰鏁版嵁
     * @return
     */
    private XnRouterResponse from(Context context, @NonNull XnRouterRequest routerRequest) {
        //long starttime = System.currentTimeMillis();
        /**
         * 1/鏌ヨ璺敱浠ュ強璺敱瑙勫垯鍖归厤浣撶郴鎵ц
         */
        XnAbstractTrack targetTrace = findAndRouterRule(context, routerRequest);
        if (targetTrace == null || targetTrace instanceof XnRouterException) {
            routerRequest.recycle();
            if (targetTrace == null) {
                String error = String.format("%s don't find XnAbstractTrack, pls use @Router inject it", routerRequest.getPath());
                logD(error);
                return XnRouterResponse.build(CODE_NOT_FOUND, error, null);
            }
            XnRouterException e = (XnRouterException) targetTrace;
            logD(String.format("code=%s,msg=%s", e.mCode, e.mMessage));
            return XnRouterResponse.build(e.mCode, e.mMessage, null);
        }

        /**
         * 2/缁勮鍙傛暟
         */
        Object attachment = routerRequest.getAndClearObject();
        Bundle data = (Bundle) routerRequest.getData().clone();
        logD("find Track start: " + System.currentTimeMillis());
        /**浣跨敤瀹屾瘯鍥炴敹鍒板璞℃睜涓?/
        logD("obtain=" + routerRequest.toString());
        routerRequest.recycle();
        logD("recycle=" + routerRequest.toString());
        logD("find Track end: " + System.currentTimeMillis());
        /**
         * 3/鍚屾璋冪敤鐩存帴杩斿洖缁撴灉
         */
        XnRouterResult result = attachment == null ? targetTrace.fire(context, data) : targetTrace.fire(context, data, attachment);
        if (result == null) {
            result = new XnRouterResult.Builder().code(XnResultCode.CODE_INVALID).msg("").object(null).build();
        }
        /**
         * 4/鎷︽埅缁撴灉
         */
        agentResult(result);
        /**
         * 5/杩斿洖璋冪敤鍝嶅簲缁撴灉
         */
        XnRouterResponse routerResponse = XnRouterResponse.build(result.getCode(), result.getMsg(), result.getObject());
        return routerResponse;
    }

    private static void logD(String msg) {
        if (isDebug) {
            Log.d(TAG, msg);
        }
    }

    private void agentResult(XnRouterResult result) {
        AgentManager.getInstance().agent(result);
    }

    /**
     * 鏌ヨ鐩爣action
     *
     * @param context 涓婁笅鏂?     * @param routerRequest 璇锋眰鏁版嵁
     * @return
     */
    private XnAbstractTrack findAndRouterRule(Context context, XnRouterRequest routerRequest) {
        /**鍖归厤鏌ヨXnRouteMeta*/
        XnRouteMeta targetRouteMeta = findRouteMeta(routerRequest);
        if(targetRouteMeta == null){
            return  null;
        }
        XnRouterRule rule = new XnRouterRule(targetRouteMeta, routerRequest);
        return RuleManager.getInstance().doRule(context,rule);

    }

    /**
     *  鏌ユ壘璺敱
     * @param routerRequest 璇锋眰鏁版嵁
     * @return
     */
    private XnRouteMeta findRouteMeta(XnRouterRequest routerRequest) {
        if (routerRequest.isMatch()) {
            return mTracks.get(routerRequest.getPath());
        } else {
            String key = findMatchKey(routerRequest);
            if (key == null) {
                return null;
            }
            return mTracks.get(key);
        }
    }

    /**
     * 鍖归厤path
     * @param routerRequest 璇锋眰
     * @return
     */
    private String findMatchKey(XnRouterRequest routerRequest) {
        for (Map.Entry<String, XnRouteMeta> entry : mTracks.entrySet()) {
            if (entry != null && routerRequest.getPath().startsWith(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }


    private PermissionDeniedListener mDeniedListener = new PermissionDeniedListener() {
        @Override
        public void onPermissionDenied(Context context) {
            /** Todo toast implement permision denied,production pls override it*/
            Toast.makeText(context, context.getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * 娣诲姞鏉冮檺涓嶈冻鎯呭喌涓嬬殑鍥炶皟
     *
     * @param listener 鐩戝惉鍣?     * @return
     */
    public XnRouter setPermissionDeniedListener(PermissionDeniedListener listener) {
        mDeniedListener = listener;
        return this;
    }

    /**
     * 鑾峰彇鏉冮檺涓嶈冻鎯呭喌涓嬬殑鍥炶皟
     *
     * @return
     */
    public PermissionDeniedListener getPermissionDeniedListener() {
        return mDeniedListener;
    }

    public interface PermissionDeniedListener {
        void onPermissionDenied(final Context context);
    }
}

