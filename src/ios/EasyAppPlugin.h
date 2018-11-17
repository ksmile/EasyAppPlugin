#import <Cordova/CDV.h>

@interface EasyAppPlugin : CDVPlugin

- (void)show:(CDVInvokedUrlCommand*)command;
- (void)hide:(CDVInvokedUrlCommand*)command;

@end
