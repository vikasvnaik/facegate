package com.vikas.facegate.di

import android.content.Context
import android.hardware.camera2.CameraManager
import com.vikas.facegate.data.face.FaceDetectorSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCameraManager(
        @ApplicationContext context: Context
    ): CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    @Provides
    @Singleton
    fun provideFaceDetectorSource(): FaceDetectorSource = FaceDetectorSource()
}