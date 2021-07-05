LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := cpp_code
LOCAL_SRC_FILES := cpp_code.cpp

include $(BUILD_SHARED_LIBRARY)