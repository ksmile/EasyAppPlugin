
#import <Cordova/CDV.h>
#import "AuthHelper.h"
#import "sdkheader.h"
#import "sslvpnnb.h"

@implementation EasyAppPlugin

 #define VPNURL @"183.224.79.51"

- (void)lognToVpn:(CDVInvokedUrlCommand*)command {
  NSDictionary* options = [command argumentAtIndex:0];
//   NSString *message  = options[@"message"];
//   NSString *duration = options[@"duration"];
//   NSString *position = options[@"position"];
//   NSDictionary *data = options[@"data"];
//   NSNumber *addPixelsY = options[@"addPixelsY"];
//   NSDictionary *styling = options[@"styling"];
  
//  if (![position isEqual: @"top"] && ![position isEqual: @"center"] && ![position isEqual: @"bottom"]) {
//    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"invalid position. valid options are 'top', 'center' and 'bottom'"];
//    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
//    return;
//  }
//
//  NSTimeInterval durationMS;
//  if ([duration.lowercaseString isEqualToString: @"short"]) {
//    durationMS = 2000;
//  } else if ([duration.lowercaseString isEqualToString: @"long"]) {
//    durationMS = 4000;
//  } else {
//    durationMS = [duration intValue];
//  }
//
//  [self.webView makeToast:message
//                 duration:durationMS / 1000
//                 position:position
//               addPixelsY:addPixelsY == nil ? 0 : [addPixelsY intValue]
//                     data:data
//                  styling:styling
//          commandDelegate:self.commandDelegate
//               callbackId:command.callbackId];
    
    if(_helper)
    {
        if([_helper queryVpnStatus] == VPN_STATUS_OK){
            [self login];
            return;
        }
    }
    self.helper = [AuthHelper getInstance];
    [self.helper setHost:VPNURL port:443 delegate:self];
  
  CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
  pluginResult.keepCallback = [NSNumber numberWithBool:YES];
  [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)hide:(CDVInvokedUrlCommand*)command {
  [self.webView hideToast];
  
  CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
  [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark SangforSDKDelegate
- (void) onCallBack:(const VPN_RESULT_NO)vpnErrno authType:(const int)authType
{
    switch (vpnErrno)
    {
        case RESULT_VPN_INIT_FAIL:
            
            //            statusTF.text = @"Vpn Init failed!！！！";
            [self.view makeToast:@"Vpn初始化失败"];
            NSLog(@"Vpn Init failed!");
            break;
            
        case RESULT_VPN_AUTH_FAIL:
        {
            [MBProgressHUD hideHUDForView:self.view animated:YES];
            [self.helper clearAuthParam:@SET_RND_CODE_STR];
            [self.helper queryVpnStatus];
            [self.view makeToast:@"Vpn认证失败"];
            break;
        }
        case RESULT_VPN_INIT_SUCCESS:
        {
            //显示当前sdk版本号
            NSString *version = [self.helper getSdkVersion];
            NSLog(@"sdk version is %@",version);
            
            [self.helper setAuthParam:@PORPERTY_NamePasswordAuth_NAME param:_txtVPNName.text];
            [self.helper setAuthParam:@PORPERTY_NamePasswordAuth_PASSWORD param:_txtVPNPassword.text];
            
            [MBProgressHUD showHUDAddedTo:self.view animated:YES];
            
            [self.helper loginVpn:SSL_AUTH_TYPE_PASSWORD];
            
            break;
        }
            
        case RESULT_VPN_AUTH_SUCCESS:
            [MBProgressHUD hideHUDForView:self.view animated:YES];
            [self.view makeToast:@"Vpn登录成功"];
            [self login];
            break;
        case RESULT_VPN_AUTH_LOGOUT:
            NSLog(@"Vpn logout success!");
            break;
        case RESULT_VPN_OTHER:
            if (VPN_OTHER_RELOGIN_FAIL == (VPN_RESULT_OTHER_NO)authType) {
                NSLog(@"Vpn relogin failed, maybe network error");
            }
            break;
            
        case RESULT_VPN_NONE:
            break;
            
        default:
            break;
    }
}

@end
