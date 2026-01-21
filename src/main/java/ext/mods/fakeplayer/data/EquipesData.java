package ext.mods.fakeplayer.data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.data.xml.IXmlReader;
import ext.mods.commons.random.Rnd;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import ext.mods.fakeplayer.holder.EquipesHolder;

public class EquipesData implements IXmlReader
{
	private final List<EquipesHolder> _equipes = new ArrayList<>();
	
	public EquipesData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		parseDataFile("custom/mods/fakesEquipes.xml");
		LOGGER.info("Loaded {} equipes.", _equipes.size());
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "equipmentSets", listNode -> forEach(listNode, "equipment", node ->
		{
			final NamedNodeMap attrs = node.getAttributes();
			
			String classId = attrs.getNamedItem("classId").getNodeValue();
			int minLevel = Integer.parseInt(attrs.getNamedItem("minLevel").getNodeValue());
			int maxLevel = Integer.parseInt(attrs.getNamedItem("maxLevel").getNodeValue());
			
			int rhand = getInt(attrs, "rhand");
			int lhand = getInt(attrs, "lhand");
			int head = getInt(attrs, "head");
			int chest = getInt(attrs, "chest");
			int legs = getInt(attrs, "legs");
			int hands = getInt(attrs, "hands");
			int feet = getInt(attrs, "feet");
			int neck = getInt(attrs, "neck");
			int lear = getInt(attrs, "lear");
			int rear = getInt(attrs, "rear");
			int lring = getInt(attrs, "lring");
			int rring = getInt(attrs, "rring");
			
			_equipes.add(new EquipesHolder(classId, minLevel, maxLevel, rhand, lhand, head, chest, legs, hands, feet, neck, lear, rear, lring, rring));
		}));
	}
	
	private int getInt(NamedNodeMap attrs, String name)
	{
		Node node = attrs.getNamedItem(name);
		return (node != null && !node.getNodeValue().isEmpty()) ? Integer.parseInt(node.getNodeValue()) : 0;
	}
	
	public EquipesHolder getArmorSet(String classId, int level)
	{
		List<EquipesHolder> matching = new ArrayList<>();
		EquipesHolder fallback = null;
		
		for (EquipesHolder holder : _equipes)
		{
			if (holder.getClassId().equalsIgnoreCase(classId))
			{
				if (level >= holder.getMinLevel() && level <= holder.getMaxLevel())
				{
					matching.add(holder);
				}
				else if (holder.getMaxLevel() < level)
				{
					
					if (fallback == null || holder.getMaxLevel() > fallback.getMaxLevel())
						fallback = holder;
				}
			}
		}
		
		if (!matching.isEmpty())
			return matching.get(Rnd.get(matching.size()));
		
		return fallback;
	}
	
	public static EquipesData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final EquipesData INSTANCE = new EquipesData();
	}
}
