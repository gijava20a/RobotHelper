package com.example.myapplication.agora;

import android.content.Context;
import android.view.SurfaceView;
import android.view.ViewGroup;

import io.agora.rtc2.*;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class AgoraHelper {
    private RtcEngine engine;
    private final Context context;

    public AgoraHelper(Context context, String appId, IRtcEngineEventHandler handler) {
        this.context = context.getApplicationContext();
        try {
            RtcEngineConfig cfg = new RtcEngineConfig();
            cfg.mContext = this.context;
            cfg.mAppId = appId;
            cfg.mEventHandler = handler;
            engine = RtcEngine.create(cfg);
            engine.enableVideo();
            engine.setVideoEncoderConfiguration(
                    new VideoEncoderConfiguration(
                            VideoEncoderConfiguration.VD_640x360,
                            VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                            VideoEncoderConfiguration.STANDARD_BITRATE,
                            VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException("Agora init failed: " + e.getMessage());
        }
    }

    public void showRemoteVideo(ViewGroup remoteContainer, int uid) {
        SurfaceView view = new SurfaceView(context);
        remoteContainer.addView(view);
        engine.setupRemoteVideo(new VideoCanvas(view, VideoCanvas.RENDER_MODE_FIT, uid));
    }

    public void joinChannel(String token, String channelName) {
        ChannelMediaOptions opt = new ChannelMediaOptions();
        opt.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
        opt.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        opt.publishCameraTrack = true;
        opt.publishMicrophoneTrack = true;
        engine.joinChannel(token, channelName, 0, opt);
    }

    public void leave() {
        if (engine != null) engine.leaveChannel();
    }

    public void destroy() {
        RtcEngine.destroy();
        engine = null;
    }
}
