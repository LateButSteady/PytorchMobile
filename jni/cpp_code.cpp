//
// Created by JWKim on 2021-07-05.
//

#include <jni.h>
#include <string.h>
#include <iostream>

extern "C" JNIEXPORT jstring JNICALL

// MainActivity에서 부를것이다.
//jstring Java_com_example_jwkim_cpp_code_MainActivity_helloWorld(JNIEnv* env, jobject obj){
Java_com_example_jwkim_cpp_code_MainActivity_helloWorld(JNIEnv* env, jobject /* this */){
    //return (*env)->NewStringUTF(env, "Hello World MF!!");

    //std::string hello = "Hello World MF!!";
    return env->NewStringUTF("Hello World MF!!");
    //return env->NewStringUTF(hello.c_str());
}