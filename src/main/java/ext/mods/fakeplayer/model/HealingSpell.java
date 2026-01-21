package ext.mods.fakeplayer.model;

import ext.mods.gameserver.enums.skills.SkillTargetType;

public class HealingSpell extends BotSkill
{
	
	private SkillTargetType _targetType;
	
	public HealingSpell(int skillId, SkillTargetType targetType, SpellUsageCondition condition, int conditionValue, int priority)
	{
		super(skillId, condition, conditionValue, priority);
		_targetType = targetType;
	}
	
	public HealingSpell(int skillId, SkillTargetType targetType, int conditionValue, int priority)
	{
		super(skillId, SpellUsageCondition.LESSHPPERCENT, conditionValue, priority);
		_targetType = targetType;
	}
	
	public SkillTargetType getTargetType()
	{
		return _targetType;
	}
}