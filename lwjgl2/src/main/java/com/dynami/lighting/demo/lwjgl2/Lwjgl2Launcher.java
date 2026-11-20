package com.dynami.lighting.demo.lwjgl2;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.dynami.lighting.demo.DynamicLightingDemo;

/** Launches the desktop (LWJGL2) application. */
public class Lwjgl2Launcher {
    public static void main(String[] args) {
        createApplication();
    }

    private static LwjglApplication createApplication() {
        return new LwjglApplication(new DynamicLightingDemo(), getDefaultConfiguration());
    }

    private static LwjglApplicationConfiguration getDefaultConfiguration() {
        LwjglApplicationConfiguration configuration = new LwjglApplicationConfiguration();
        configuration.title = "DynamicLightingDemo";
        configuration.width = 640;
        configuration.height = 480;
        //// This prevents a confusing error that would appear after exiting normally.
        configuration.forceExit = false;

        for (int size : new int[] { 128, 64, 32, 16 }) {
            configuration.addIcon("libgdx" + size + ".png", FileType.Internal);
        }
        return configuration;
    }
}