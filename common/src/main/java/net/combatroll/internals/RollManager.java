package net.combatroll.internals;

import net.combatroll.CombatRoll;
import net.combatroll.api.EntityAttributes_CombatRoll;
import net.combatroll.client.CombatRollClient;
import net.combatroll.mixin.PlayerEntityAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

import static net.combatroll.api.EntityAttributes_CombatRoll.Type.COUNT;
import static net.combatroll.api.EntityAttributes_CombatRoll.Type.RECHARGE;

public class RollManager {
    public boolean isEnabled = true;
    public static int rollDuration() {
        return CombatRoll.config.roll_duration;
    }
    private int timeSinceLastRoll = 10;
    private int currentCooldownProgress = 0;
    private int currentCooldownLength = 0;
    private int maxRolls = 1;
    private int availableRolls = 0;
    private double stepHeightInitial = -1.0;
    private double newStepHeight;
    private double stepHeightBoost = 0.6;
    
    public RollManager() { }

    public record CooldownInfo(int elapsed, int total, int availableRolls, int maxRolls) { }

    public CooldownInfo getCooldown() {
        return new CooldownInfo(currentCooldownProgress, currentCooldownLength, availableRolls, maxRolls);
    }

    public boolean isRollAvailable(PlayerEntity player) {
        return isEnabled
                && !isRolling()
                && availableRolls > 0
                && !((PlayerEntityAccessor)player).invokeIsImmobile_CombatRoll()
                && player.canMoveVoluntarily()
                && player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) > 0;
    }

    public boolean isRolling() {
        return timeSinceLastRoll <= rollDuration();
    }

    public void onRoll(ClientPlayerEntity player) {
        availableRolls -= 1;
        timeSinceLastRoll = 0;
        updateCooldownLength(player);
        
        if (CombatRollClient.config.rollUphill) {
            try {
                this.stepHeightInitial = player.getAttributeInstance(Registries.ATTRIBUTE.get(new Identifier("forge:step_height_addition"))).getBaseValue();
                this.newStepHeight = this.stepHeightInitial + stepHeightBoost;
                player.getAttributeInstance(Registries.ATTRIBUTE.get(new Identifier("forge:step_height_addition"))).setBaseValue(this.newStepHeight);
            } catch (Exception ex) {
                this.stepHeightInitial = (double) player.getStepHeight();
                this.newStepHeight = this.stepHeightInitial + stepHeightBoost;
                player.setStepHeight((float) this.newStepHeight);
            }
        }
    }

    public void tick(ClientPlayerEntity player) {
        maxRolls = (int) EntityAttributes_CombatRoll.getAttributeValue(player, COUNT);
        timeSinceLastRoll += 1;
        
        if (!isRolling()) {
            if (stepHeightInitial >= 0.0f) {
                double stepHeightCurrent;
                try {
                    stepHeightCurrent = player.getAttributeInstance(Registries.ATTRIBUTE.get(new Identifier("forge:step_height_addition"))).getBaseValue();
                } catch (Exception ex) {
                    stepHeightCurrent = (double) player.getStepHeight();
                }

                if (stepHeightCurrent == newStepHeight) {
                    try {
                        player.getAttributeInstance(Registries.ATTRIBUTE.get(new Identifier("forge:step_height_addition"))).setBaseValue(this.stepHeightInitial);
                    } catch (Exception ex) {
                        player.setStepHeight((float) this.stepHeightInitial);
                    }
                    
                    this.stepHeightInitial = -1.0;
                }
            }
        }
        
        if (availableRolls < maxRolls) {
            currentCooldownProgress += 1;
            if (currentCooldownProgress >= currentCooldownLength) {
                rechargeRoll(player);
            }
        }
        if (availableRolls == maxRolls) {
            currentCooldownProgress = 0;
        }
        if (availableRolls > maxRolls) {
            availableRolls = maxRolls;
        }
    }

    private void rechargeRoll(ClientPlayerEntity player) {
        availableRolls += 1;
        currentCooldownProgress = 0;
        updateCooldownLength(player);
        if (CombatRollClient.config.playCooldownSound) {
            var cooldownReady = Registries.SOUND_EVENT.get(new Identifier("combatroll:roll_cooldown_ready"));
            if (cooldownReady != null) {
                player.getWorld().playSound(player.getX(), player.getY(), player.getZ(), cooldownReady, SoundCategory.PLAYERS, 1, 1, false);
            }
        }
    }

    private void updateCooldownLength(ClientPlayerEntity player) {
        var duration = CombatRoll.config.roll_cooldown;
        currentCooldownLength = (int) Math.round(duration * 20F * (20F / EntityAttributes_CombatRoll.getAttributeValue(player, RECHARGE)));
    }
}
