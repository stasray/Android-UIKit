package io.agora.agorauikit_android

import android.app.Activity
import android.graphics.Rect
import io.agora.agorauikit_android.AgoraRtmController.AgoraRtmController
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.models.UserInfo
import java.util.logging.Level
import java.util.logging.Logger

@ExperimentalUnsignedTypes
class AgoraVideoViewerHandler(private val hostView: AgoraVideoViewer) :
    IRtcEngineEventHandler() {

    override fun onClientRoleChanged(oldRole: Int, newRole: Int) {
        super.onClientRoleChanged(oldRole, newRole)
        val isHost = newRole == Constants.CLIENT_ROLE_BROADCASTER
        if (!isHost) {
            this.hostView.userVideoLookup.remove(this.hostView.userID)
        } else if (!this.hostView.userVideoLookup.contains(this.hostView.userID)) {
            (this.hostView.context as Activity).runOnUiThread(
                Runnable {
                    this.hostView.addLocalVideo()
                }
            )
        }
        // Only show the camera options when we are a broadcaster
//            this.getControlContainer().isHidden = !isHost

        this.hostView.rtcOverrideHandler?.onClientRoleChanged(oldRole, newRole)
    }

    override fun onUserJoined(uid: Int, elapsed: Int) {
        Logger.getLogger("AgoraUIKit").log(Level.INFO, "onUserJoined: $uid")
        super.onUserJoined(uid, elapsed)
        this.hostView.remoteUserIDs.add(uid)

        this.hostView.rtcOverrideHandler?.onUserJoined(uid, elapsed)
    }

    override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
        super.onRemoteAudioStateChanged(uid, state, reason, elapsed)
        Logger.getLogger("AgoraUIKit").log(Level.WARNING, "setting muted state: " + state)
        (this.hostView.context as Activity).runOnUiThread {
            if (state == Constants.REMOTE_AUDIO_STATE_STOPPED || state == Constants.REMOTE_AUDIO_STATE_STARTING || state == Constants.REMOTE_VIDEO_STATE_DECODING) {
                if (state == Constants.REMOTE_AUDIO_STATE_STARTING && !this.hostView.userVideoLookup.containsKey(
                        uid
                    )
                ) {
                    this.hostView.addUserVideo(uid)
                }
                if (this.hostView.userVideoLookup.containsKey(uid)) {
                    this.hostView.userVideoLookup[uid]?.audioMuted =
                        state == Constants.REMOTE_AUDIO_STATE_STOPPED
                }
            }
        }

        this.hostView.rtcOverrideHandler?.onRemoteAudioStateChanged(uid, state, reason, elapsed)
    }

    override fun onUserOffline(uid: Int, reason: Int) {
        super.onUserOffline(uid, reason)
        Logger.getLogger("AgoraUIKit").log(Level.WARNING, "User offline: $reason")
        if (reason == Constants.USER_OFFLINE_QUIT || reason == Constants.USER_OFFLINE_DROPPED) {
            this.hostView.remoteUserIDs.remove(uid)
        }
        if (this.hostView.userVideoLookup.containsKey(uid)) {
            (this.hostView.context as Activity).runOnUiThread {
                this.hostView.removeUserVideo(uid)
            }
        }

        this.hostView.rtcOverrideHandler?.onUserOffline(uid, reason)
    }

    override fun onActiveSpeaker(uid: Int) {
        super.onActiveSpeaker(uid)
        this.hostView.activeSpeaker = uid

        this.hostView.rtcOverrideHandler?.onActiveSpeaker(uid)
    }

    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
        super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
        (this.hostView.context as Activity).runOnUiThread {
            when (state) {
                Constants.REMOTE_VIDEO_STATE_DECODING -> {
                    if (!this.hostView.userVideoLookup.containsKey(uid)) {
                        this.hostView.addUserVideo(uid)
                    }
                    this.hostView.userVideoLookup[uid]?.videoMuted = false
                    if (this.hostView.activeSpeaker == null && uid != this.hostView.userID) {
                        this.hostView.activeSpeaker = uid
                    }
                }
                Constants.REMOTE_VIDEO_STATE_STOPPED -> {
                    this.hostView.userVideoLookup[uid]?.videoMuted = true
                }
            }
        }

        this.hostView.rtcOverrideHandler?.onRemoteVideoStateChanged(uid, state, reason, elapsed)
    }

    override fun onLocalVideoStateChanged(localVideoState: Int, error: Int) {
        super.onLocalVideoStateChanged(localVideoState, error)
        (this.hostView.context as Activity).runOnUiThread {
            when (localVideoState) {
                Constants.LOCAL_VIDEO_STREAM_STATE_CAPTURING, Constants.LOCAL_VIDEO_STREAM_STATE_STOPPED -> {
                    this.hostView.userVideoLookup[
                        this.hostView.userID
                    ]?.videoMuted = localVideoState == Constants.LOCAL_VIDEO_STREAM_STATE_STOPPED
                }
            }
        }

        this.hostView.rtcOverrideHandler?.onLocalVideoStateChanged(localVideoState, error)
    }

    override fun onLocalAudioStateChanged(state: Int, error: Int) {
        super.onLocalAudioStateChanged(state, error)
        (this.hostView.context as Activity).runOnUiThread {
            when (state) {
                Constants.LOCAL_AUDIO_STREAM_STATE_CAPTURING, Constants.LOCAL_AUDIO_STREAM_STATE_STOPPED -> {
                    this.hostView.userVideoLookup[
                        this.hostView.userID
                    ]?.audioMuted = state == Constants.LOCAL_AUDIO_STREAM_STATE_STOPPED
                }
            }
        }

        this.hostView.rtcOverrideHandler?.onLocalAudioStateChanged(state, error)
    }

    override fun onFirstLocalAudioFramePublished(elapsed: Int) {
        super.onFirstLocalAudioFramePublished(elapsed)
//        this.hostView.addLocalVideo()?.audioMuted = false

        this.hostView.rtcOverrideHandler?.onFirstLocalAudioFramePublished(elapsed)
    }

    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
        super.onJoinChannelSuccess(channel, uid, elapsed)

        this.hostView.connectionData.channel = channel
        Logger.getLogger("AgoraUIKit").log(Level.SEVERE, "join channel success")
        this.hostView.userID = uid
        if (this.hostView.userRole == Constants.CLIENT_ROLE_BROADCASTER) {
            (this.hostView.context as Activity).runOnUiThread(
                Runnable {
                    this.hostView.addLocalVideo()
                }
            )
        }
        channel?.let {
            this.hostView.delegate?.joinedChannel(it)
        }
        this.hostView.isInRtcChannel = true
        if (this.hostView.agoraRtmController.loginStatus != AgoraRtmController.LoginStatus.LOGGED_IN) {
            this.hostView.triggerLoginToRtm()
        }

        this.hostView.rtcOverrideHandler?.onJoinChannelSuccess(channel, uid, elapsed)
    }

    override fun onTokenPrivilegeWillExpire(token: String?) {
        super.onTokenPrivilegeWillExpire(token)
        if (this.hostView.delegate?.tokenWillExpire(token) == true) {
            return
        }
        this.hostView.fetchRenewToken()

        this.hostView.rtcOverrideHandler?.onTokenPrivilegeWillExpire(token)
    }

    override fun onRequestToken() {
        super.onRequestToken()
        if (this.hostView.delegate?.tokenDidExpire() == true) {
            return
        }
        this.hostView.fetchRenewToken()

        this.hostView.rtcOverrideHandler?.onRequestToken()
    }

    override fun onApiCallExecuted(error: Int, api: String?, result: String?) {
        super.onApiCallExecuted(error, api, result)

        this.hostView.rtcOverrideHandler?.onApiCallExecuted(error, api, result)
    }

    override fun onAudioEffectFinished(soundId: Int) {
        super.onAudioEffectFinished(soundId)

        this.hostView.rtcOverrideHandler?.onAudioEffectFinished(soundId)
    }

    override fun onAudioMixingStateChanged(state: Int, reason: Int) {
        super.onAudioMixingStateChanged(state, reason)

        this.hostView.rtcOverrideHandler?.onAudioMixingStateChanged(state, reason)
    }

    override fun onAudioPublishStateChanged(
        channel: String?,
        oldState: Int,
        newState: Int,
        elapseSinceLastState: Int
    ) {
        super.onAudioPublishStateChanged(channel, oldState, newState, elapseSinceLastState)

        this.hostView.rtcOverrideHandler?.onAudioPublishStateChanged(channel, oldState, newState, elapseSinceLastState)
    }

    override fun onAudioRouteChanged(routing: Int) {
        super.onAudioRouteChanged(routing)

        this.hostView.rtcOverrideHandler?.onAudioRouteChanged(routing)
    }

    override fun onAudioSubscribeStateChanged(
        channel: String?,
        uid: Int,
        oldState: Int,
        newState: Int,
        elapseSinceLastState: Int
    ) {
        super.onAudioSubscribeStateChanged(channel, uid, oldState, newState, elapseSinceLastState)

        this.hostView.rtcOverrideHandler?.onAudioSubscribeStateChanged(channel, uid, oldState, newState, elapseSinceLastState)
    }

    override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
        super.onAudioVolumeIndication(speakers, totalVolume)

        this.hostView.rtcOverrideHandler?.onAudioVolumeIndication(speakers, totalVolume)
    }

    override fun onCameraExposureAreaChanged(rect: Rect?) {
        super.onCameraExposureAreaChanged(rect)

        this.hostView.rtcOverrideHandler?.onCameraExposureAreaChanged(rect)
    }

    override fun onCameraFocusAreaChanged(rect: Rect?) {
        super.onCameraFocusAreaChanged(rect)

        this.hostView.rtcOverrideHandler?.onCameraExposureAreaChanged(rect)
    }

    override fun onChannelMediaRelayEvent(code: Int) {
        super.onChannelMediaRelayEvent(code)

        this.hostView.rtcOverrideHandler?.onChannelMediaRelayEvent(code)
    }

    override fun onChannelMediaRelayStateChanged(state: Int, code: Int) {
        super.onChannelMediaRelayStateChanged(state, code)

        this.hostView.rtcOverrideHandler?.onChannelMediaRelayStateChanged(state, code)
    }

    override fun onClientRoleChangeFailed(reason: Int, currentRole: Int) {
        super.onClientRoleChangeFailed(reason, currentRole)

        this.hostView.rtcOverrideHandler?.onClientRoleChangeFailed(reason, currentRole)
    }

    override fun onConnectionLost() {
        super.onConnectionLost()

        this.hostView.rtcOverrideHandler?.onConnectionLost()
    }

    override fun onConnectionStateChanged(state: Int, reason: Int) {
        super.onConnectionStateChanged(state, reason)

        this.hostView.rtcOverrideHandler?.onConnectionStateChanged(state, reason)
    }

    override fun onContentInspectResult(result: Int) {
        super.onContentInspectResult(result)

        this.hostView.rtcOverrideHandler?.onContentInspectResult(result)
    }

    override fun onError(err: Int) {
        super.onError(err)

        this.hostView.rtcOverrideHandler?.onError(err)
    }

    override fun onFacePositionChanged(
        imageWidth: Int,
        imageHeight: Int,
        faces: Array<out AgoraFacePositionInfo>?
    ) {
        super.onFacePositionChanged(imageWidth, imageHeight, faces)

        this.hostView.rtcOverrideHandler?.onFacePositionChanged(imageHeight, imageHeight, faces)
    }

    override fun onFirstLocalVideoFrame(width: Int, height: Int, elapsed: Int) {
        super.onFirstLocalVideoFrame(width, height, elapsed)

        this.hostView.rtcOverrideHandler?.onFirstLocalVideoFrame(width, height, elapsed)
    }

    override fun onFirstLocalVideoFramePublished(elapsed: Int) {
        super.onFirstLocalVideoFramePublished(elapsed)

        this.hostView.rtcOverrideHandler?.onFirstLocalAudioFramePublished(elapsed)
    }

    override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
        super.onFirstRemoteVideoDecoded(uid, width, height, elapsed)

        this.hostView.rtcOverrideHandler?.onFirstRemoteVideoDecoded(uid, width, height, elapsed)
    }

    override fun onFirstRemoteVideoFrame(uid: Int, width: Int, height: Int, elapsed: Int) {
        super.onFirstRemoteVideoFrame(uid, width, height, elapsed)

        this.hostView.rtcOverrideHandler?.onFirstRemoteVideoFrame(uid, width, height, elapsed)
    }

    override fun onLastmileProbeResult(result: LastmileProbeResult?) {
        super.onLastmileProbeResult(result)

        this.hostView.rtcOverrideHandler?.onLastmileProbeResult(result)
    }

    override fun onLastmileQuality(quality: Int) {
        super.onLastmileQuality(quality)

        this.hostView.rtcOverrideHandler?.onLastmileQuality(quality)
    }

    override fun onLeaveChannel(stats: RtcStats?) {
        super.onLeaveChannel(stats)

        this.hostView.rtcOverrideHandler?.onLeaveChannel(stats)
    }

    override fun onLocalAudioStats(stats: LocalAudioStats?) {
        super.onLocalAudioStats(stats)

        this.hostView.rtcOverrideHandler?.onLocalAudioStats(stats)
    }

    override fun onLocalPublishFallbackToAudioOnly(isFallbackOrRecover: Boolean) {
        super.onLocalPublishFallbackToAudioOnly(isFallbackOrRecover)

        this.hostView.rtcOverrideHandler?.onLocalPublishFallbackToAudioOnly(isFallbackOrRecover)
    }

    override fun onLocalUserRegistered(uid: Int, userAccount: String?) {
        super.onLocalUserRegistered(uid, userAccount)

        this.hostView.rtcOverrideHandler?.onLocalUserRegistered(uid, userAccount)
    }

    override fun onLocalVideoStats(stats: LocalVideoStats?) {
        super.onLocalVideoStats(stats)

        this.hostView.rtcOverrideHandler?.onLocalVideoStats(stats)
    }

    override fun onLocalVoicePitchInHz(pitchInHz: Int) {
        super.onLocalVoicePitchInHz(pitchInHz)

        this.hostView.rtcOverrideHandler?.onLocalVoicePitchInHz(pitchInHz)
    }

    override fun onMediaEngineLoadSuccess() {
        super.onMediaEngineLoadSuccess()

        this.hostView.rtcOverrideHandler?.onMediaEngineLoadSuccess()
    }

    override fun onMediaEngineStartCallSuccess() {
        super.onMediaEngineStartCallSuccess()

        this.hostView.rtcOverrideHandler?.onMediaEngineStartCallSuccess()
    }

    override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
        super.onNetworkQuality(uid, txQuality, rxQuality)

        this.hostView.rtcOverrideHandler?.onNetworkQuality(uid, txQuality, rxQuality)
    }

    override fun onNetworkTypeChanged(type: Int) {
        super.onNetworkTypeChanged(type)

        this.hostView.rtcOverrideHandler?.onNetworkTypeChanged(type)
    }

    override fun onProxyConnected(
        channel: String?,
        uid: Int,
        proxyType: Int,
        localProxyIp: String?,
        elapsed: Int
    ) {
        super.onProxyConnected(channel, uid, proxyType, localProxyIp, elapsed)

        this.hostView.rtcOverrideHandler?.onProxyConnected(channel, uid, proxyType, localProxyIp, elapsed)
    }

    override fun onRejoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
        super.onRejoinChannelSuccess(channel, uid, elapsed)

        this.hostView.rtcOverrideHandler?.onRejoinChannelSuccess(channel, uid, elapsed)
    }

    override fun onRemoteAudioStats(stats: RemoteAudioStats?) {
        super.onRemoteAudioStats(stats)

        this.hostView.rtcOverrideHandler?.onRemoteAudioStats(stats)
    }

    override fun onRemoteSubscribeFallbackToAudioOnly(uid: Int, isFallbackOrRecover: Boolean) {
        super.onRemoteSubscribeFallbackToAudioOnly(uid, isFallbackOrRecover)

        this.hostView.rtcOverrideHandler?.onRemoteSubscribeFallbackToAudioOnly(uid, isFallbackOrRecover)
    }

    override fun onRemoteVideoStats(stats: RemoteVideoStats?) {
        super.onRemoteVideoStats(stats)

        this.hostView.rtcOverrideHandler?.onRemoteVideoStats(stats)
    }

    override fun onRequestAudioFileInfo(info: AudioFileInfo?, error: Int) {
        super.onRequestAudioFileInfo(info, error)

        this.hostView.rtcOverrideHandler?.onRequestAudioFileInfo(info, error)
    }

    override fun onRtcStats(stats: RtcStats?) {
        super.onRtcStats(stats)

        this.hostView.rtcOverrideHandler?.onRtcStats(stats)
    }

    override fun onRtmpStreamingEvent(url: String?, error: Int) {
        super.onRtmpStreamingEvent(url, error)

        this.hostView.rtcOverrideHandler?.onRtmpStreamingEvent(url, error)
    }

    override fun onRtmpStreamingStateChanged(url: String?, state: Int, errCode: Int) {
        super.onRtmpStreamingStateChanged(url, state, errCode)

        this.hostView.rtcOverrideHandler?.onRtmpStreamingStateChanged(url, state, errCode)
    }

    override fun onSnapshotTaken(
        channel: String?,
        uid: Int,
        filePath: String?,
        width: Int,
        height: Int,
        errCode: Int
    ) {
        super.onSnapshotTaken(channel, uid, filePath, width, height, errCode)

        this.hostView.rtcOverrideHandler?.onSnapshotTaken(channel, uid, filePath, width, height, errCode)
    }

    override fun onStreamInjectedStatus(url: String?, uid: Int, status: Int) {
        super.onStreamInjectedStatus(url, uid, status)

        this.hostView.rtcOverrideHandler?.onStreamInjectedStatus(url, uid, status)
    }

    override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray?) {
        super.onStreamMessage(uid, streamId, data)

        this.hostView.rtcOverrideHandler?.onStreamMessage(uid, streamId, data)
    }

    override fun onStreamMessageError(
        uid: Int,
        streamId: Int,
        error: Int,
        missed: Int,
        cached: Int
    ) {
        super.onStreamMessageError(uid, streamId, error, missed, cached)

        this.hostView.rtcOverrideHandler?.onStreamMessageError(uid, streamId, error, missed, cached)
    }

    override fun onTranscodingUpdated() {
        super.onTranscodingUpdated()

        this.hostView.rtcOverrideHandler?.onTranscodingUpdated()
    }

    override fun onUploadLogResult(requestId: String?, success: Boolean, reason: Int) {
        super.onUploadLogResult(requestId, success, reason)

        this.hostView.rtcOverrideHandler?.onUploadLogResult(requestId, success, reason)
    }

    override fun onUserEnableLocalVideo(uid: Int, enabled: Boolean) {
        super.onUserEnableLocalVideo(uid, enabled)

        this.hostView.rtcOverrideHandler?.onUserEnableLocalVideo(uid, enabled)
    }

    override fun onUserEnableVideo(uid: Int, enabled: Boolean) {
        super.onUserEnableVideo(uid, enabled)

        this.hostView.rtcOverrideHandler?.onUserEnableVideo(uid, enabled)
    }

    override fun onUserInfoUpdated(uid: Int, userInfo: UserInfo?) {
        super.onUserInfoUpdated(uid, userInfo)

        this.hostView.rtcOverrideHandler?.onUserInfoUpdated(uid, userInfo)
    }

    override fun onUserMuteAudio(uid: Int, muted: Boolean) {
        super.onUserMuteAudio(uid, muted)

        this.hostView.rtcOverrideHandler?.onUserMuteAudio(uid, muted)
    }

    override fun onUserMuteVideo(uid: Int, muted: Boolean) {
        super.onUserMuteVideo(uid, muted)

        this.hostView.rtcOverrideHandler?.onUserMuteVideo(uid, muted)
    }

    override fun onUserSuperResolutionEnabled(uid: Int, enabled: Boolean, reason: Int) {
        super.onUserSuperResolutionEnabled(uid, enabled, reason)

        this.hostView.rtcOverrideHandler?.onUserSuperResolutionEnabled(uid, enabled, reason)
    }

    override fun onVideoPublishStateChanged(
        channel: String?,
        oldState: Int,
        newState: Int,
        elapseSinceLastState: Int
    ) {
        super.onVideoPublishStateChanged(channel, oldState, newState, elapseSinceLastState)

        this.hostView.rtcOverrideHandler?.onVideoPublishStateChanged(channel, oldState, newState, elapseSinceLastState)
    }

    override fun onVideoSizeChanged(uid: Int, width: Int, height: Int, rotation: Int) {
        super.onVideoSizeChanged(uid, width, height, rotation)

        this.hostView.rtcOverrideHandler?.onVideoSizeChanged(uid, width, height, rotation)
    }

    override fun onVideoSubscribeStateChanged(
        channel: String?,
        uid: Int,
        oldState: Int,
        newState: Int,
        elapseSinceLastState: Int
    ) {
        super.onVideoSubscribeStateChanged(channel, uid, oldState, newState, elapseSinceLastState)

        this.hostView.rtcOverrideHandler?.onVideoSubscribeStateChanged(channel, uid, oldState, newState, elapseSinceLastState)
    }

    override fun onVirtualBackgroundSourceEnabled(enabled: Boolean, reason: Int) {
        super.onVirtualBackgroundSourceEnabled(enabled, reason)

        this.hostView.rtcOverrideHandler?.onVirtualBackgroundSourceEnabled(enabled, reason)
    }

    override fun onWarning(warn: Int) {
        super.onWarning(warn)

        this.hostView.rtcOverrideHandler?.onWarning(warn)
    }

    override fun onWlAccMessage(reason: Int, action: Int, wlAccMsg: String?) {
        super.onWlAccMessage(reason, action, wlAccMsg)

        this.hostView.rtcOverrideHandler?.onWlAccMessage(reason, action, wlAccMsg)
    }

    override fun onWlAccStats(currentStats: WlAccStats?, averageStats: WlAccStats?) {
        super.onWlAccStats(currentStats, averageStats)

        this.hostView.rtcOverrideHandler?.onWlAccStats(currentStats, averageStats)
    }
}
