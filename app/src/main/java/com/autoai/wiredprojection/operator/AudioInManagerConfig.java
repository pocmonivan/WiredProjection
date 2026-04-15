package com.autoai.wiredprojection.operator;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRecorder;

public final class AudioInManagerConfig {
    public static final int DEFAULT_PORT_DIGITAL = 5;

    public final int defaultPort;
    public final int sampleRate;
    public final int channelConfig;
    public final int audioFormat;
    public final int recordBufferMultiplier;
    public final int pipeBufferSize;
    public final int ignoreFramesCount;
    public final int audioSourceType;
    public final int outputStreamType;
    public final int focusStreamType;
    public final int focusGainType;

    private AudioInManagerConfig(Builder builder) {
        defaultPort = builder.defaultPort;
        sampleRate = builder.sampleRate;
        channelConfig = builder.channelConfig;
        audioFormat = builder.audioFormat;
        recordBufferMultiplier = builder.recordBufferMultiplier;
        pipeBufferSize = builder.pipeBufferSize;
        ignoreFramesCount = builder.ignoreFramesCount;
        audioSourceType = builder.audioSourceType;
        outputStreamType = builder.outputStreamType;
        focusStreamType = builder.focusStreamType;
        focusGainType = builder.focusGainType;
    }

    public static AudioInManagerConfig defaultConfig() {
        return new Builder().build();
    }

    public static final class Builder {
        private int defaultPort = DEFAULT_PORT_DIGITAL;
        private int sampleRate = 48000;
        private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        private int recordBufferMultiplier = 8;
        private int pipeBufferSize = 1024;
        private int ignoreFramesCount = 3;
        private int audioSourceType = MediaRecorder.AudioSource.DIGITAL_IN;
        private int outputStreamType = AudioManager.STREAM_AUXIN;
        private int focusStreamType = AudioManager.STREAM_MUSIC;
        private int focusGainType = AudioManager.AUDIOFOCUS_GAIN;

        public Builder setDefaultPort(int value) {
            defaultPort = value;
            return this;
        }

        public Builder setSampleRate(int value) {
            sampleRate = value;
            return this;
        }

        public Builder setChannelConfig(int value) {
            channelConfig = value;
            return this;
        }

        public Builder setAudioFormat(int value) {
            audioFormat = value;
            return this;
        }

        public Builder setRecordBufferMultiplier(int value) {
            recordBufferMultiplier = value;
            return this;
        }

        public Builder setPipeBufferSize(int value) {
            pipeBufferSize = value;
            return this;
        }

        public Builder setIgnoreFramesCount(int value) {
            ignoreFramesCount = value;
            return this;
        }

        public Builder setAudioSourceType(int value) {
            audioSourceType = value;
            return this;
        }

        public Builder setOutputStreamType(int value) {
            outputStreamType = value;
            return this;
        }

        public Builder setFocusStreamType(int value) {
            focusStreamType = value;
            return this;
        }

        public Builder setFocusGainType(int value) {
            focusGainType = value;
            return this;
        }

        public AudioInManagerConfig build() {
            return new AudioInManagerConfig(this);
        }
    }
}
