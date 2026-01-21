package ext.mods.fakeplayer.holder;

public class EquipesHolder
{
	private final String _classId;
	private final int _minLevel;
	private final int _maxLevel;
	private final int _rhand;
	private final int _lhand;
	private final int _head;
	private final int _chest;
	private final int _legs;
	private final int _hands;
	private final int _feet;
	private final int _neck;
	private final int _lear;
	private final int _rear;
	private final int _lring;
	private final int _rring;
	
	public EquipesHolder(String classId, int minLevel, int maxLevel, int rhand, int lhand, int head, int chest, int legs, int hands, int feet, int neck, int lear, int rear, int lring, int rring)
	{
		_classId = classId;
		_minLevel = minLevel;
		_maxLevel = maxLevel;
		_rhand = rhand;
		_lhand = lhand;
		_head = head;
		_chest = chest;
		_legs = legs;
		_hands = hands;
		_feet = feet;
		_neck = neck;
		_lear = lear;
		_rear = rear;
		_lring = lring;
		_rring = rring;
	}
	
	public String getClassId()
	{
		return _classId;
	}
	
	public int getMinLevel()
	{
		return _minLevel;
	}
	
	public int getMaxLevel()
	{
		return _maxLevel;
	}
	
	public int getRhand()
	{
		return _rhand;
	}
	
	public int getLhand()
	{
		return _lhand;
	}
	
	public int getHead()
	{
		return _head;
	}
	
	public int getChest()
	{
		return _chest;
	}
	
	public int getLegs()
	{
		return _legs;
	}
	
	public int getHands()
	{
		return _hands;
	}
	
	public int getFeet()
	{
		return _feet;
	}
	
	public int getNeck()
	{
		return _neck;
	}
	
	public int getLear()
	{
		return _lear;
	}
	
	public int getRear()
	{
		return _rear;
	}
	
	public int getLring()
	{
		return _lring;
	}
	
	public int getRring()
	{
		return _rring;
	}
}
