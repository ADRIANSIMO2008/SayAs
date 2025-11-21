package com.adriansimo.sayas;

import net.fabricmc.api.ModInitializer;

public class SayAsMod implements ModInitializer {
    public static final String MODID = "sayas";

    @Override
    public void onInitialize() {
        SayAsCommand.register();
        System.out.println("[SayAs] Mod initialized.");
    }
}
