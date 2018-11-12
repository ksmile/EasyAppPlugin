package org.apache.cordova.easyapp;

import android.widget.Toast;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/**
 * This class echoes a string called from JavaScript.
 */
public class EasyAppPlugin extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("ToastTest")) {
            String message = args.getString(0);
            this.coolMethod(message, callbackContext);
            Toast.makeText(cordova.getActivity(), "自定义插件调用成功！", Toast.LENGTH_SHORT).show();
            return true;
        } else if (action.equals("VpnLogin")) {
            try {
                final String vpnServe = args.getString(0);
                final String un = args.getString(1);
                final String pwd = args.getString(2);
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        lognToVpn(vpnServe, un, pwd, callbackContext);
                    }
                });
//                cordova.getThreadPool().execute(new Runnable() {
//                    @Override
//                    public void run() {
//                        lognToVpn(un, pwd);
//                    }
//                });

                //下面三句为cordova插件回调页面的逻辑代码
                PluginResult mPlugin = new PluginResult(PluginResult.Status.NO_RESULT);
                mPlugin.setKeepCallback(true);

                callbackContext.sendPluginResult(mPlugin);
                return true;

            } catch (Exception e) {
                Toast.makeText(cordova.getActivity().getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
                return false;
            }
        } else if (action.equals("VpnLogout")) {
            try {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // lognToVpn(vpnServe, un, pwd);
                    }
                });

                //下面三句为cordova插件回调页面的逻辑代码
                PluginResult mPlugin = new PluginResult(PluginResult.Status.NO_RESULT);
                mPlugin.setKeepCallback(true);

                callbackContext.sendPluginResult(mPlugin);
                return true;

            } catch (Exception e) {
                Toast.makeText(cordova.getActivity().getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
                return false;
            }
        }
        return super.execute(action, args, callbackContext);
    }

    private void coolMethod(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    /*登陆到VPN*/
    private void lognToVpn(String vpnServe, String un, String pwd, CallbackContext callbackContext) {
        try {
            VpnUtils vpnUtils = VpnUtils.getInstance();
            vpnUtils.init(cordova.getActivity(), vpnServe, un, pwd);
            vpnUtils.doLogin(new VpnUtils.VpnLoginResultListener() {
                @Override
                public void onVpnLoginResult(boolean result, String errInfo) {
                    JSONObject paramEntity = new JSONObject();
                    try {
                        paramEntity.put("errorInfo", errInfo);
                        paramEntity.put("success", result);
                        // Toast.makeText(cordova.getActivity().getApplicationContext(), paramEntity.toString(), Toast.LENGTH_LONG).show();
                        callbackContext.success(paramEntity);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
