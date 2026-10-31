package com.samurailemon.craftersalmanac.compat;

import com.samurailemon.craftersalmanac.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Only loaded when Mod Menu is installed. Adds the "Config" button on our entry in the mod list. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
