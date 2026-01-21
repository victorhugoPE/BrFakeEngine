package ext.mods.fakeplayer.holder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ext.mods.gameserver.model.location.Location;

public class WalkerHolder
{
	private static final Map<String, List<Location>> CITIES_WAYPOINTS = new HashMap<>();
	
	public static void addCityWaypoints(String cityName, List<Location> waypoints)
	{
		CITIES_WAYPOINTS.put(cityName, waypoints);
	}
	
	public static List<Location> getCityWaypoints(String cityName)
	{
		return CITIES_WAYPOINTS.get(cityName);
	}
	
	public static Map<String, List<Location>> getAllWaypoints()
	{
		return CITIES_WAYPOINTS;
	}
	
}
