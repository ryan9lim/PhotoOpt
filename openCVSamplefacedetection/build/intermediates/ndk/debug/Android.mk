LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := detection_based_tracker
LOCAL_LDFLAGS := -Wl,--build-id
LOCAL_SRC_FILES := \
	/Users/robertrtung/StudioProjects/PhotoOpt/openCVSamplefacedetection/src/main/jni/Android.mk \
	/Users/robertrtung/StudioProjects/PhotoOpt/openCVSamplefacedetection/src/main/jni/Application.mk \
	/Users/robertrtung/StudioProjects/PhotoOpt/openCVSamplefacedetection/src/main/jni/DetectionBasedTracker_jni.cpp \

LOCAL_C_INCLUDES += /Users/robertrtung/StudioProjects/PhotoOpt/openCVSamplefacedetection/src/main/jni
LOCAL_C_INCLUDES += /Users/robertrtung/StudioProjects/PhotoOpt/openCVSamplefacedetection/src/debug/jni

include $(BUILD_SHARED_LIBRARY)
