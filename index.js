import {
	Platform,
	NativeModules,
  NativeAppEventEmitter
} from 'react-native';

//module.exports = NativeModules.WaveformViewModule;

let WaveformViewModule = NativeModules.WaveformViewModule;
export default {
	init(options){
		let opt = {
		    onStop(){},
			...options
		};
		let fnConf = {
			confirm: opt.onStop,
		};
		WaveformViewModule._init(opt);
        this.listener && this.listener.remove();
        this.listener = NativeAppEventEmitter.addListener('confirmEvent', event => {
            fnConf[event['type']](event['voiceResult'], event['voiceApiType']);
        });
	},
	start(options){
		WaveformViewModule.start(options);
	},
	stop() {
		WaveformViewModule.stop();
	},
  isWaveformShow(callback){
		WaveformViewModule.isWaveformShow(callback);
	},
	initVoice(options){
		let opt = {
		    onStop(){},
			...options
		};
		let fnConf = {
			confirm: opt.onStop,
		};
		WaveformViewModule.initRecordVoice(opt);
        this.listener && this.listener.remove();
        this.listener = NativeAppEventEmitter.addListener('confirmEvent', event => {
            fnConf[event['type']](event['voiceResult'], event['voiceApiType']);
        });
	},
	startVoice(options){
		WaveformViewModule.startRecordVoice(options);
	},
	alert(msg){
    WaveformViewModule.alert(msg);
	}
}

