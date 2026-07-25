// Self-contained Android build pipeline.
//
// It provisions everything it needs by itself — a Temurin JDK 17 and the Android SDK
// (command-line tools, platform-tools, platform android-34, build-tools 34.0.0) — into a
// per-agent cache under $HOME/.android-ci, then builds with the committed Gradle wrapper
// (which downloads Gradle 8.7 itself). Nothing needs to be pre-installed on the agent
// except a POSIX shell with: git, curl, unzip, tar.
//
// Point a Jenkins "Pipeline from SCM" job at this repo; no global tool config required.

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '10'))
    }

    environment {
        // Persistent tool cache (survives workspace cleanups → fast rebuilds).
        TOOLS_DIR        = "${HOME}/.android-ci"
        JAVA_HOME        = "${HOME}/.android-ci/jdk-17"
        ANDROID_SDK_ROOT = "${HOME}/.android-ci/android-sdk"
        ANDROID_HOME     = "${HOME}/.android-ci/android-sdk"
        GRADLE_USER_HOME = "${HOME}/.android-ci/gradle"   // caches Gradle + dependencies
        // Pinned versions
        CMDLINE_TOOLS_VER = "11076708"
        ANDROID_PLATFORM  = "platforms;android-34"
        ANDROID_BUILDTOOLS = "build-tools;34.0.0"
        // Put the provisioned JDK + SDK tools on PATH for all steps.
        PATH = "${HOME}/.android-ci/jdk-17/bin:${HOME}/.android-ci/android-sdk/platform-tools:${PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Provision JDK 17') {
            steps {
                sh label: 'Install Temurin JDK 17', script: '''
                    set -eu
                    mkdir -p "$TOOLS_DIR"
                    if [ ! -x "$JAVA_HOME/bin/javac" ]; then
                        echo "==> Downloading Temurin JDK 17 (latest GA, linux x64)"
                        # For arm64 agents, change x64 -> aarch64 in the URL below.
                        curl -fSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" \
                            -o /tmp/jdk17.tar.gz
                        rm -rf "$JAVA_HOME"; mkdir -p "$JAVA_HOME"
                        tar -xzf /tmp/jdk17.tar.gz -C "$JAVA_HOME" --strip-components=1
                        rm -f /tmp/jdk17.tar.gz
                    fi
                    "$JAVA_HOME/bin/java" -version
                '''
            }
        }

        stage('Provision Android SDK') {
            steps {
                sh label: 'Install Android SDK packages', script: '''
                    set -eu
                    SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
                    if [ ! -x "$SDKMANAGER" ]; then
                        echo "==> Downloading Android command-line tools"
                        curl -fSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VER}_latest.zip" \
                            -o /tmp/cmdline-tools.zip
                        rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest" "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
                        mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
                        unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
                        # The zip extracts to a "cmdline-tools" dir; sdkmanager expects it at ".../latest".
                        mv "$ANDROID_SDK_ROOT/cmdline-tools/tmp/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
                        rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp" /tmp/cmdline-tools.zip
                    fi

                    echo "==> Accepting licenses + installing SDK packages"
                    yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true
                    "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
                        "platform-tools" "$ANDROID_PLATFORM" "$ANDROID_BUILDTOOLS"
                '''
            }
        }

        stage('Build & Test') {
            steps {
                sh label: 'Gradle assembleDebug + unit tests', script: '''
                    set -eu
                    echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
                    chmod +x gradlew
                    ./gradlew --no-daemon --stacktrace clean testDebugUnitTest assembleDebug
                '''
            }
        }
    }

    post {
        always {
            junit testResults: 'app/build/test-results/testDebugUnitTest/*.xml',
                  allowEmptyResults: true
        }
        success {
            archiveArtifacts artifacts: 'app/build/outputs/apk/debug/*.apk', fingerprint: true
            sh 'echo "Artifacts:"; ls -lh app/build/outputs/apk/debug/*.apk'
        }
    }
}
