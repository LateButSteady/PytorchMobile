//
// Created by JWKim on 2021-07-05.
//

#include <jni.h>
#include <string.h>
#include <iostream>

extern "C" JNIEXPORT jstring JNICALL

// SubActivity_Test에서 부를것이다.
Java_com_example_jwkim_kr_pytorchmobile_MainActivity_helloWorld(JNIEnv* env, jobject /* this */){

    return env->NewStringUTF("Hello World MF!!");
}