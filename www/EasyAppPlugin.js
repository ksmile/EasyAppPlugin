var exec = require('cordova/exec');

exports.ToastTest = function (arg0, success, error) {
	exec(success, error, "EasyAppPlugin", "ToastTest", [arg0]);
};

exports.VpnLogin = function (arg0, arg1, arg2, success, error) {
	exec(success, error, "EasyAppPlugin", "VpnLogin", [arg0, arg1, arg2]);
};

exports.VpnLogout = function (success, error) {
	exec(success, error, "EasyAppPlugin", "VpnLogout", []);
};

exports.VpnStatus = function (success, error) {
	exec(success, error, "EasyAppPlugin", "VpnStatus", []);
};