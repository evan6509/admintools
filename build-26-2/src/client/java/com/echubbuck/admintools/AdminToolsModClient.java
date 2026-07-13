package com.echubbuck.admintools;

import com.echubbuck.admintools.gui.EnderSeeScreen;
import com.echubbuck.admintools.gui.EnderSeeScreenHandler;
import com.echubbuck.admintools.gui.InvSeeScreen;
import com.echubbuck.admintools.gui.InvSeeScreenHandler;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class AdminToolsModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(InvSeeScreenHandler.TYPE, InvSeeScreen::new);
        MenuScreens.register(EnderSeeScreenHandler.TYPE, EnderSeeScreen::new);
    }
}
