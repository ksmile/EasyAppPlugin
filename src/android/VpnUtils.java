package org.apache.cordova.easyapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.support.annotation.NonNull;

import com.sangfor.ssl.IVpnDelegate;
import com.sangfor.ssl.SFException;
import com.sangfor.ssl.SangforAuth;
import com.sangfor.ssl.common.VpnCommon;
import com.sangfor.ssl.service.setting.SystemConfiguration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mooning on 2017-03-16.
 */

public class VpnUtils implements IVpnDelegate{
    private static final String TAG = "EasyVPN:";
    private static VpnUtils instance;
    private Context context;
    private VpnLoginResultListener listener, listener2;
    private boolean logout = false;

    // 认证所需信息
    private static String VPN_IP = "183.224.79.51"; // VPN设备地址　（也可以使用域名访问）
    private static int VPN_PORT = 443; // vpn设备端口号，一般为443
    // 用户名密码认证；用户名和密码
    private static String USER_NAME = "15096600342@yngov.vpn.yn";
    private static String USER_PASSWD = "600342";
    // 证书认证；导入证书路径和证书密码（如果服务端没有设置证书认证此处可以不设置）
    private static String CERT_PATH = "";
    private static String CERT_PASSWD = "";
    // 测试内网服务器地址。（在vpn服务器上，配置的内网资源）
    private static String TEST_URL = "http://59.216.224.49";

    private static String SMS_CODE = "";
    private static String CHALLENGE_CODE = "";
    private InetAddress m_iAddr = null;

    private final int TEST_URL_TIMEOUT_MILLIS = 8 * 1000;// 测试vpn资源的超时时间
    private int AUTH_MODULE = SangforAuth.AUTH_MODULE_EASYAPP;

    public static VpnUtils getInstance(){
        if(instance==null) instance = new VpnUtils();
        return instance;
    }

    void init(Context context, String vpnServe, String username, String password){
        try {
            this.context = context;
            VPN_IP = vpnServe;
            USER_NAME = username;
            USER_PASSWD = password;
//            VPN_IP = context.getString(R.string.vpn_web_root);
//            VPN_PORT = Integer.parseInt(context.getString(R.string.vpn_web_port));
            logout = false;
        }catch (Exception ignored){}
    }

    void doLogin(VpnLoginResultListener lst){
        listener = lst;
        try {
            PermissionsUtil.requestPermission((Activity)context, new PermissionListener() {
                @Override
                public void permissionGranted(@NonNull String[] permission) {
                    doLogin();
                }

                @Override
                public void permissionDenied(@NonNull String[] permission) {

                }
            }, Manifest.permission.INTERNET, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH);
        }catch (Exception ignored){}
    }

    private void doLogin(){
        if(USER_NAME.isEmpty()||USER_PASSWD.isEmpty()){
            returnResult(false, "VPN 用户名和密码不能为空！");
        }else if(!initVpnModule()){
            returnResult(false, "VPN模块初始化失败！");
        }
        else {
            initSslVpn();
        }
    }

    boolean doLogout(VpnLoginResultListener lst){
        this.listener2 = lst;
        return SangforAuth.getInstance().vpnLogout();
    }

    boolean getVpnStatus(){
        return SangforAuth.getInstance().vpnQueryStatus()==VPN_STATUS_ONLINE;
    }

    private void returnResult(boolean result, String errInfo){
        if(listener!=null){
            listener.onVpnLoginResult(result, errInfo);
        }
        if(!result){
            // Intent intent = new Intent(ChatMsgEntity.LOGOUT_ACTION);
            // context.sendBroadcast(intent);//传递过去
        }
    }

    @Override
    public void vpnCallback(int vpnResult, int authType) {
        SangforAuth sfAuth = SangforAuth.getInstance();
        boolean result = false;
        String errInfo = "VPN登陆失败！";
        switch (vpnResult) {
            case IVpnDelegate.RESULT_VPN_INIT_FAIL:
                /**
                 * 初始化vpn失败
                 */
                Log.i(TAG, "RESULT_VPN_INIT_FAIL, error is " + sfAuth.vpnGeterr());
                displayToast("RESULT_VPN_INIT_FAIL, error is " + sfAuth.vpnGeterr());
                if(logout)
                    returnResult(false, "vpn初始化失败");
                else {
                    logout = true;
                    sfAuth.vpnLogout();
                    doLogin(listener);
                }
                break;

            case IVpnDelegate.RESULT_VPN_INIT_SUCCESS:
                /**
                 * 初始化vpn成功，接下来就需要开始认证工作了
                 */
                Log.i(TAG, "RESULT_VPN_INIT_SUCCESS, current vpn status is " + sfAuth.vpnQueryStatus());
                displayToast("RESULT_VPN_INIT_SUCCESS, current vpn status is " + sfAuth.vpnQueryStatus());
                Log.i(TAG, "vpnResult============" + vpnResult + "\nauthType ============" + authType);

                // 设置后台不自动登陆,true为off,即取消自动登陆.默认为false,后台自动登陆.
                // sfAuth.setLoginParam(AUTO_LOGIN_OFF_KEY, "true");
                // 初始化成功，进行认证操作　（此处采用“用户名密码”认证）
                doVpnLogin(IVpnDelegate.AUTH_TYPE_PASSWORD);
                break;

            case IVpnDelegate.RESULT_VPN_AUTH_FAIL:
                /**
                 * 认证失败，有可能是传入参数有误，具体信息可通过sfAuth.vpnGeterr()获取
                 */
                String errString = sfAuth.vpnGeterr();
                Log.i(TAG, "RESULT_VPN_AUTH_FAIL, error is " + errString);
                displayToast("RESULT_VPN_AUTH_FAIL, error is " + errString);
                returnResult(false, "vpn认证失败:" + errString);
                break;

            case IVpnDelegate.RESULT_VPN_AUTH_SUCCESS:
                /**
                 * 认证成功，认证成功有两种情况，一种是认证通过，可以使用sslvpn功能了，
                 *
                 * 另一种是 前一个认证（如：用户名密码认证）通过，但需要继续认证（如：需要继续证书认证）
                 */
                if (authType == IVpnDelegate.AUTH_TYPE_NONE) {

                /*
                 * // session共享登陆--主APP保存：认证成功 保存TWFID（SessionId），供子APP使用 String twfid = sfAuth.getTwfid(); Log.i(TAG, "twfid = "+twfid);
                 */
                    Log.i(TAG, "welcome to sangfor sslvpn!");
                    displayToast("welcome to sangfor sslvpn!");

                    // 若为L3vpn流程，认证成功后会自动开启l3vpn服务，需等l3vpn服务开启完成后再访问资源
                    if (SangforAuth.getInstance().getModuleUsed() == SangforAuth.AUTH_MODULE_EASYAPP) {
                        // EasyApp流程，认证流程结束，可访问资源。
                        doResourceRequest();
                    }
                } else if (authType == IVpnDelegate.VPN_TUNNEL_OK) {
                    // l3vpn流程，l3vpn服务通道建立成功，可访问资源
                    Log.i(TAG, "L3VPN tunnel OK!");
                    displayToast("L3VPN tunnel OK!");
                    doResourceRequest();
                } else {
                    Log.i(TAG, "auth success, and need next auth, next auth type is " + authType);
                    displayToast("auth success, and need next auth, next auth type is " + authType);

                    if (authType == IVpnDelegate.AUTH_TYPE_SMS) {
                        // 下一次认证为短信认证，获取相关的信息
                        String phoneNum = SangforAuth.getInstance().getSmsPhoneNum();
                        String countDown = SangforAuth.getInstance().getSmsCountDown();
                        String toastStrsg = "sms code send to [" + phoneNum + "]\n" + "reget code count down [" + countDown + "]\n";
                        displayToast(toastStrsg);
                    } else if (authType == IVpnDelegate.AUTH_TYPE_RADIUS) {
                        displayToast("start radius challenge auth");
                    } else if (authType == IVpnDelegate.AUTH_TYPE_TOKEN) {
                        String tokenCode = CHALLENGE_CODE;
                        if (TextUtils.isEmpty(tokenCode)) {
                            displayToast("need input tokencode");
                            break;
                        }
                        sfAuth.setLoginParam(IVpnDelegate.TOKEN_AUTH_CODE, tokenCode);
                    } else if(!logout){
                        logout = true;
                        doVpnLogin(authType);
                        return;
                    }
                }
                returnResult(true, "vpn认证成功");
                break;
            case IVpnDelegate.RESULT_VPN_AUTH_CANCEL:
                Log.i(TAG, "RESULT_VPN_AUTH_CANCEL");
                displayToast("RESULT_VPN_AUTH_CANCEL");
                break;
            case IVpnDelegate.RESULT_VPN_AUTH_LOGOUT:
                /**
                 * 主动注销（自己主动调用logout接口）
                 */
                Log.i(TAG, "RESULT_VPN_AUTH_LOGOUT");
                displayToast("RESULT_VPN_AUTH_LOGOUT");
                if(listener2!=null){
                    listener2.onVpnLoginResult(true, "VPN注销成功！");
                }
                break;
            case IVpnDelegate.RESULT_VPN_L3VPN_FAIL:
                /**
                 * L3vpn启动失败，有可能是没有l3vpn资源，具体信息可通过sfAuth.vpnGeterr()获取
                 */
                Log.i(TAG, "RESULT_VPN_L3VPN_FAIL, error is " + sfAuth.vpnGeterr());
                displayToast("RESULT_VPN_L3VPN_FAIL, error is " + sfAuth.vpnGeterr());
                break;
            case IVpnDelegate.RESULT_VPN_L3VPN_SUCCESS:
                /**
                 * L3vpn启动成功
                 */
                Log.i(TAG, "RESULT_VPN_L3VPN_SUCCESS ===== " + SystemConfiguration.getInstance().getSessionId());
                break;
            case IVpnDelegate.RESULT_VPN_L3VPN_RELOGIN:
                /**
                 * L3vpn服务端注销虚拟IP,一般是私有帐号在其他设备同时登录造成的
                 */
                Log.i(TAG, "relogin now");
                displayToast("relogin now");
                break;
            default:
                /**
                 * 其它情况，不会发生，如果到该分支说明代码逻辑有误
                 */
                Log.i(TAG, "default result, vpn result is " + vpnResult);
                displayToast("default result, vpn result is " + vpnResult);
                returnResult(result,errInfo);
                break;
        }
    }

    @Override
    public void reloginCallback(int status, int result) {
        switch (status) {
            case IVpnDelegate.VPN_START_RELOGIN:
                Log.e(TAG, "relogin callback start relogin start ...");
                break;
            case IVpnDelegate.VPN_END_RELOGIN:
                Log.e(TAG, "relogin callback end relogin ...");

                if (result == IVpnDelegate.VPN_RELOGIN_SUCCESS) {
                    Log.e(TAG, "relogin callback, relogin success!");
                    displayToast("relogin callback, relogin success! ");
                } else {
                    Log.e(TAG, "relogin callback, relogin failed");
                    displayToast("relogin callback, relogin failed");
                }
                break;
        }
    }

    /**
     * 认证过程若需要图形校验码，则回调通告图形校验码位图，
     *
     * @param data
     *            图形校验码位图
     */
    @Override
    public void vpnRndCodeCallback(byte[] data) {
        Log.d(TAG, "vpnRndCodeCallback data: " + Boolean.toString(data == null));
        if (data != null) {
            Log.i(TAG, "vpnRndCodeCallback RndCo we not support RndCode now");
        }
    }


    private boolean initVpnModule() {
        SangforAuth sfAuth = SangforAuth.getInstance();
        try {
            // SDK模式初始化，easyapp模式或者是l3vpn模式，两种模式区别请参考文档。
            AUTH_MODULE = SangforAuth.AUTH_MODULE_EASYAPP;
            sfAuth.init(((Activity)context).getApplication(), context, this, AUTH_MODULE);//SangforAuth.AUTH_MODULE_L3VPN、SangforAuth.AUTH_MODULE_EASYAPP
            sfAuth.setLoginParam(AUTH_CONNECT_TIME_OUT, String.valueOf(5));
            return true;
        } catch (Exception ignored) {}
        return false;
    }
    /**
     * 开始初始化VPN，该初始化为异步接口，后续动作通过回调函数vpncallback通知结果
     *
     * @return 成功返回true，失败返回false，一般情况下返回true
     */
    private boolean initSslVpn() {
        SangforAuth sfAuth = SangforAuth.getInstance();
        m_iAddr = null;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    m_iAddr = InetAddress.getByName(VPN_IP);
                    Log.i(TAG, "ip Addr is : " + m_iAddr.getHostAddress());
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String strHost = "";
        if (m_iAddr != null) {
            strHost = m_iAddr.getHostAddress();
        }

        if (TextUtils.isEmpty(strHost)) {
            Log.i(TAG, "vpn host error");
            displayToast("vpn host error");
            return false;
        }
        long host = VpnCommon.ipToLong(strHost);
        if (sfAuth.vpnInit(host, VPN_PORT) == false) {
            Log.d(TAG, "vpn init fail, errno is " + sfAuth.vpnGeterr());
            return false;
        }

        return true;
    }

    /**
     * 处理认证，通过传入认证类型（需要的话可以改变该接口传入一个hashmap的参数用户传入认证参数）.
     *
     * 也可以一次性把认证参数设入，这样就如果认证参数全满足的话就可以一次性认证通过，可见下面屏蔽代码
     *
     * @param authType
     *            认证类型
     * @throws SFException
     */
    private void doVpnLogin(int authType) {
        Log.d(TAG, "doVpnLogin authType " + authType);
        boolean ret = false;
        SangforAuth sfAuth = SangforAuth.getInstance();
        /*
         * // session共享登陆：主APP封装时走原认证流程，子APP认证时使用TWFID（SessionId）认证方式 boolean isMainApp = true; //子APP,isMainApp = false; if(!isMainApp){ authType =
         * IVpnDelegate.AUTH_TYPE_TWFID; }
         */

        switch (authType) {
            case IVpnDelegate.AUTH_TYPE_CERTIFICATE:
                String certPath = CERT_PATH;
                String certPasswd = CERT_PASSWD;
                sfAuth.setLoginParam(IVpnDelegate.CERT_PASSWORD, certPasswd);
                sfAuth.setLoginParam(IVpnDelegate.CERT_P12_FILE_NAME, certPath);
                ret = sfAuth.vpnLogin(IVpnDelegate.AUTH_TYPE_CERTIFICATE);
                break;
            case IVpnDelegate.AUTH_TYPE_PASSWORD:
                String user = USER_NAME;
                String passwd = USER_PASSWD;
                sfAuth.setLoginParam(IVpnDelegate.PASSWORD_AUTH_USERNAME, user);
                sfAuth.setLoginParam(IVpnDelegate.PASSWORD_AUTH_PASSWORD, passwd);
                ret = sfAuth.vpnLogin(IVpnDelegate.AUTH_TYPE_PASSWORD);
                break;
            case IVpnDelegate.AUTH_TYPE_SMS:
                // 进行短信认证
                String smsCode = SMS_CODE;
                sfAuth.setLoginParam(IVpnDelegate.SMS_AUTH_CODE, smsCode);
                ret = sfAuth.vpnLogin(IVpnDelegate.AUTH_TYPE_SMS);
                break;
            case IVpnDelegate.AUTH_TYPE_SMS1:
                ret = sfAuth.vpnLogin(IVpnDelegate.AUTH_TYPE_SMS1);
                break;

            case IVpnDelegate.AUTH_TYPE_TOKEN:
                String token = "123321";
                sfAuth.setLoginParam(IVpnDelegate.TOKEN_AUTH_CODE, token);
                ret = sfAuth.vpnLogin(IVpnDelegate.AUTH_TYPE_TOKEN);
                break;

            case IVpnDelegate.AUTH_TYPE_RADIUS:
                // 进行挑战认证
                String challenge = CHALLENGE_CODE;
                sfAuth.setLoginParam(IVpnDelegate.CHALLENGE_AUTH_REPLY, challenge);
                ret = sfAuth.vpnLogin(IVpnDelegate.AUTH_TYPE_RADIUS);
                break;

            case IVpnDelegate.AUTH_TYPE_TWFID:
                // session共享登陆--子APP登陆：子APP使用TWFID登陆。这两个APP就共享VPN隧道，占用同一个授权。
                String twfid = "11438E3C617095C50D28BA337133872730CBAB0D64F98B53F5105221B9D937E8";
                if (twfid != null && !twfid.equals("")) {
                    Log.i(TAG, "do TWFID Auth, TwfId:" + twfid);
                    sfAuth.setLoginParam(IVpnDelegate.TWF_AUTH_TWFID, twfid);
                    ret = sfAuth.vpnLogin(IVpnDelegate.AUTH_TYPE_TWFID);
                } else {
                    Log.e(TAG, "You hasn't written TwfId");
                    displayToast("You hasn't written TwfId");
                }
                break;
            default:
                Log.w(TAG, "default authType " + authType);
                break;
        }

        if (ret == true) {
            Log.i(TAG, "success to call login method");
        } else {
            Log.i(TAG, "fail to call login method");
        }

    }

    private void doResourceRequest() {
        // 认证结束，可访问资源。
    }

    private void displayToast(String str) {
//        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
    }

    interface VpnLoginResultListener {
        void onVpnLoginResult(boolean result, String errInfo);
    }
}
