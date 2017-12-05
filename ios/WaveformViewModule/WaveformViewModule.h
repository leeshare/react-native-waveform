//
//  RNNetworkingManager.h
//  RNNetworking
//
//  Created by Erdem Başeğmez on 18.06.2015.
//  Copyright (c) 2015 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
//#import <UIKit/UIKit.h>
//#import "RCTBridgeModule.h"
#import <React/RCTBridge.h>
#import <UIKit/UIKit.h>

@class ISEParams;

@protocol ISESettingDelegate <NSObject>

- (void)onParamsChanged:(ISEParams *)params;

@end

@interface WaveformViewModule : NSObject <RCTBridgeModule>

@property (nonatomic, strong) ISEParams *iseParams;
//@property (nonatomic, weak) id <ISESettingDelegate> delegate;

//@property NSString *selfVoiceDir;

@end
