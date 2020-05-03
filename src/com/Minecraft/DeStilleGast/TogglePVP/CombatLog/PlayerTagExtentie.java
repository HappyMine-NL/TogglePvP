package com.Minecraft.DeStilleGast.TogglePVP.CombatLog;

import org.bukkit.entity.Player;

import java.util.ArrayList;

public class PlayerTagExtentie {
	
	private Player myPlayer;
	private ArrayList<String> attackedBy = new ArrayList<String>();
	
	
	public PlayerTagExtentie(Player pl){
		myPlayer = pl;
	}

	public Player getMyPlayer() {
		return myPlayer;
	}


	public void addAttacker(String name){
		this.attackedBy.add(name);
	}
	
	public boolean isAttacker(String name){
		return this.attackedBy.contains(name);
	}
	
	public void removeAttacker(String name){
		this.attackedBy.remove(name);
	}
	
	public ArrayList<String> getAttackerList(){
		return this.attackedBy;
	}
}
