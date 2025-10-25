package com.example.myapplication.agora;

import static androidx.constraintlayout.widget.Constraints.TAG;

import android.content.Context;
import android.util.Log;
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
            engine.enableAudio();

            engine.setDefaultAudioRoutetoSpeakerphone(true);
            engine.setEnableSpeakerphone(true);
            engine.adjustRecordingSignalVolume(100);
            engine.adjustPlaybackSignalVolume(100);
            engine.setAudioProfile(
                    Constants.AUDIO_PROFILE_DEFAULT,
                    Constants.AUDIO_SCENARIO_CHATROOM
            );

            engine.enableAudioVolumeIndication(1000, 3, true);

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
        if (remoteContainer == null) {
            Log.e(TAG, "Remote container is null");
            return;
        }

        if (engine == null) {
            Log.e(TAG, "Engine is null");
            return;
        }

        try {
            remoteContainer.removeAllViews();

            SurfaceView view = new SurfaceView(context);
            view.setZOrderMediaOverlay(true);
            remoteContainer.addView(view);

            int result = engine.setupRemoteVideo(
                    new VideoCanvas(view, VideoCanvas.RENDER_MODE_FIT, uid)
            );

            if (result == 0) {
                Log.d(TAG, "Remote video setup for UID: " + uid);
            } else {
                Log.e(TAG, "Failed to setup remote video. Error: " + result);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in showRemoteVideo: " + e.getMessage());
        }
    }

    public void joinChannel(String token, String channelName) {
        if (engine == null) {
            Log.e(TAG, "Cannot join - engine is null");
            return;
        }

        try {
            Log.d(TAG, "📞 Joining channel: " + channelName);

            ChannelMediaOptions opt = new ChannelMediaOptions();

            opt.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;

            opt.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;

            opt.publishCameraTrack = true;
            opt.publishMicrophoneTrack = true;  // YOUR MIC

            opt.autoSubscribeAudio = true;
            opt.autoSubscribeVideo = true;
            int result = engine.joinChannel(token, channelName, 0, opt);

            if (result == 0) {
                Log.d(TAG, "Join channel initiated successfully");
            } else {
                Log.e(TAG, "Failed to join channel. Error code: " + result);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in joinChannel: " + e.getMessage());
        }
    }

    public void leave() {
        if (engine != null) {
            try {
                int result = engine.leaveChannel();
                if (result == 0) {
                    Log.d(TAG, "Left channel");
                } else {
                    Log.e(TAG, "Leave channel failed: " + result);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error leaving channel: " + e.getMessage());
            }
        }
    }

    public void destroy() {
        try {
            if (engine != null) {
                RtcEngine.destroy();
                engine = null;
                Log.d(TAG, "Engine destroyed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error destroying engine: " + e.getMessage());
        }
    }


    public void muteLocalAudio(boolean mute) {
        if (engine != null) {
            engine.muteLocalAudioStream(mute);
            Log.d(TAG, mute ? "Local audio muted" : "Local audio unmuted");
        }
    }

    public void adjustRecordingVolume(int volume) {
        if (engine != null) {
            engine.adjustRecordingSignalVolume(volume);
            Log.d(TAG, "Recording volume: " + volume);
        }
    }

    public void adjustPlaybackVolume(int volume) {
        if (engine != null) {
            engine.adjustPlaybackSignalVolume(volume);
            Log.d(TAG, "Playback volume: " + volume);
        }
    }

    public void setEnableSpeakerphone(boolean enabled) {
        if (engine != null) {
            engine.setEnableSpeakerphone(enabled);
            Log.d(TAG, enabled ? "Speakerphone ON" : "Earpiece ON");
        }
    }
}
