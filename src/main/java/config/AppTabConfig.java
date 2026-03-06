package config;

import java.util.List;

import dyntabs.DynTabConfig;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AppTabConfig extends DynTabConfig {

	@Override
	public List<String> getInitialTabNames() {
		// Tab names that open automatically on page load.
		// Must match the name attribute in @DynTab on your bean classes.
		// Return an empty list if no tabs should open at startup.
		return List.of("HomeDynTab");
	}

	@Override
	public int getMaxNumberOfTabs() {
		return 7; // override if you need more or fewer simultaneous tabs
	}
}
